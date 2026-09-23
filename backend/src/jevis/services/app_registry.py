from dataclasses import dataclass


@dataclass(frozen=True)
class AppCapability:
    package: str
    label: str
    keywords: tuple[str, ...]


APP_CAPABILITIES = (
    AppCapability("com.tencent.androidqqmail", "QQ 邮箱", ("邮件", "邮箱", "email", "mail")),
    AppCapability("com.android.calendar", "日历", ("日历", "日程", "会议", "calendar")),
    AppCapability("com.tencent.mm", "微信", ("微信", "wechat")),
    AppCapability("com.android.browser", "浏览器", ("浏览器", "网页", "网站", "搜索", "browser")),
    AppCapability("com.miui.notes", "便签", ("便签", "笔记", "记录", "note")),
    AppCapability("com.autonavi.minimap", "高德地图", ("地图", "导航", "路线", "地点", "map")),
    AppCapability("com.miui.gallery", "相册", ("相册", "照片", "图片", "gallery")),
    AppCapability("com.android.contacts", "联系人", ("联系人", "通讯录", "contact")),
)

ALLOWED_TARGET_APPS = {item.package for item in APP_CAPABILITIES}


def infer_target_app(instruction: str) -> str | None:
    normalized = instruction.casefold()
    return next(
        (
            app.package
            for app in APP_CAPABILITIES
            if any(keyword.casefold() in normalized for keyword in app.keywords)
        ),
        None,
    )
