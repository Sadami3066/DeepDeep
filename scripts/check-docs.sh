#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

mapfile -t files < <(git ls-files '*.md')
if [ "${#files[@]}" -eq 0 ]; then
  echo "没有 Markdown 文件" >&2
  exit 1
fi

python3 - "${files[@]}" <<'PY'
import os
import re
import sys

LINK = re.compile(r'\[[^\]]*\]\(([^)\s]+)(?:\s+"[^"]*")?\)')
bad = []
for path in sys.argv[1:]:
    try:
        text = open(path, encoding="utf-8").read()
    except OSError as exc:
        bad.append((path, f"读取失败：{exc}"))
        continue
    for match in LINK.finditer(text):
        target = match.group(1)
        if target.startswith(("http", "https", "mailto:", "tel:", "#", "data:")):
            continue
        target = target.split("#")[0]
        if not target:
            continue
        resolved = os.path.normpath(os.path.join(os.path.dirname(path), target))
        if not os.path.exists(resolved):
            bad.append((path, target))

if bad:
    print(f"断链 {len(bad)} 处：")
    for path, target in bad:
        print(f"  {path} -> {target}")
    sys.exit(1)
print(f"文档链接检查通过：{len(sys.argv) - 1} 个 Markdown 文件")
PY
