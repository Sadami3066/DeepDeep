#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

PACKAGE="io.github.whywood.deepdeep"
TEST_PACKAGE="io.github.whywood.deepdeep.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="io.github.whywood.deepdeep.plugin.manager.PluginBundleE2ETest"
DEVICE_DIR="/sdcard/Android/data/${PACKAGE}/files/plugin-e2e"
PLUGINS_DIR="${PLUGINS_DIR:-$(cd .. && pwd)/DeepDeep-Official-Plugins}"
UPDATE_MODULE="${UPDATE_MODULE:-wecom}"
UPDATE_VERSION="9.9.9"
MODULES=(feishu wecom sysu micloud)

UPDATE_MANIFEST="${PLUGINS_DIR}/${UPDATE_MODULE}/src/main/assets/plugin.json"
UPDATE_BUNDLE="${PLUGINS_DIR}/${UPDATE_MODULE}/build/plugin/${UPDATE_MODULE}.ddp"
UPDATE_STAGED="/tmp/deepdeep-${UPDATE_MODULE}-update.ddp"

if [[ ! -d "$PLUGINS_DIR" ]]; then
  echo "plugin repository not found: $PLUGINS_DIR" >&2
  exit 1
fi

./scripts/gradlew-locked :design:publishToMavenLocal :plugin-api:publishToMavenLocal :app:assembleDebug :app:assembleDebugAndroidTest

(
  cd "$PLUGINS_DIR"
  tasks=()
  for module in "${MODULES[@]}"; do
    tasks+=(":${module}:signPlugin" ":${module}:verifyPlugin")
  done
  ./scripts/gradlew-locked "${tasks[@]}"
)

UPDATE_BACKUP="$(mktemp)"
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

adb push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/deepdeep.apk >/dev/null
adb push app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk /data/local/tmp/deepdeep-test.apk >/dev/null
adb shell su -c "pm install -r /data/local/tmp/deepdeep.apk" >/dev/null
adb shell su -c "pm install -r /data/local/tmp/deepdeep-test.apk" >/dev/null
adb shell su -c "mkdir -p ${DEVICE_DIR}"
adb shell su -c "rm -f ${DEVICE_DIR}/*.ddp"
adb shell su -c "mkdir -p ${DEVICE_DIR}-update"
adb shell su -c "rm -f ${DEVICE_DIR}-update/*.ddp"

for module in "${MODULES[@]}"; do
  ddp="${PLUGINS_DIR}/${module}/build/plugin/${module}.ddp"
  adb push "$ddp" "/data/local/tmp/${module}.ddp" >/dev/null
  adb shell su -c "cp /data/local/tmp/${module}.ddp ${DEVICE_DIR}/${module}.ddp"
  adb shell su -c "chmod 644 ${DEVICE_DIR}/${module}.ddp"
done

adb push "$UPDATE_STAGED" "/data/local/tmp/${UPDATE_MODULE}-update.ddp" >/dev/null
adb shell su -c "cp /data/local/tmp/${UPDATE_MODULE}-update.ddp ${DEVICE_DIR}-update/${UPDATE_MODULE}.ddp"
adb shell su -c "chmod 644 ${DEVICE_DIR}-update/${UPDATE_MODULE}.ddp"

adb shell am instrument -w -e class "$TEST_CLASS" "${TEST_PACKAGE}/${RUNNER}"
