#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

./scripts/gradlew-locked check :app:assembleDebug

if [[ "${1:-}" == "--device" ]]; then
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  adb shell am start -n io.github.whywood.deepdeep/.MainActivity
fi
