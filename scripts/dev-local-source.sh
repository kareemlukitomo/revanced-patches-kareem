#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STATE_DIR="$ROOT_DIR/.git/local-dev"
STATE_FILE="$STATE_DIR/state.env"
SERVER_LOG="$STATE_DIR/http.log"
TUNNEL_LOG="$STATE_DIR/cloudflared.log"
COMMAND="${1:-start}"
SERVER_SESSION_NAME="${SERVER_SESSION_NAME:-rvp-local-preview-http}"
TUNNEL_SESSION_NAME="${TUNNEL_SESSION_NAME:-rvp-local-preview-tunnel}"

mkdir -p "$STATE_DIR"

write_manager_source() {
  local public_base_url="$1"
  local artifact_name="$2"
  local local_version="$3"
  local signature_name="${artifact_name}.asc"
  local signature_download_url="null"

  if [[ -f "$ROOT_DIR/patches/build/libs/$signature_name" ]]; then
    signature_download_url="\"${public_base_url}/${signature_name}\""
  fi

  cat >"$ROOT_DIR/patches/build/libs/manager-source.local.json" <<EOF
{
  "version": "v${local_version}",
  "created_at": "$(date -u +%Y-%m-%dT%H:%M:%S)",
  "description": "Kareem ReVanced patches.",
  "download_url": "${public_base_url}/${artifact_name}",
  "signature_download_url": ${signature_download_url}
}
EOF
}

wait_for_http_url() {
  local url="$1"
  local label="$2"

  for _ in $(seq 1 120); do
    if curl --fail --silent --show-error --head "$url" >/dev/null; then
      return 0
    fi
    sleep 1
  done

  echo "Timed out waiting for $label: $url" >&2
  return 1
}

stop_processes() {
  if [[ -f "$STATE_FILE" ]]; then
    # shellcheck disable=SC2046
    eval $(grep -E '^(SERVER_PID|TUNNEL_PID|SERVER_SESSION|TUNNEL_SESSION|LOCAL_PORT|LOCAL_VERSION|ARTIFACT_PATH|PUBLIC_BASE_URL)=' "$STATE_FILE" || true)
    [[ -n "${SERVER_SESSION:-}" ]] && tmux kill-session -t "$SERVER_SESSION" 2>/dev/null || true
    [[ -n "${TUNNEL_SESSION:-}" ]] && tmux kill-session -t "$TUNNEL_SESSION" 2>/dev/null || true
    [[ -n "${SERVER_PID:-}" ]] && kill "$SERVER_PID" 2>/dev/null || true
    [[ -n "${TUNNEL_PID:-}" ]] && kill "$TUNNEL_PID" 2>/dev/null || true
    rm -f "$STATE_FILE"
  fi

  tmux kill-session -t "$SERVER_SESSION_NAME" 2>/dev/null || true
  tmux kill-session -t "$TUNNEL_SESSION_NAME" 2>/dev/null || true
}

start_tmux_session() {
  local session_name="$1"
  local command="$2"
  tmux kill-session -t "$session_name" 2>/dev/null || true
  tmux new-session -d -s "$session_name" "$command"
}

wait_for_tmux_pane_pid() {
  local session_name="$1"

  for _ in $(seq 1 10); do
    local pane_pid
    pane_pid="$(tmux list-panes -t "$session_name" -F '#{pane_pid}' 2>/dev/null | head -n 1 || true)"
    if [[ -n "$pane_pid" ]]; then
      printf '%s\n' "$pane_pid"
      return 0
    fi
    sleep 1
  done

  return 1
}

