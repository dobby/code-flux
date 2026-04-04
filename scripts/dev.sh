#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_PORT="${SERVER_PORT:-8086}"
FRONTEND_HOST="${FRONTEND_HOST:-127.0.0.1}"
FRONTEND_PORT="${FRONTEND_PORT:-4173}"
API_PROXY_TARGET="${VITE_API_PROXY_TARGET:-http://${FRONTEND_HOST}:${BACKEND_PORT}}"

(
  cd "$ROOT_DIR/backend"
  SERVER_PORT="$BACKEND_PORT" APP_CONFIG_FILE="$ROOT_DIR/config/config.yaml" ./gradlew bootRun
) &
BACKEND_PID=$!

cleanup() {
  kill "$BACKEND_PID" >/dev/null 2>&1 || true
}

trap cleanup EXIT

cd "$ROOT_DIR/frontend"
VITE_API_PROXY_TARGET="$API_PROXY_TARGET" npm run dev -- --host "$FRONTEND_HOST" --port "$FRONTEND_PORT"
