#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${CODE_FLUX_ELECTRON_BACKEND_MODE:-auto}"
RESOURCES_DIR="$ROOT_DIR/desktop/resources"
BACKEND_DIR="$RESOURCES_DIR/backend"
CONFIG_DIR="$RESOURCES_DIR/config"
JAR_SOURCE="$ROOT_DIR/backend/build/libs/code-flux-dashboard.jar"
NATIVE_SOURCE="${NATIVE_EXECUTABLE_PATH:-$ROOT_DIR/backend/build/native/nativeCompile/code-flux-dashboard}"

rm -rf "$BACKEND_DIR"
mkdir -p "$BACKEND_DIR/jar" "$BACKEND_DIR/native" "$CONFIG_DIR"

cp "$ROOT_DIR/config/config.example.yaml" "$CONFIG_DIR/config.example.yaml"

if [[ -f "$JAR_SOURCE" ]]; then
  cp "$JAR_SOURCE" "$BACKEND_DIR/jar/code-flux-dashboard.jar"
fi

if [[ -f "$NATIVE_SOURCE" ]]; then
  cp "$NATIVE_SOURCE" "$BACKEND_DIR/native/code-flux-dashboard"
  chmod +x "$BACKEND_DIR/native/code-flux-dashboard"
fi

case "$MODE" in
  native)
    [[ -x "$BACKEND_DIR/native/code-flux-dashboard" ]] || {
      echo "Native backend artifact not found at $NATIVE_SOURCE" >&2
      exit 1
    }
    ;;
  jar)
    [[ -f "$BACKEND_DIR/jar/code-flux-dashboard.jar" ]] || {
      echo "Jar backend artifact not found at $JAR_SOURCE" >&2
      exit 1
    }
    ;;
  auto)
    if [[ ! -x "$BACKEND_DIR/native/code-flux-dashboard" && ! -f "$BACKEND_DIR/jar/code-flux-dashboard.jar" ]]; then
      echo "No desktop backend artifacts were prepared." >&2
      exit 1
    fi
    ;;
  *)
    echo "Unsupported CODE_FLUX_ELECTRON_BACKEND_MODE: $MODE" >&2
    exit 1
    ;;
esac
