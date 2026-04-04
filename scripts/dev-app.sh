#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEV_URL="${DEV_URL:-http://127.0.0.1:4173/}"
PROFILE_DIR="${HOME}/Library/Application Support/CodeFlux/dev-chromium-profile"

declare -a BROWSER_CANDIDATES=(
  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
  "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge"
  "/Applications/Brave Browser.app/Contents/MacOS/Brave Browser"
)

find_browser() {
  local browser
  for browser in "${BROWSER_CANDIDATES[@]}"; do
    if [[ -x "$browser" ]]; then
      printf '%s\n' "$browser"
      return 0
    fi
  done

  return 1
}

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

BROWSER_BIN="$(find_browser || true)"
if [[ -z "$BROWSER_BIN" ]]; then
  echo "No supported Chromium browser found. Install Chrome, Edge, or Brave." >&2
  exit 1
fi

mkdir -p "$PROFILE_DIR"

cleanup() {
  if [[ -n "${DEV_PID:-}" ]] && kill -0 "$DEV_PID" >/dev/null 2>&1; then
    kill "$DEV_PID" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT INT TERM

cd "$ROOT_DIR"
./scripts/dev.sh &
DEV_PID=$!

if ! wait_for_url; then
  echo "Timed out waiting for $DEV_URL" >&2
  exit 1
fi

"$BROWSER_BIN" \
  --user-data-dir="$PROFILE_DIR" \
  --app="$DEV_URL" \
  --start-maximized \
  >/dev/null 2>&1 &

wait "$DEV_PID"
