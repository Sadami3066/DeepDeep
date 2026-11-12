#!/usr/bin/env bash

deepdeep_repo_root() {
  cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd
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

deepdeep_ensure_root() {
  local serial="${1:-}" id
  if [[ -z "$serial" ]]; then
    echo "deepdeep_ensure_root 需要设备 serial" >&2
    return 1
  fi
  if adb -s "$serial" shell su -c true >/dev/null 2>&1; then
    return 0
  fi
  adb -s "$serial" root >/dev/null 2>&1 || true
  adb -s "$serial" wait-for-device >/dev/null 2>&1 || true
  id="$(adb -s "$serial" shell id 2>/dev/null || true)"
  case "$id" in
    *uid=0*) return 0 ;;
  esac
  echo "需要 root 设备（su 或 adb root）：$serial" >&2
  return 1
}
