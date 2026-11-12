#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/env.sh"
cd "$(deepdeep_repo_root)"

deepdeep_ensure_jdk17

if ! sdk="$(deepdeep_android_sdk)"; then
  cat >&2 <<'EOF'
找不到 Android SDK：请安装 Android SDK 或设置 ANDROID_HOME。
  macOS: $HOME/Library/Android/sdk
  Linux: $HOME/Android/Sdk 或 /usr/local/lib/android/sdk
EOF
  exit 1
fi

if [[ -f local.properties ]]; then
  configured=""
  while IFS= read -r line; do
    case "$line" in
      sdk.dir=*)
        configured="${line#sdk.dir=}"
        break
        ;;
    esac
  done < local.properties
  if [[ -n "$configured" && "$configured" != "$sdk" ]]; then
    echo "[setup] local.properties 已指向 $configured（探测到 $sdk），沿用现有配置" >&2
    sdk="$configured"
  fi
else
  printf 'sdk.dir=%s\n' "$sdk" > local.properties
fi

echo "[setup] Android SDK: $sdk"
deepdeep_ensure_sdk_components "$sdk"
export ANDROID_HOME="$sdk"
export ANDROID_SDK_ROOT="$sdk"

cat <<'EOF'
[setup] 首次构建需下载 Gradle 分发包与依赖，并在 JitPack 现编 SDK；冷缓存约 15–30 分钟。
[setup] 建议内存 ≥ 8 GB、空闲磁盘 ≥ 10 GB。
EOF

./scripts/gradlew-locked :app:assembleDebug
echo "APK: $(pwd)/app/build/outputs/apk/debug/app-debug.apk"
