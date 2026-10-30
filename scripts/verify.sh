#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

LOCK_FILE="${TMPDIR:-/tmp}/deepdeep-verify.lock"
exec 9>"$LOCK_FILE"
if ! flock -n 9; then
  echo "[verify] 另一个构建正在运行，等待中…" >&2
  flock -w 1800 9 || { echo "[verify] 等待构建锁超时（30 分钟）" >&2; exit 1; }
fi

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
./gradlew --max-workers=2 \
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" \
  check :app:assembleDebug "$@"
