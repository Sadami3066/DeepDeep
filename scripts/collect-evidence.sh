#!/usr/bin/env bash
set -euo pipefail

ADB="${ADB:-adb}"
SIZE="${EVIDENCE_MAX_SIZE:-720}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EVIDENCE_ROOT="$ROOT/docs/evidence"
ACCEPTANCE="$ROOT/docs/ACCEPTANCE.md"

usage() {
  cat >&2 <<'EOF'
用法: collect-evidence.sh <ACC-ID> [备注...]

截一张当前屏幕，压到 720p，落到 docs/evidence/<YYYY-MM-DD>-<ID>/，并维护 verdict.md。

示例:
  ./scripts/collect-evidence.sh ACC-CHAT-01 长任务 2s
  ./scripts/collect-evidence.sh ACC-SCHEDULE-06 一次性时间-过去

环境变量:
  ADB=/path/to/adb        指定 adb（默认取 PATH 里的 adb）
  EVIDENCE_MAX_SIZE=720   截图长边上限（默认 720）
  ANDROID_SERIAL=<serial> 多设备时指定目标设备
EOF
}

fail() {
  printf '[evidence] %s\n' "$*" >&2
  exit 1
}

[[ $# -ge 1 ]] || { usage; exit 2; }

id="${1^^}"
[[ "$id" =~ ^ACC-[A-Z][A-Z0-9]{1,11}-[0-9]{2}$ ]] || fail "条目 ID 不合法：$1（应形如 ACC-CHAT-01，取自 docs/ACCEPTANCE.md）"
shift
note="$*"

[[ -f "$ACCEPTANCE" ]] || fail "找不到 docs/ACCEPTANCE.md"
grep -q "^### $id " "$ACCEPTANCE" || fail "$id 不在 docs/ACCEPTANCE.md 里；先补条目再采证据"
old="$(awk -v id="$id" '$0 ~ ("^### " id " ") { found=1; next } found && /^- 旧编号：/ { sub(/^- 旧编号：/, ""); print; exit }' "$ACCEPTANCE")"
old="${old:-未知}"

command -v "$ADB" >/dev/null 2>&1 || fail "找不到 adb：$ADB。安装 Android platform-tools，或设置 ADB=/path/to/adb"
if command -v timeout >/dev/null 2>&1; then
  adb_() { timeout 20 "$ADB" "$@" 2>/dev/null; }
else
  adb_() { "$ADB" "$@" 2>/dev/null; }
fi

state="$(adb_ get-state || true)"
[[ "$state" == "device" ]] || fail "没有可用设备（adb get-state 返回：${state:-空}）。先执行 adb devices 确认设备在线且已授权；多设备时设置 ANDROID_SERIAL。"
serial="$(adb_ get-serialno || true)"
model="$(adb_ shell getprop ro.product.model || true)"
android="$(adb_ shell getprop ro.build.version.release || true)"
abi="$(adb_ shell getprop ro.product.cpu.abi || true)"

dir="$EVIDENCE_ROOT/$(date +%F)-$id"
mkdir -p "$dir"
count="$(find "$dir" -maxdepth 1 -type f -name '[0-9][0-9]-*.png' | wc -l)"
next="$(printf '%02d' "$((count + 1))")"
slug="$(printf '%s' "$note" | tr -cs 'A-Za-z0-9' '-' | sed -e 's/^-*//' -e 's/-*$//' | cut -c1-40)"
slug="${slug:-shot}"

raw="$(mktemp "${TMPDIR:-/tmp}/deepdeep-shot.XXXXXX.png")"
trap 'rm -f "$raw"' EXIT
adb_ exec-out screencap -p > "$raw" || fail "screencap 失败（设备可能已断开）"
[[ -s "$raw" ]] || fail "screencap 输出为空"
magic="$(od -An -tx1 -N4 "$raw" | tr -d ' \n')"
[[ "$magic" == "89504e47" ]] || fail "screencap 输出不是 PNG（magic=$magic）"

out_name="$next-$slug.png"
out="$dir/$out_name"
resized=0
if command -v magick >/dev/null 2>&1; then
  if magick "$raw" -resize "${SIZE}x${SIZE}>" -strip "$out"; then resized=1; fi
elif command -v convert >/dev/null 2>&1; then
  if convert "$raw" -resize "${SIZE}x${SIZE}>" -strip "$out"; then resized=1; fi
elif command -v ffmpeg >/dev/null 2>&1; then
  if ffmpeg -loglevel error -y -i "$raw" -vf "scale='min(iw,${SIZE})':'min(ih,${SIZE})':force_original_aspect_ratio=decrease:flags=lanczos" -frames:v 1 "$out"; then resized=1; fi
elif command -v python3 >/dev/null 2>&1 && python3 -c 'import PIL' >/dev/null 2>&1; then
  if python3 - "$raw" "$out" "$SIZE" <<'PY'
import sys
from PIL import Image
raw, out, size = sys.argv[1], sys.argv[2], int(sys.argv[3])
with Image.open(raw) as image:
    image.thumbnail((size, size), Image.LANCZOS)
    image.save(out, 'PNG', optimize=True)
PY
  then resized=1; fi
fi

if [[ "$resized" -eq 0 ]]; then
  rm -f "$out"
  out_name="$next-$slug-raw.png"
  out="$dir/$out_name"
  cp "$raw" "$out"
  printf '[evidence] 警告：没有可用的压缩工具（magick/convert/ffmpeg/python3-PIL），已保存原图：%s\n' "$out" >&2
  printf '[evidence] 警告：未压缩原图不要入库，验收后按 docs/evidence/README.md 处理。\n' >&2
fi

verdict="$dir/verdict.md"
if [[ ! -f "$verdict" ]]; then
  cat > "$verdict" <<EOF
# $id 验收证据

- 条目：$id
- 旧编号：$old
- 日期：$(date +%F)
- 设备：${model:-未知} / Android ${android:-未知} / ${abi:-未知}（serial：${serial:-未知}）
- 截图规格：长边 ≤ ${SIZE}px

## 判定

- [ ] 通过
- [ ] 失败
- [ ] 待定

## 一行现象

（填写屏幕上实际看到什么；失败时写清与判据的偏差。）

## 截图
EOF
fi

if [[ "$resized" -eq 0 ]]; then
  if [[ -n "$note" ]]; then
    printf -- '- %s（未压缩原图，勿入库；%s）\n' "$out_name" "$note" >> "$verdict"
  else
    printf -- '- %s（未压缩原图，勿入库）\n' "$out_name" >> "$verdict"
  fi
elif [[ -n "$note" ]]; then
  printf -- '- %s（%s）\n' "$out_name" "$note" >> "$verdict"
else
  printf -- '- %s\n' "$out_name" >> "$verdict"
fi

total=$((count + 1))
if [[ "$total" -gt 10 ]]; then
  printf '[evidence] 提示：本轮已有 %d 张截图，超出规范（5–10 张）；合并或删减后再提交。\n' "$total" >&2
fi

printf '[evidence] 已保存 %s（本轮第 %d 张）\n' "$out_name" "$total"
printf '[evidence] 目录：%s\n' "$dir"
printf '[evidence] 下一步：编辑 %s 勾选判定并写一行现象。\n' "$verdict"
