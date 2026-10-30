#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

FROM="${1:-}"
TO="${2:-HEAD}"

if [ -z "$FROM" ]; then
  FROM="$(git describe --tags --abbrev=0 2>/dev/null || git rev-list --max-parents=0 HEAD)"
fi

echo "# Changelog"
echo
echo "## ${TO} (since ${FROM})"
echo
git log --no-merges --reverse --format='- %s' "${FROM}..${TO}" |
  grep -vE '^- (docs: mark|chore: pin wrapper)' || true
echo
echo "生成命令：\`./scripts/changelog.sh <from-tag> <to-tag>\`（不手写流水账）"
