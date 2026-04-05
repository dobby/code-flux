#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEV_TARGET_URL="${CODE_FLUX_ELECTRON_TARGET_URL:-}"
FRONTEND_HOST="${FRONTEND_HOST:-127.0.0.1}"
FRONTEND_PORT="${FRONTEND_PORT:-4173}"
DEV_URL="$DEV_TARGET_URL"
DEV_SERVER_LOG="$(mktemp -t code-flux-electron-dev-log.XXXXXX)"

cleanup() {
  if [[ -n "${DEV_PID:-}" ]] && kill -0 "$DEV_PID" >/dev/null 2>&1; then
    kill "$DEV_PID" >/dev/null 2>&1 || true
  fi
}

cleanup_on_exit() {
  cleanup
  rm -f "$DEV_SERVER_LOG"
}

trap cleanup_on_exit EXIT INT TERM

wait_for_url() {
  local url="$1"

  for _ in {1..120}; do
    if curl --silent --fail --max-time 2 "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done

  return 1
}

extract_vite_url() {
  local log_line
  log_line=$(grep -Eom1 "Local: https?://[^ ]+" "$DEV_SERVER_LOG" || true)
  if [[ -n "$log_line" ]]; then
    echo "${log_line#Local: }"
    return 0
  fi
  return 1
}

resolve_dev_url() {
  local discovered_url
  if [[ -n "$DEV_TARGET_URL" ]]; then
    echo "$DEV_TARGET_URL"
    return 0
  fi

  for _ in {1..120}; do
    discovered_url=$(extract_vite_url)
    if [[ -n "$discovered_url" ]]; then
      echo "$discovered_url"
      return 0
    fi
    sleep 1
  done

  echo "${FRONTEND_HOST}:${FRONTEND_PORT}"
}

"$ROOT_DIR/scripts/generate-app-icons.sh"

cd "$ROOT_DIR"
./scripts/dev.sh >"$DEV_SERVER_LOG" 2>&1 &
DEV_PID=$!

DEV_URL="$(resolve_dev_url)"
if [[ ! "$DEV_URL" =~ ^https?:// ]]; then
  DEV_URL="http://$DEV_URL"
fi

if ! wait_for_url "$DEV_URL"; then
  echo "Timed out waiting for $DEV_URL" >&2
  echo "--- dev server log ---" >&2
  sed -n '1,120p' "$DEV_SERVER_LOG" >&2
  exit 1
fi

cd "$ROOT_DIR/desktop"
npm ci
CODE_FLUX_ELECTRON_TARGET_URL="$DEV_URL" npm run dev
