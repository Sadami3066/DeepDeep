#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh

if sdk="$(deepdeep_android_sdk)"; then
  export ANDROID_HOME="$sdk"
  export ANDROID_SDK_ROOT="$sdk"
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
  ./scripts/api-compat.sh
fi

if [[ "$device" -eq 1 ]]; then
  deepdeep_require_adb
  serial="$(deepdeep_device_serial)"
  adb -s "$serial" wait-for-device
  deepdeep_install_apk "$serial" app/build/outputs/apk/debug/app-debug.apk
  adb -s "$serial" shell am start -n io.github.whywood.deepdeep/.MainActivity
fi
