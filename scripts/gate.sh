#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

LEVEL="${1:-3}"
shift || true

START=$(date +%s)
elapsed() { echo "$(($(date +%s) - START))s"; }

docs() { ./scripts/check-docs.sh; }

compile() {
  ./scripts/gradlew-locked \
    :runtime-api:compileKotlin \
    :design:compileDebugKotlin \
    :shell:compileDebugKotlin \
    :runtime:compileDebugKotlin \
    :app:compileDebugKotlin
}

tests() {
  ./scripts/gradlew-locked \
    :app:testDebugUnitTest \
    :runtime:testDebugUnitTest \
    :shell:testDebugUnitTest \
    :design:testDebugUnitTest
}

full() { ./scripts/gradlew-locked check :app:assembleDebug; }

case "$LEVEL" in
  0|docs)
    docs
    echo "[gate] L0 docs 通过 · $(elapsed)"
    ;;
  1|compile)
    docs
    compile
    echo "[gate] L1 compile 通过 · $(elapsed)"
    echo "[gate] 提示：改了行为/数据就继续跑到 L3"
    ;;
  2|test)
    docs
    tests
    echo "[gate] L2 test 通过 · $(elapsed)"
    echo "[gate] 提示：进入代码改动默认档 L3：./scripts/gate.sh 3"
    ;;
  3|full)
    docs
    full
    echo "[gate] L3 full 通过 · $(elapsed)"
    ;;
  4|device)
    docs
    full
    if ! adb devices 2>/dev/null | awk 'NR>1 && $2=="device"' | grep -q .; then
      echo "[gate] 没找到已连接的真机（adb devices）" >&2
      exit 1
    fi
    adb install -r app/build/outputs/apk/debug/app-debug.apk
    echo "[gate] L4 device 通过 · $(elapsed)"
    echo "[gate] 验收截图：./scripts/collect-evidence.sh <ACC-ID>"
    ;;
  ci)
    MODE="${1:-full}"
    case "$MODE" in
      fast|full) ;;
      *) echo "用法：./scripts/gate.sh ci [fast|full]" >&2; exit 2 ;;
    esac
    BRANCH="$(git branch --show-current)"
    echo "[gate] 在 GitHub 上跑 $MODE（分支 $BRANCH），本地不等它"
    gh workflow run ci.yml --ref "$BRANCH" -f level="$MODE"
    sleep 4
    gh run list --workflow ci.yml --limit 1
    echo "[gate] 看日志：gh run watch；取消：gh run cancel <id>"
    ;;
  *)
    cat >&2 <<'USAGE'
用法：./scripts/gate.sh <档位> [参数]

  0 | docs      只查文档断链（约 2s）
  1 | compile   文档 + 五个模块编译
  2 | test      文档 + 单测（含编译）
  3 | full      文档 + check（单测/lint/模块边界）+ debug APK（默认档）
  4 | device    3 + 覆盖安装到真机
  ci [fast|full]  在 GitHub 上手动跑（不阻塞本地）

不传档位默认 3。拿不准就升一档。
USAGE
    exit 2
    ;;
esac
