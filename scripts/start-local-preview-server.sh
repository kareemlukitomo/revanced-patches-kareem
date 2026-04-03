#!/usr/bin/env bash
set -euo pipefail

port="${1:-8765}"
artifact_dir="${2:-patches/build/libs}"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ "$artifact_dir" != /* ]]; then
  artifact_dir="$repo_root/$artifact_dir"
fi

exec python3 -m http.server "$port" --bind 127.0.0.1 --directory "$artifact_dir"
