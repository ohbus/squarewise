#!/usr/bin/env sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$repo_root"

mkdir -p build/reports/acceptance
uv_command=uv
if ! command -v "$uv_command" >/dev/null 2>&1 && command -v uv.exe >/dev/null 2>&1; then
  uv_command=uv.exe
fi
"$uv_command" run --frozen --no-build python tests/acceptance/runner.py "$@"
