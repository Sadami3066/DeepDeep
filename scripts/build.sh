#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

usage() {
  cat <<'EOF'
usage: scripts/build.sh <command> [gradle args...]
  quick              编译 app（:app:compileDebugKotlin）
  test [--tests ...] 定向单测（:app:testDebugUnitTest）
  check              集成点完整验证（scripts/check.sh --full）
  release            完整验证后构建 release APK
  status             显示 daemon、内存与构建缓存状态
  stop               停止 Gradle/Kotlin daemon，回收内存
EOF
}

case "${1:-}" in
  quick)
    shift
    exec ./scripts/gradlew-locked :app:compileDebugKotlin "$@"
    ;;
  test)
    shift
    exec ./scripts/gradlew-locked :app:testDebugUnitTest "$@"
    ;;
  check)
    shift
    exec ./scripts/check.sh --full "$@"
    ;;
  release)
    shift
    ./scripts/check.sh --full
    exec ./scripts/gradlew-locked :app:assembleRelease "$@"
    ;;
  status)
    ps -eo pid,rss,args | grep -E 'GradleDaemon|KotlinCompileDaemon' | grep -v grep || true
    if command -v free >/dev/null 2>&1; then free -h; else vm_stat 2>/dev/null | head -5; fi
    swapon --show 2>/dev/null || true
    du -sh "$HOME/.gradle/caches/build-cache-1" 2>/dev/null || true
    ;;
  stop)
    ./gradlew --stop >/dev/null 2>&1 || true
    pkill -f 'KotlinCompileDaemon' 2>/dev/null || true
    sleep 1
    echo "[build] daemons stopped"
    ;;
  *)
    usage
    exit 1
    ;;
esac
