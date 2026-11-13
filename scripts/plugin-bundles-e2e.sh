#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh

PACKAGE="io.github.whywood.deepdeep"
TEST_PACKAGE="io.github.whywood.deepdeep.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="io.github.whywood.deepdeep.plugin.manager.PluginBundleE2ETest"
DEVICE_DIR="/sdcard/Android/data/${PACKAGE}/files/plugin-e2e"
PLUGINS_DIR="${PLUGINS_DIR:-$(deepdeep_neighbor_dir DeepDeep-Official-Plugins || true)}"
UPDATE_MODULE="${UPDATE_MODULE:-wecom}"
UPDATE_VERSION="9.9.9"
MODULES=(feishu wecom sysu micloud mail wechat themes)

UPDATE_MANIFEST="${PLUGINS_DIR}/${UPDATE_MODULE}/src/main/assets/plugin.json"
UPDATE_BUNDLE="${PLUGINS_DIR}/${UPDATE_MODULE}/build/plugin/${UPDATE_MODULE}.ddp"
UPDATE_STAGED="/tmp/deepdeep-${UPDATE_MODULE}-update.ddp"

if [[ -z "$PLUGINS_DIR" || ! -d "$PLUGINS_DIR" ]]; then
  echo "插件仓库不存在：${PLUGINS_DIR:-未找到}（用 PLUGINS_DIR 指定 DeepDeep-Official-Plugins 路径）" >&2
  exit 1
fi

deepdeep_require_adb
serial="$(deepdeep_device_serial)"
if sdk="$(deepdeep_android_sdk)"; then
  export ANDROID_HOME="$sdk"
  export ANDROID_SDK_ROOT="$sdk"
fi

./scripts/gradlew-locked :app:assembleDebug :app:assembleDebugAndroidTest

(
  cd "$PLUGINS_DIR"
  tasks=()
  for module in "${MODULES[@]}"; do
    tasks+=(":${module}:signPlugin" ":${module}:verifyPlugin")
  done
  ./scripts/gradlew-locked "${tasks[@]}"
)

UPDATE_BACKUP="$(mktemp "${TMPDIR:-/tmp}/deepdeep-update.XXXXXX")"
cp "$UPDATE_MANIFEST" "$UPDATE_BACKUP"
restore_update_manifest() {
  cp "$UPDATE_BACKUP" "$UPDATE_MANIFEST"
  rm -f "$UPDATE_BACKUP"
  (cd "$PLUGINS_DIR" && ./scripts/gradlew-locked ":${UPDATE_MODULE}:signPlugin" >/dev/null 2>&1) || true
}
trap restore_update_manifest EXIT

python3 - "$UPDATE_MANIFEST" "$UPDATE_VERSION" <<'PY'
import json, sys
from pathlib import Path
path = Path(sys.argv[1])
data = json.loads(path.read_text(encoding="utf-8"))
data["version"] = sys.argv[2]
path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY
(cd "$PLUGINS_DIR" && ./scripts/gradlew-locked ":${UPDATE_MODULE}:signPlugin" >/dev/null)
cp "$UPDATE_BUNDLE" "$UPDATE_STAGED"
restore_update_manifest
trap - EXIT

deepdeep_install_apk "$serial" app/build/outputs/apk/debug/app-debug.apk
deepdeep_install_apk "$serial" app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

deepdeep_clear_app_dir "$serial" "$DEVICE_DIR"
deepdeep_clear_app_dir "$serial" "${DEVICE_DIR}-update"

for module in "${MODULES[@]}"; do
  deepdeep_stage_app_file "$serial" "${PLUGINS_DIR}/${module}/build/plugin/${module}.ddp" "$DEVICE_DIR" "${module}.ddp"
done
deepdeep_stage_app_file "$serial" "$UPDATE_STAGED" "${DEVICE_DIR}-update" "${UPDATE_MODULE}.ddp"

deepdeep_instrument "$serial" "$TEST_CLASS" "${TEST_PACKAGE}/${RUNNER}"
