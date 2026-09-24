"""Local USB debug bridge. No model-supplied shell commands are accepted."""

import asyncio
import re

from jevis.core.config import get_settings
from jevis.services.app_registry import ALLOWED_TARGET_APPS

_launch_lock = asyncio.Lock()


async def adb(*arguments: str) -> str:
    process = await asyncio.create_subprocess_exec(
        "adb", *arguments, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE,
    )
    try:
        stdout, stderr = await asyncio.wait_for(process.communicate(), timeout=8)
    except BaseException:
        if process.returncode is None:
            process.kill()
        await process.wait()
        raise
    output = stdout.decode(errors="replace")
    if process.returncode or "SecurityException" in output or "Error:" in output:
        raise RuntimeError("ADB 隔离启动失败，请检查 USB 授权和应用多显示支持")
    return output


def foreground_by_display(dump: str) -> dict[int, str]:
    result = {}
    display = None
    for line in dump.splitlines():
        match = re.match(r"Display #(\d+)", line)
        if match:
            display = int(match[1])
        match = re.search(r"topResumedActivity=.*? u\d+ ([\w.]+)/", line)
        if match and display is not None:
            result[display] = match[1]
    return result


def background_root(dump: str, package: str) -> int | None:
    root = None
    for line in dump.splitlines():
        match = re.match(r"RootTask id=(\d+).*displayId=0\b", line)
        if line.startswith("RootTask "):
            root = int(match[1]) if match else None
        if root is not None and f"topActivity=ComponentInfo{{{package}/" in line:
            return root
    return None


async def prepare_display(device_id: str, display_id: int, package: str) -> None:
    if display_id <= 0 or package not in ALLOWED_TARGET_APPS:
        raise ValueError("拒绝非隔离显示或非白名单应用")
    async with _launch_lock:
        connected = re.findall(r"^(\S+)\s+device$", await adb("devices"), re.MULTILINE)
        settings = get_settings()
        # ANDROID_ID is app/signing-key scoped; shell settings returns a different ID.
        # Use an explicit deployment binding rather than guessing another connected phone.
        if (device_id != settings.adb_device_id or not settings.adb_device_serial
                or settings.adb_device_serial not in connected):
            raise RuntimeError("USB 手机绑定不匹配，请配置 ADB_DEVICE_SERIAL 和 ADB_DEVICE_ID")
        serial = settings.adb_device_serial

        async def shell(*args: str) -> str:
            return await adb("-s", serial, "shell", *args)

        before = foreground_by_display(await shell("dumpsys", "activity", "activities"))
        if before.get(0) == package:
            raise RuntimeError("用户正在主屏使用目标应用，请先切换其他应用再从任务列表发起")
        if before.get(display_id) == package:
            return
        stacks = await shell("am", "stack", "list")
        if f"displayId={display_id} " not in stacks:
            raise RuntimeError("指定隔离显示已不存在，请重新启动隔离显示")
        root = background_root(stacks, package)
        if root is not None:
            await shell("am", "display", "move-stack", str(root), str(display_id))
        else:
            resolved = await shell("cmd", "package", "resolve-activity", "--brief", package)
            component = resolved.strip().splitlines()[-1]
            if not re.fullmatch(re.escape(package) + r"/[\w.$]+", component):
                raise RuntimeError("无法解析目标应用启动入口")
            await shell("am", "start", "--display", str(display_id), "-n", component,
                        "-f", "0x18000000")
        for _ in range(12):
            after = foreground_by_display(await shell("dumpsys", "activity", "activities"))
            if after.get(0) == package:
                raise RuntimeError("应用跳转到主屏，已阻止后续操作")
            if after.get(display_id) == package:
                return
            await asyncio.sleep(0.25)
        raise RuntimeError("目标应用未进入隔离显示，已阻止后续操作")
