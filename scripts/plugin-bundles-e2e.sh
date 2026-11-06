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
    :feishu:verifyPlugin \
    :wecom:verifyPlugin \
    :jwxt:verifyPlugin \
    :micloud:verifyPlugin \
    :sample:verifyPlugin \
    :template:verifyPlugin \
    :interop:verifyPlugin
)

adb push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/deepdeep.apk >/dev/null
adb push app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk /data/local/tmp/deepdeep-test.apk >/dev/null
adb shell su -c "pm install -r /data/local/tmp/deepdeep.apk" >/dev/null
adb shell su -c "pm install -r /data/local/tmp/deepdeep-test.apk" >/dev/null
adb shell su -c "mkdir -p ${DEVICE_DIR}"
adb shell su -c "rm -f ${DEVICE_DIR}/*.ddp"

for ddp in ../plugins/feishu/build/plugin/feishu.ddp \
  ../plugins/wecom/build/plugin/wecom.ddp \
  ../plugins/jwxt/build/plugin/jwxt.ddp \
  ../plugins/micloud/build/plugin/micloud.ddp \
  ../plugins/interop/build/plugin/interop.ddp; do
  name="$(basename "$ddp")"
  adb push "$ddp" "/data/local/tmp/$name" >/dev/null
  adb shell su -c "cp /data/local/tmp/$name ${DEVICE_DIR}/$name"
  adb shell su -c "chmod 644 ${DEVICE_DIR}/$name"
done

adb shell am instrument -w -e class "$TEST_CLASS" "${TEST_PACKAGE}/${RUNNER}"
