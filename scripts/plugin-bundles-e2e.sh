#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

PACKAGE="io.github.whywood.deepdeep"
TEST_PACKAGE="io.github.whywood.deepdeep.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="io.github.whywood.deepdeep.plugin.manager.PluginBundleE2ETest"
DEVICE_DIR="/sdcard/Android/data/${PACKAGE}/files/plugin-e2e"

./scripts/gradlew-locked :design:publishToMavenLocal :plugin-api:publishToMavenLocal :app:assembleDebug :app:assembleDebugAndroidTest

(
  cd ../plugins
  ./scripts/gradlew-locked \
    :feishu:signPlugin \
    :wecom:signPlugin \
    :jwxt:signPlugin \
    :micloud:signPlugin \
    :sample:signPlugin \
    :template:signPlugin \
    :interop:signPlugin \
    :feishu:verifyPlugin \
    :wecom:verifyPlugin \
    :jwxt:verifyPlugin \
    :micloud:verifyPlugin \
    :sample:verifyPlugin \
    :template:verifyPlugin \
    :interop:verifyPlugin
)

INTEROP_MANIFEST="../plugins/interop/src/main/assets/plugin.json"
INTEROP_BACKUP="$(mktemp)"
cp "$INTEROP_MANIFEST" "$INTEROP_BACKUP"
restore_interop() { cp "$INTEROP_BACKUP" "$INTEROP_MANIFEST"; rm -f "$INTEROP_BACKUP"; }
trap restore_interop EXIT
python3 - "$INTEROP_MANIFEST" <<'PY'
import json, sys
from pathlib import Path
path = Path(sys.argv[1])
data = json.loads(path.read_text())
data["version"] = "1.0.1"
path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
PY
(cd ../plugins && ./scripts/gradlew-locked :interop:signPlugin >/dev/null)
cp ../plugins/interop/build/plugin/interop.ddp /tmp/deepdeep-interop-update.ddp
restore_interop
trap - EXIT
(cd ../plugins && ./scripts/gradlew-locked :interop:signPlugin >/dev/null)

adb push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/deepdeep.apk >/dev/null
adb push app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk /data/local/tmp/deepdeep-test.apk >/dev/null
adb shell su -c "pm install -r /data/local/tmp/deepdeep.apk" >/dev/null
adb shell su -c "pm install -r /data/local/tmp/deepdeep-test.apk" >/dev/null
adb shell su -c "mkdir -p ${DEVICE_DIR}"
adb shell su -c "rm -f ${DEVICE_DIR}/*.ddp"
adb shell su -c "mkdir -p ${DEVICE_DIR}-update"
adb shell su -c "rm -f ${DEVICE_DIR}-update/*.ddp"

for ddp in ../plugins/feishu/build/plugin/feishu.ddp \
  ../plugins/wecom/build/plugin/wecom.ddp \
  ../plugins/jwxt/build/plugin/jwxt.ddp \
  ../plugins/micloud/build/plugin/micloud.ddp \
  ../plugins/sample/build/plugin/sample.ddp \
  ../plugins/template/build/plugin/template.ddp \
  ../plugins/interop/build/plugin/interop.ddp; do
  name="$(basename "$ddp")"
  adb push "$ddp" "/data/local/tmp/$name" >/dev/null
  adb shell su -c "cp /data/local/tmp/$name ${DEVICE_DIR}/$name"
  adb shell su -c "chmod 644 ${DEVICE_DIR}/$name"
done

adb push /tmp/deepdeep-interop-update.ddp /data/local/tmp/interop-update.ddp >/dev/null
adb shell su -c "cp /data/local/tmp/interop-update.ddp ${DEVICE_DIR}-update/interop.ddp"
adb shell su -c "chmod 644 ${DEVICE_DIR}-update/interop.ddp"

adb shell am instrument -w -e class "$TEST_CLASS" "${TEST_PACKAGE}/${RUNNER}"
