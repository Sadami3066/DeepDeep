#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
"$(dirname "$0")/check-docs.sh"
exec "$(dirname "$0")/gradlew-locked" check :app:assembleDebug "$@"
