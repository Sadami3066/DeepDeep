#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

exec "$(dirname "$0")/gate.sh" 3 "$@"
