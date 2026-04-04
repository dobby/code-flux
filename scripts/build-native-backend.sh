#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

resolve_native_image_bin() {
  if [[ -n "${NATIVE_IMAGE_BIN:-}" && -x "${NATIVE_IMAGE_BIN:-}" ]]; then
    printf '%s\n' "$NATIVE_IMAGE_BIN"
    return 0
  fi

  if [[ -n "${GRAALVM_HOME:-}" && -x "${GRAALVM_HOME}/bin/native-image" ]]; then
    printf '%s\n' "${GRAALVM_HOME}/bin/native-image"
    return 0
  fi

  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/native-image" ]]; then
    printf '%s\n' "${JAVA_HOME}/bin/native-image"
    return 0
  fi

  if [[ -x "$HOME/.sdkman/candidates/java/current/bin/native-image" ]]; then
    printf '%s\n' "$HOME/.sdkman/candidates/java/current/bin/native-image"
    return 0
  fi

  command -v native-image 2>/dev/null || true
}

NATIVE_IMAGE_BIN="$(resolve_native_image_bin)"
if [[ -z "$NATIVE_IMAGE_BIN" ]]; then
  echo "native-image was not found. Install GraalVM JDK 21+ with native-image, or set GRAALVM_HOME/JAVA_HOME/NATIVE_IMAGE_BIN." >&2
  exit 1
fi

"$ROOT_DIR/scripts/prepare-static-assets.sh"

cd "$ROOT_DIR/backend"
./gradlew clean bootJar nativeCompile

CODE_FLUX_ELECTRON_BACKEND_MODE=native "$ROOT_DIR/scripts/prepare-desktop-resources.sh"
