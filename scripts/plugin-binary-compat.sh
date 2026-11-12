#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh

PACKAGE="io.github.whywood.deepdeep"
TEST_PACKAGE="${PACKAGE}.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="io.github.whywood.deepdeep.plugin.manager.PluginBinaryCompatTest"
DEVICE_DIR="/sdcard/Android/data/${PACKAGE}/files/plugin-binary-compat"
FIXTURES_DIR="${FIXTURES_DIR:-app/src/androidTest/assets/plugin-fixtures}"
DEBUG_APK="app/build/outputs/apk/debug/app-debug.apk"
ANDROID_TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

step() {
  printf '\n==> %s\n' "$*"
}

fail() {
  printf '结论：FAIL — %s\n' "$*" >&2
  exit 1
}

trap 'code=$?; if (( code != 0 )); then printf "\n结论：FAIL (exit %s)\n" "$code" >&2; fi' EXIT

if ! command -v adb >/dev/null 2>&1; then
  if sdk="$(deepdeep_android_sdk)"; then
    export PATH="$sdk/platform-tools:$PATH"
  fi
fi

step "1/6 检查 adb 与设备"
deepdeep_require_adb || fail "缺少 adb"
serial="$(deepdeep_device_serial)" || fail "没有可用设备"
printf '设备：%s\n' "$serial"
if sdk="$(deepdeep_android_sdk)"; then
  export ANDROID_HOME="$sdk"
  export ANDROID_SDK_ROOT="$sdk"
fi

step "2/6 检查夹具"
if [[ ! -d "$FIXTURES_DIR" ]]; then
  fail "夹具目录不存在：$FIXTURES_DIR"
fi
shopt -s nullglob
fixtures=("$FIXTURES_DIR"/*.ddp)
shopt -u nullglob
if (( ${#fixtures[@]} == 0 )); then
  fail "夹具目录没有 .ddp：$FIXTURES_DIR"
fi
printf '夹具目录：%s\n' "$FIXTURES_DIR"
for fixture in "${fixtures[@]}"; do
  printf '  %s (%s bytes)\n' "$(basename "$fixture")" "$(wc -c < "$fixture")"
done

step "3/6 构建 debug 宿主与 androidTest APK"
./scripts/gradlew-locked :app:assembleDebug :app:assembleDebugAndroidTest

step "4/6 安装 APK"
if [[ "${RESET_APP:-0}" == "1" ]]; then
  printf 'uninstall %s\n' "$PACKAGE"
  adb -s "$serial" uninstall "$PACKAGE" >/dev/null 2>&1 || true
  printf 'uninstall %s\n' "$TEST_PACKAGE"
  adb -s "$serial" uninstall "$TEST_PACKAGE" >/dev/null 2>&1 || true
fi
deepdeep_install_apk "$serial" "$DEBUG_APK" || fail "宿主 APK 安装失败（设备已有不同签名的同包名应用时，先以 RESET_APP=1 重跑）"
deepdeep_install_apk "$serial" "$ANDROID_TEST_APK" || fail "androidTest APK 安装失败"

step "5/6 推送夹具到 app 外部目录"
deepdeep_clear_app_dir "$serial" "$DEVICE_DIR"
for fixture in "${fixtures[@]}"; do
  deepdeep_stage_app_file "$serial" "$fixture" "$DEVICE_DIR"
  printf '  pushed %s\n' "$(basename "$fixture")"
done

step "6/6 运行设备二进制回归"
deepdeep_instrument "$serial" "$TEST_CLASS" "${TEST_PACKAGE}/${RUNNER}" || fail "设备回归测试未通过"

printf '\n结论：PASS — %s 在 %s 通过（夹具 %d 个）\n' "$TEST_CLASS" "$serial" "${#fixtures[@]}"
