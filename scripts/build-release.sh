#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

"$ROOT_DIR/scripts/prepare-static-assets.sh"

cd "$ROOT_DIR/backend"
./gradlew clean bootJar
