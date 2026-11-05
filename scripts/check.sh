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
    check :app:assembleDebug :plugin-sample:assembleDebug :plugin-api:assembleRelease
  ./scripts/plugin-api-abi.sh
  ./scripts/gradlew-locked :plugin-api:publishToMavenLocal

  key_root="$(mktemp -d)"
  trap 'rm -rf "$key_root"' EXIT
  key_rel="$(realpath -m --relative-to="$(pwd)/plugin-template" "$key_root/plugin-signing")"
  (
    exec 9>"${TMPDIR:-/tmp}/deepdeep-build.lock"
    flock 9
    cd plugin-template
    ../gradlew --max-workers=1 -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" \
      -PpluginSigningKey="$key_rel" assemblePlugin signPlugin verifyPlugin
  )
fi

if [[ "$device" -eq 1 ]]; then
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  adb shell am start -n io.github.whywood.deepdeep/.MainActivity
fi
