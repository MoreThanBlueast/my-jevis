#!/usr/bin/env bash
set -euo pipefail

if ! command -v adb >/dev/null 2>&1; then
  echo "adb 未安装或不在 PATH 中" >&2
  exit 1
fi

device_count="$(adb devices | awk 'NR>1 && $2 == "device" {count++} END {print count+0}')"
if [ "$device_count" -ne 1 ]; then
  echo "需要且只能连接一台已授权设备，当前数量：$device_count" >&2
  exit 1
fi

echo "model=$(adb shell getprop ro.product.model | tr -d '\r')"
echo "android=$(adb shell getprop ro.build.version.release | tr -d '\r')"
echo "sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
echo "hyperos=$(adb shell getprop ro.mi.os.version.name | tr -d '\r')"
echo "qqmail=$(adb shell pm list packages com.tencent.androidqqmail | tr -d '\r')"
echo "users:"
adb shell pm list users
echo "displays:"
adb shell dumpsys display | awk '/DisplayDeviceInfo|mDisplayId=|FLAG_TRUSTED|OWN_FOCUS/ {print}' | head -80