start() {
  stop_processes

  local artifact_path
  artifact_path="$("$ROOT_DIR/scripts/build-local-rvp.sh")"
  local artifact_name
  artifact_name="$(basename "$artifact_path")"
  local local_version="${artifact_name#patches-}"
  local_version="${local_version%-android.rvp}"
  local local_port="${LOCAL_PORT:-18080}"
  local artifacts_dir="$ROOT_DIR/patches/build/libs"
  local server_session="$SERVER_SESSION_NAME"
  local tunnel_session="$TUNNEL_SESSION_NAME"
  : >"$SERVER_LOG"
  : >"$TUNNEL_LOG"

  local quoted_root quoted_artifacts_dir quoted_server_log quoted_tunnel_log quoted_tunnel_url
  printf -v quoted_root '%q' "$ROOT_DIR"
  printf -v quoted_artifacts_dir '%q' "$artifacts_dir"
  printf -v quoted_server_log '%q' "$SERVER_LOG"
  printf -v quoted_tunnel_log '%q' "$TUNNEL_LOG"
  printf -v quoted_tunnel_url '%q' "http://127.0.0.1:${local_port}"

  start_tmux_session \
    "$server_session" \
    "cd $quoted_root && exec python3 -m http.server $local_port --bind 127.0.0.1 --directory $quoted_artifacts_dir >>$quoted_server_log 2>&1"
  local server_pid
  server_pid="$(wait_for_tmux_pane_pid "$server_session")"

  sleep 1
  if [[ -z "$server_pid" ]] || ! kill -0 "$server_pid" 2>/dev/null; then
    echo "Local HTTP server exited early. See $SERVER_LOG" >&2
    exit 1
  fi

  start_tmux_session \
    "$tunnel_session" \
    "cd $quoted_root && exec cloudflared tunnel --url $quoted_tunnel_url --no-autoupdate --loglevel info >>$quoted_tunnel_log 2>&1"
  local tunnel_pid
  tunnel_pid="$(wait_for_tmux_pane_pid "$tunnel_session")"

  local public_base_url=""
  for _ in $(seq 1 60); do
    public_base_url="$(grep -oE 'https://[-0-9a-z]+\.trycloudflare\.com' "$TUNNEL_LOG" | head -n 1 || true)"
    if [[ -n "$public_base_url" ]]; then
      break
    fi
    sleep 1
  done

  if [[ -z "$public_base_url" ]]; then
    echo "Could not determine the Cloudflare tunnel URL. See $TUNNEL_LOG" >&2
    exit 1
  fi

  write_manager_source "$public_base_url" "$artifact_name" "$local_version"
  "$ROOT_DIR/scripts/generate-local-preview-json.sh" "$public_base_url" "$ROOT_DIR/patches/build/libs" >/dev/null

  wait_for_http_url "http://127.0.0.1:${local_port}/patches.json" "local patches.json"
  if ! curl --fail --silent --show-error --head --max-time 5 "${public_base_url}/${artifact_name}" >/dev/null; then
    echo "Warning: public tunnel URL is not reachable yet; it may take a little longer to propagate." >&2
  fi

  cat >"$STATE_FILE" <<EOF
SERVER_PID=$server_pid
TUNNEL_PID=$tunnel_pid
SERVER_SESSION=$server_session
TUNNEL_SESSION=$tunnel_session
LOCAL_PORT=$local_port
LOCAL_VERSION=$local_version
ARTIFACT_PATH=$artifact_path
PUBLIC_BASE_URL=$public_base_url
EOF

  printf 'artifact=%s\n' "$artifact_path"
  printf 'public_base_url=%s\n' "$public_base_url"
  printf 'download_url=%s/%s\n' "$public_base_url" "$artifact_name"
  printf 'manager_source=%s\n' "$ROOT_DIR/patches/build/libs/manager-source.local.json"
}

status() {
  if [[ ! -f "$STATE_FILE" ]]; then
    echo "No local dev source is running."
    exit 1
  fi

  # shellcheck disable=SC2046
  eval $(grep -E '^(SERVER_PID|TUNNEL_PID|SERVER_SESSION|TUNNEL_SESSION|LOCAL_PORT|LOCAL_VERSION|ARTIFACT_PATH|PUBLIC_BASE_URL)=' "$STATE_FILE" || true)
  if ! tmux has-session -t "${SERVER_SESSION:-}" 2>/dev/null || ! tmux has-session -t "${TUNNEL_SESSION:-}" 2>/dev/null; then
    echo "Local dev source state is stale. Run $0 stop && $0 start." >&2
    exit 1
  fi

  printf 'artifact=%s\n' "${ARTIFACT_PATH:-}"
  printf 'public_base_url=%s\n' "${PUBLIC_BASE_URL:-}"
  printf 'local_port=%s\n' "${LOCAL_PORT:-}"
  printf 'server_session=%s\n' "${SERVER_SESSION:-}"
  printf 'tunnel_session=%s\n' "${TUNNEL_SESSION:-}"
  printf 'manager_source=%s\n' "$ROOT_DIR/patches/build/libs/manager-source.local.json"
}

case "$COMMAND" in
  start)
    start
    ;;
  stop)
    stop_processes
    ;;
  status)
    status
    ;;
  *)
    echo "Usage: $0 {start|stop|status}" >&2
    exit 1
    ;;
esac
