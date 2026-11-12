#!/usr/bin/env bash

deepdeep_repo_root() {
  cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd
}

deepdeep_neighbor_dir() {
  local repo dir name
  repo="$(deepdeep_repo_root)"
  for name in "$@"; do
    for dir in "$repo/../$name" "$repo/../../$name"; do
      if [[ -d "$dir" ]]; then
        (cd "$dir" && pwd)
        return 0
      fi
    done
  done
  return 1
}

deepdeep_android_sdk() {
  local repo local_sdk="" line sdk
  repo="$(deepdeep_repo_root)"
  if [[ -f "$repo/local.properties" ]]; then
    while IFS= read -r line; do
      case "$line" in
        sdk.dir=*)
          local_sdk="${line#sdk.dir=}"
          break
          ;;
      esac
    done < "$repo/local.properties"
  fi
  for sdk in \
    "${ANDROID_HOME:-}" \
    "${ANDROID_SDK_ROOT:-}" \
    "$local_sdk" \
    "${HOME:-}/Android/Sdk" \
    "${HOME:-}/Library/Android/sdk" \
    "/usr/local/lib/android/sdk" \
    "/opt/android-sdk"; do
    if [[ -n "$sdk" && -d "$sdk" ]]; then
      printf '%s\n' "$sdk"
      return 0
    fi
  done
  return 1
}

deepdeep_require() {
  local missing="" cmd
  for cmd in "$@"; do
    command -v "$cmd" >/dev/null 2>&1 || missing="${missing:+$missing }$cmd"
  done
  if [[ -n "$missing" ]]; then
    echo "缺少命令：$missing" >&2
    return 1
  fi
}

deepdeep_ensure_jdk17() {
  if ! deepdeep_require java javac; then
    echo "需要 JDK 17+（含 javac）；macOS: brew install openjdk@17" >&2
    return 1
  fi
  local version major
  version="$(javac -version 2>&1)" || {
    echo "无法运行 javac -version" >&2
    return 1
  }
  major="$(printf '%s\n' "$version" | sed -E 's/^javac ([0-9]+).*/\1/')"
  if [[ ! "$major" =~ ^[0-9]+$ ]] || (( major < 17 )); then
    echo "需要 JDK 17+（含 javac），当前：$version；macOS: brew install openjdk@17" >&2
    return 1
  fi
}

deepdeep_ensure_sdk_components() {
  local sdk="${1:-}" sdkmanager
  if [[ -z "$sdk" ]]; then
    echo "deepdeep_ensure_sdk_components 需要 SDK 路径" >&2
    return 1
  fi
  if [[ -d "$sdk/platforms/android-36" && -d "$sdk/build-tools/36.0.0" ]]; then
    return 0
  fi
  sdkmanager="$(find "$sdk/cmdline-tools" -name sdkmanager -type f 2>/dev/null | sed -n '1p' || true)"
  if [[ -z "$sdkmanager" ]]; then
    echo "找不到 sdkmanager：请安装 Android cmdline-tools 到 $sdk/cmdline-tools" >&2
    return 1
  fi
  echo "[setup] 安装 SDK Platform 36 与 build-tools 36.0.0…"
  { yes | "$sdkmanager" --licenses; } >/dev/null 2>&1 || true
  "$sdkmanager" "platforms;android-36" "build-tools;36.0.0"
}

deepdeep_require_adb() {
  deepdeep_require adb
}

deepdeep_device_serial() {
  if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    printf '%s\n' "$ANDROID_SERIAL"
    return 0
  fi
  local output line serial="" list="" count=0
  if ! output="$(adb devices)"; then
    echo "无法运行 adb devices（请确认 platform-tools 已安装并在 PATH 中）" >&2
    return 1
  fi
  while IFS= read -r line; do
    if [[ "$line" =~ ^([^[:space:]]+)[[:space:]]+device$ ]]; then
      serial="${BASH_REMATCH[1]}"
      list="${list}${list:+$'\n'}${serial}"
      count=$((count + 1))
    fi
  done <<< "$output"
  if (( count == 0 )); then
    echo "没有在线设备：请连接设备并开启 USB 调试，或设置 ANDROID_SERIAL" >&2
    return 1
  fi
  if (( count > 1 )); then
    echo "有多台在线设备，请设置 ANDROID_SERIAL 指定其中一台：" >&2
    printf '%s\n' "$list" | sed 's/^/  /' >&2
    return 1
  fi
  printf '%s\n' "$serial"
}

deepdeep_install_apk() {
  local serial="${1:-}" apk="${2:-}" output
  if [[ -z "$serial" || -z "$apk" ]]; then
    echo "deepdeep_install_apk 需要设备 serial 与 APK 路径" >&2
    return 1
  fi
  output="$(adb -s "$serial" install -r -t "$apk" 2>&1)" || true
  if [[ "$output" != *"Success"* ]]; then
    printf '%s\n' "$output" >&2
    echo "安装失败：$apk；设备上已有同包名但签名不同的构建时需先卸载（会清数据），或换设备/模拟器" >&2
    return 1
  fi
}

deepdeep_clear_app_dir() {
  local serial="${1:-}" device_dir="${2:-}"
  if [[ -z "$serial" || -z "$device_dir" ]]; then
    echo "deepdeep_clear_app_dir 需要设备 serial 与设备目录" >&2
    return 1
  fi
  adb -s "$serial" shell "rm -rf '$device_dir' && mkdir -p '$device_dir'"
}

deepdeep_stage_app_file() {
  local serial="${1:-}" source="${2:-}" device_dir="${3:-}" name="${4:-}"
  if [[ -z "$serial" || -z "$source" || -z "$device_dir" ]]; then
    echo "deepdeep_stage_app_file 需要设备 serial、源文件与设备目录" >&2
    return 1
  fi
  if [[ -z "$name" ]]; then
    name="$(basename "$source")"
  fi
  adb -s "$serial" shell "mkdir -p '$device_dir'"
  adb -s "$serial" push "$source" "$device_dir/$name" >/dev/null
}

deepdeep_instrument() {
  local serial="${1:-}" test_class="${2:-}" component="${3:-}" output
  if [[ -z "$serial" || -z "$test_class" || -z "$component" ]]; then
    echo "deepdeep_instrument 需要设备 serial、测试类与测试组件" >&2
    return 1
  fi
  if ! output="$(adb -s "$serial" shell am instrument -w -e class "$test_class" "$component")"; then
    printf '%s\n' "$output"
    echo "设备测试执行失败：$test_class" >&2
    return 1
  fi
  printf '%s\n' "$output"
  if [[ "$output" != *"OK ("* ]]; then
    echo "设备测试未通过：$test_class" >&2
    return 1
  fi
}
