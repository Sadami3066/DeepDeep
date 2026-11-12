#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh

PACKAGE="io.github.whywood.deepdeep"
TEST_PACKAGE="io.github.whywood.deepdeep.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="io.github.whywood.deepdeep.plugin.manager.ExternalPluginE2ETest"
DEVICE_DIR="/sdcard/Android/data/${PACKAGE}/files/plugin-e2e"
PLUGINS_DIR="${PLUGINS_DIR:-$(deepdeep_neighbor_dir plugins DeepDeep-Plugins || true)}"
E2E_MODULE="${E2E_MODULE:-sample}"
E2E_DDP="${E2E_DDP:-${PLUGINS_DIR}/${E2E_MODULE}/build/plugin/${E2E_MODULE}.ddp}"

if [[ -z "$PLUGINS_DIR" || ! -d "$PLUGINS_DIR" ]]; then
  echo "插件仓库不存在：${PLUGINS_DIR:-未找到}（用 PLUGINS_DIR 指定 DeepDeep-Plugins 路径）" >&2
  exit 1
fi

deepdeep_require_adb
serial="$(deepdeep_device_serial)"
if sdk="$(deepdeep_android_sdk)"; then
  export ANDROID_HOME="$sdk"
  export ANDROID_SDK_ROOT="$sdk"
fi

if [[ ! -f "$E2E_DDP" ]]; then
  if [[ -x "$PLUGINS_DIR/$E2E_MODULE/gradlew" ]]; then
    (cd "$PLUGINS_DIR/$E2E_MODULE" && ./gradlew verifyPlugin)
  elif [[ -d "$PLUGINS_DIR/$E2E_MODULE" && -x "$PLUGINS_DIR/scripts/gradlew-locked" ]]; then
    (cd "$PLUGINS_DIR" && ./scripts/gradlew-locked ":${E2E_MODULE}:verifyPlugin")
  else
    echo "缺少测试夹具：$E2E_DDP（插件仓库需有 ${E2E_MODULE} 模块，或用 E2E_DDP 指定已签名包）" >&2
    exit 1
  fi
fi

./scripts/gradlew-locked :app:assembleDebug :app:assembleDebugAndroidTest

deepdeep_install_apk "$serial" app/build/outputs/apk/debug/app-debug.apk
deepdeep_install_apk "$serial" app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

deepdeep_clear_app_dir "$serial" "$DEVICE_DIR"
deepdeep_stage_app_file "$serial" "$E2E_DDP" "$DEVICE_DIR" plugin-sample-signed.ddp

deepdeep_instrument "$serial" "$TEST_CLASS" "${TEST_PACKAGE}/${RUNNER}"
