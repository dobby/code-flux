#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR_PATH="$ROOT_DIR/backend/build/libs/code-flux-dashboard.jar"
PORT="${SERVER_PORT:-8086}"
DEFAULT_CONFIG_PATH="$ROOT_DIR/config/config.yaml"

if [[ ! -f "$JAR_PATH" ]]; then
  echo "Release JAR not found at $JAR_PATH. Run ./scripts/build-release.sh first." >&2
  exit 1
fi

resolve_java_bin() {
  if [[ -n "${JAVA_BIN:-}" && -x "${JAVA_BIN:-}" ]]; then
    printf '%s\n' "$JAVA_BIN"
    return 0
  fi

  if [[ -x /usr/libexec/java_home ]]; then
    local java_home_21
    java_home_21="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
    if [[ -n "$java_home_21" && -x "$java_home_21/bin/java" ]]; then
      printf '%s\n' "$java_home_21/bin/java"
      return 0
    fi
  fi

  local sdkman_java
  sdkman_java="$(find "$HOME/.sdkman/candidates/java" -maxdepth 1 -type d -name '21*' | sort -r | head -n 1 || true)"
  if [[ -n "$sdkman_java" && -x "$sdkman_java/bin/java" ]]; then
    printf '%s\n' "$sdkman_java/bin/java"
    return 0
  fi

  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    printf '%s\n' "${JAVA_HOME}/bin/java"
    return 0
  fi

  if [[ -x "$HOME/.sdkman/candidates/java/current/bin/java" ]]; then
    printf '%s\n' "$HOME/.sdkman/candidates/java/current/bin/java"
    return 0
  fi

  command -v java
}

JAVA_BIN="$(resolve_java_bin)"
JAVA_VERSION_OUTPUT="$("$JAVA_BIN" -version 2>&1 | head -n 1)"
JAVA_MAJOR="$(printf '%s' "$JAVA_VERSION_OUTPUT" | sed -E 's/.*version "([0-9]+).*/\1/')"

if [[ -z "$JAVA_MAJOR" || "$JAVA_MAJOR" -lt 21 ]]; then
  echo "Java 21+ is required to run $JAR_PATH." >&2
  echo "Resolved java: $JAVA_BIN" >&2
  echo "Detected version: $JAVA_VERSION_OUTPUT" >&2
  exit 1
fi

export APP_BASE_URL="${APP_BASE_URL:-http://localhost:${PORT}}"
export APP_CONFIG_FILE="${APP_CONFIG_FILE:-$DEFAULT_CONFIG_PATH}"

if [[ ! -f "$APP_CONFIG_FILE" ]]; then
  echo "Config file not found at $APP_CONFIG_FILE." >&2
  exit 1
fi

cd "$ROOT_DIR"
exec "$JAVA_BIN" -jar "$JAR_PATH"
