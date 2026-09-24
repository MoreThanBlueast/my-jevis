#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/open-jevis-gradle}"

cd "$repo_dir/android"
./gradlew :app:assembleDebug
adb reverse tcp:8000 "tcp:${JEVIS_BACKEND_PORT:-18001}"
adb install -r app/build/outputs/apk/debug/app-debug.apk

echo "安装完成。请在系统无障碍设置中启用 open-jevis 隔离执行器。"
