#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

JAVA_MAJOR="$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')"
if [ "${JAVA_MAJOR:-0}" -lt 17 ]; then
  echo "需要 JDK 17+，当前 Java 主版本：${JAVA_MAJOR:-unknown}" >&2
  exit 1
fi

ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
if [ ! -d "$ANDROID_HOME/platforms/android-36" ] || [ ! -d "$ANDROID_HOME/build-tools/36.0.0" ]; then
  SDKMANAGER="$(find "$ANDROID_HOME/cmdline-tools" -name sdkmanager -type f 2>/dev/null | head -n 1)"
  if [ -z "$SDKMANAGER" ]; then
    echo "找不到 sdkmanager：请安装 cmdline-tools 到 $ANDROID_HOME，或先手动装 SDK Platform 36 / build-tools 36.0.0" >&2
    exit 1
  fi
  yes | "$SDKMANAGER" --licenses > /dev/null || true
  "$SDKMANAGER" "platforms;android-36" "build-tools;36.0.0"
fi

printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
export ANDROID_HOME

./scripts/gradlew-locked syncPiSdk
./scripts/gradlew-locked :app:assembleDebug

echo "APK: $(pwd)/app/build/outputs/apk/debug/app-debug.apk"
