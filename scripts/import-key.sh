#!/usr/bin/env bash
set -euo pipefail
PACKAGE="${PACKAGE:-io.github.whywood.deepdeep}"
KEY_FILE="${KEY_FILE:-$HOME/.config/deepdeep/deepseek.key}"
DEVICE_TMP="/data/local/tmp/deepseek.key"

test -f "$KEY_FILE"
adb push "$KEY_FILE" "$DEVICE_TMP" >/dev/null
adb shell "cat $DEVICE_TMP" | adb shell "run-as $PACKAGE sh -c 'mkdir -p files/import; cat > files/import/deepseek.key'"
adb shell rm -f "$DEVICE_TMP"
echo "imported key for $PACKAGE on next launch"
