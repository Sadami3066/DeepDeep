#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

PACKAGE="io.github.whywood.deepdeep"
TEST_PACKAGE="io.github.whywood.deepdeep.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="io.github.whywood.deepdeep.plugin.host.ExternalPluginSpikeTest"
DEVICE_DIR="/sdcard/Android/data/${PACKAGE}/files/plugin-e2e"

./scripts/gradlew-locked :plugin-sample:assemblePlugin :app:assembleDebug :app:assembleDebugAndroidTest

adb push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/deepdeep.apk >/dev/null
adb push app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk /data/local/tmp/deepdeep-test.apk >/dev/null
adb push plugin-sample/build/plugin/plugin-sample.ddp /data/local/tmp/plugin-sample.ddp >/dev/null
adb shell su -c "pm install -r /data/local/tmp/deepdeep.apk" >/dev/null
adb shell su -c "pm install -r /data/local/tmp/deepdeep-test.apk" >/dev/null
adb shell su -c "mkdir -p ${DEVICE_DIR}"
adb shell su -c "cp /data/local/tmp/plugin-sample.ddp ${DEVICE_DIR}/plugin-sample.ddp"
adb shell su -c "chmod 644 ${DEVICE_DIR}/plugin-sample.ddp"

adb shell am instrument -w -e class "$TEST_CLASS" "${TEST_PACKAGE}/${RUNNER}"
