#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_MODE="${CODE_FLUX_ELECTRON_BACKEND_MODE:-native}"

"$ROOT_DIR/scripts/generate-app-icons.sh"

case "$BACKEND_MODE" in
  native)
    "$ROOT_DIR/scripts/build-native-backend.sh"
    ;;
  jar)
    "$ROOT_DIR/scripts/build-release.sh"
    CODE_FLUX_ELECTRON_BACKEND_MODE=jar "$ROOT_DIR/scripts/prepare-desktop-resources.sh"
    ;;
  *)
    echo "Unsupported CODE_FLUX_ELECTRON_BACKEND_MODE: $BACKEND_MODE" >&2
    exit 1
    ;;
esac

cd "$ROOT_DIR/desktop"
npm ci
npm run package -- "$@"
