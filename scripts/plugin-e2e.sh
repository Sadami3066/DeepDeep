#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

PACKAGE="io.github.whywood.deepdeep"
TEST_PACKAGE="io.github.whywood.deepdeep.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="io.github.whywood.deepdeep.plugin.manager.ExternalPluginE2ETest"
DEVICE_DIR="/sdcard/Android/data/${PACKAGE}/files/plugin-e2e"

./scripts/gradlew-locked \
    :plugin-api:publishToMavenLocal \
    :app:assembleDebug \
    :app:assembleDebugAndroidTest

(
  cd ../plugins
  ./gradlew :sample:verifyPlugin
)

adb push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/deepdeep.apk >/dev/null
adb push app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk /data/local/tmp/deepdeep-test.apk >/dev/null
adb push ../plugins/sample/build/plugin/sample.ddp /data/local/tmp/plugin-sample-signed.ddp >/dev/null
adb shell su -c "pm install -r /data/local/tmp/deepdeep.apk" >/dev/null
adb shell su -c "pm install -r /data/local/tmp/deepdeep-test.apk" >/dev/null
adb shell su -c "mkdir -p ${DEVICE_DIR}"
adb shell su -c "cp /data/local/tmp/plugin-sample-signed.ddp ${DEVICE_DIR}/plugin-sample-signed.ddp"
adb shell su -c "chmod 644 ${DEVICE_DIR}/plugin-sample-signed.ddp"

adb shell am instrument -w -e class "$TEST_CLASS" "${TEST_PACKAGE}/${RUNNER}"
