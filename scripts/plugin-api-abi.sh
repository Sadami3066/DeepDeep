#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

missing=""
command -v unzip >/dev/null 2>&1 || missing="unzip"

JAVAP=""
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/javap" ]]; then
  JAVAP="$JAVA_HOME/bin/javap"
elif command -v javap >/dev/null 2>&1; then
  JAVAP="javap"
else
  missing="${missing:+$missing }javap"
fi

if [[ -n "$missing" ]]; then
  echo "缺少命令：$missing（Ubuntu 安装 openjdk-17-jdk-headless / macOS 安装 JDK）" >&2
  exit 1
fi

GOLDEN="plugin-api/abi/plugin-api.api"
UPDATE=0
if [[ "${1:-}" == "--update" ]]; then
  UPDATE=1
fi

./scripts/gradlew-locked :plugin-api:assembleRelease

AAR="plugin-api/build/outputs/aar/plugin-api-release.aar"
if [[ ! -f "$AAR" ]]; then
  echo "[abi] missing $AAR" >&2
  exit 1
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

unzip -q "$AAR" classes.jar -d "$TMP"
JAR="$TMP/classes.jar"

if command -v jar >/dev/null 2>&1; then
  jar tf "$JAR"
else
  unzip -Z1 "$JAR"
fi | grep '\.class$' | sed 's#/#.#g; s#\.class$##' | sort -u > "$TMP/classes.txt"

: > "$TMP/api.txt"
while IFS= read -r cls; do
  "$JAVAP" -public -classpath "$JAR" "$cls" | sed '/^Compiled from "/d' >> "$TMP/api.txt"
done < "$TMP/classes.txt"

if [[ "$UPDATE" -eq 1 ]]; then
  mkdir -p "$(dirname "$GOLDEN")"
  cp "$TMP/api.txt" "$GOLDEN"
  echo "[abi] updated $GOLDEN ($(wc -l < "$TMP/api.txt") lines, $(wc -l < "$TMP/classes.txt") classes)"
  exit 0
fi

if [[ ! -f "$GOLDEN" ]]; then
  echo "[abi] missing $GOLDEN; run scripts/plugin-api-abi.sh --update after an intentional ABI change" >&2
  exit 1
fi

if ! diff -u "$GOLDEN" "$TMP/api.txt"; then
  echo "[abi] plugin-api public ABI changed. If intentional, run scripts/plugin-api-abi.sh --update and bump plugin-api version." >&2
  exit 1
fi

echo "[abi] OK: $(wc -l < "$TMP/api.txt") lines across $(wc -l < "$TMP/classes.txt") classes"
