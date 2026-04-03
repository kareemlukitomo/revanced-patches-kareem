#!/usr/bin/env bash
set -euo pipefail

port="${1:-8765}"

exec cloudflared tunnel --url "http://127.0.0.1:$port" --no-autoupdate --loglevel info
