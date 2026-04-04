#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEV_URL="${CODE_FLUX_ELECTRON_TARGET_URL:-http://127.0.0.1:4173/}"

wait_for_url() {
  local attempt
  for attempt in {1..120}; do
    if curl --silent --fail --max-time 2 "$DEV_URL" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done

  return 1
}

cleanup() {
  if [[ -n "${DEV_PID:-}" ]] && kill -0 "$DEV_PID" >/dev/null 2>&1; then
    kill "$DEV_PID" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT INT TERM

"$ROOT_DIR/scripts/generate-app-icons.sh"

cd "$ROOT_DIR"
./scripts/dev.sh &
DEV_PID=$!

if ! wait_for_url; then
  echo "Timed out waiting for $DEV_URL" >&2
  exit 1
fi

cd "$ROOT_DIR/desktop"
npm ci
CODE_FLUX_ELECTRON_TARGET_URL="$DEV_URL" npm run dev
