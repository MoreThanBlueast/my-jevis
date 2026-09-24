#!/usr/bin/env bash
set -euo pipefail

if ! adb get-state >/dev/null 2>&1; then
  echo "没有可用的 ADB 设备" >&2
  exit 1
fi
if ! command -v scrcpy >/dev/null 2>&1; then
  echo "请先安装 scrcpy" >&2
  exit 1
fi

adb reverse tcp:8000 "tcp:${JEVIS_BACKEND_PORT:-18001}" >/dev/null
exec scrcpy \
  --new-display=1080x1920/420 \
  --start-app=com.jevis.mobile \
  --display-ime-policy=local \
  --no-audio \
  --no-clipboard-autosync \
  --window-title="open-jevis isolated display"
