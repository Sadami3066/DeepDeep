#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh

PACKAGE="io.github.whywood.deepdeep"
TEST_PACKAGE="io.github.whywood.deepdeep.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="io.github.whywood.deepdeep.plugin.manager.ExternalPluginE2ETest"
DEVICE_DIR="/sdcard/Android/data/${PACKAGE}/files/plugin-e2e"
PLUGINS_DIR="${PLUGINS_DIR:-$(cd .. && pwd)/plugins}"
E2E_MODULE="${E2E_MODULE:-sample}"
E2E_DDP="${E2E_DDP:-${PLUGINS_DIR}/${E2E_MODULE}/build/plugin/${E2E_MODULE}.ddp}"

if [[ ! -d "$PLUGINS_DIR" ]]; then
  echo "插件仓库不存在：$PLUGINS_DIR（可用 PLUGINS_DIR 指定）" >&2
  exit 1
fi
if [[ ! -d "$PLUGINS_DIR/$E2E_MODULE" ]]; then
  echo "插件仓库缺少 $E2E_MODULE 模块（模板仓库当前只有 template），此回归需先补 sample 插件" >&2
  exit 1
fi

deepdeep_require_adb
serial="$(deepdeep_device_serial)"
deepdeep_ensure_root "$serial"

./scripts/gradlew-locked \
    :design:publishToMavenLocal \
    :plugin-api:publishToMavenLocal \
    :app:assembleDebug \
    :app:assembleDebugAndroidTest

(
  cd "$PLUGINS_DIR"
  ./scripts/gradlew-locked ":${E2E_MODULE}:verifyPlugin"
)

adb -s "$serial" push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/deepdeep.apk >/dev/null
adb -s "$serial" push app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk /data/local/tmp/deepdeep-test.apk >/dev/null
adb -s "$serial" push "$E2E_DDP" /data/local/tmp/plugin-sample-signed.ddp >/dev/null
adb -s "$serial" shell su -c "pm install -r /data/local/tmp/deepdeep.apk" >/dev/null
adb -s "$serial" shell su -c "pm install -r /data/local/tmp/deepdeep-test.apk" >/dev/null
adb -s "$serial" shell su -c "mkdir -p ${DEVICE_DIR}"
adb -s "$serial" shell su -c "cp /data/local/tmp/plugin-sample-signed.ddp ${DEVICE_DIR}/plugin-sample-signed.ddp"
adb -s "$serial" shell su -c "chmod 644 ${DEVICE_DIR}/plugin-sample-signed.ddp"

adb -s "$serial" shell am instrument -w -e class "$TEST_CLASS" "${TEST_PACKAGE}/${RUNNER}"
