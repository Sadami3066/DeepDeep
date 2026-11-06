#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -z "${ANDROID_HOME:-}" && -f local.properties ]]; then
  sdk_dir="$(sed -n 's/^sdk\.dir=//p' local.properties)"
  if [[ -n "$sdk_dir" ]]; then
    export ANDROID_HOME="$sdk_dir"
    export ANDROID_SDK_ROOT="$sdk_dir"
  fi
fi

mode="${1:-}"
full=0
device=0
case "$mode" in
  "") ;;
  --full) full=1 ;;
  --device) full=1; device=1 ;;
  *)
    echo "usage: $0 [--full|--device]" >&2
    exit 2
    ;;
esac

if [[ "$full" -eq 0 ]]; then
  ./scripts/gradlew-locked check :app:assembleDebug
else
  ./scripts/gradlew-locked -Pdeepdeep.strictBoundaries=true \
    check :app:assembleDebug :plugin-api:assembleRelease
  ./scripts/plugin-api-abi.sh
fi

if [[ "$device" -eq 1 ]]; then
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  adb shell am start -n io.github.whywood.deepdeep/.MainActivity
fi
