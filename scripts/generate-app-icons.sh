#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE_IMAGE="${1:-}"
PUBLIC_DIR="$ROOT_DIR/frontend/public"
ICONS_DIR="$PUBLIC_DIR/icons"
DESKTOP_ASSETS_DIR="$ROOT_DIR/desktop/assets"

mkdir -p "$ICONS_DIR" "$DESKTOP_ASSETS_DIR"

if [[ -n "$SOURCE_IMAGE" && ! -f "$SOURCE_IMAGE" ]]; then
  echo "Source image not found at $SOURCE_IMAGE" >&2
  exit 1
fi

if [[ -z "$SOURCE_IMAGE" ]]; then
  SOURCE_IMAGE="$ICONS_DIR/icon-master.png"
fi

if [[ ! -f "$SOURCE_IMAGE" ]]; then
  echo "Usage: ./scripts/generate-app-icons.sh /absolute/path/to/source.png" >&2
  exit 1
fi

if ! command -v magick >/dev/null 2>&1; then
  echo "ImageMagick 'magick' is required to generate app icons." >&2
  exit 1
fi

TMP_TRIMMED="$(mktemp "${TMPDIR:-/tmp}/code-flux-icon-trim.XXXXXX.png")"
TMP_ICONSET_DIR="$(mktemp -d "${TMPDIR:-/tmp}/code-flux-iconset.XXXXXX")"
TMP_DESKTOP_MASTER="$(mktemp "${TMPDIR:-/tmp}/code-flux-desktop-icon.XXXXXX.png")"
trap 'rm -f "$TMP_TRIMMED" "$TMP_DESKTOP_MASTER"; rm -rf "$TMP_ICONSET_DIR"' EXIT

if [[ "$SOURCE_IMAGE" != "$ICONS_DIR/icon-master.png" ]]; then
  read -r source_width source_height <<<"$(magick "$SOURCE_IMAGE" -format '%w %h' info:)"
  magick "$SOURCE_IMAGE" \
    -alpha set \
    -fuzz 4% \
    -fill none \
    -draw "color 0,0 floodfill" \
    -draw "color $((source_width - 1)),0 floodfill" \
    -draw "color 0,$((source_height - 1)) floodfill" \
    -draw "color $((source_width - 1)),$((source_height - 1)) floodfill" \
    -trim +repage \
    -bordercolor none \
    -border 12 \
    "$TMP_TRIMMED"

  read -r trimmed_width trimmed_height <<<"$(magick "$TMP_TRIMMED" -format '%w %h' info:)"
  canvas_size=$(( trimmed_width > trimmed_height ? trimmed_width : trimmed_height ))

  magick "$TMP_TRIMMED" \
    -background none \
    -gravity center \
    -extent "${canvas_size}x${canvas_size}" \
    "$ICONS_DIR/icon-master.png"
fi

for size in 16 32 64 128 180 192 256 512 1024; do
  magick "$ICONS_DIR/icon-master.png" -resize "${size}x${size}" "$ICONS_DIR/icon-${size}.png"
done

cp "$ICONS_DIR/icon-180.png" "$PUBLIC_DIR/apple-touch-icon.png"
cp "$ICONS_DIR/icon-32.png" "$PUBLIC_DIR/favicon-32x32.png"

magick "$ICONS_DIR/icon-master.png" \
  -trim +repage \
  -background none \
  -gravity center \
  -resize 944x944 \
  -extent 1024x1024 \
  "$TMP_DESKTOP_MASTER"

cp "$TMP_DESKTOP_MASTER" "$DESKTOP_ASSETS_DIR/icon.png"

magick "$ICONS_DIR/icon-master.png" \
  \( -clone 0 -resize 16x16 \) \
  \( -clone 0 -resize 32x32 \) \
  -delete 0 \
  "$PUBLIC_DIR/favicon.ico"

if command -v iconutil >/dev/null 2>&1; then
  ICONSET_DIR="$TMP_ICONSET_DIR/CodeFlux.iconset"
  mkdir -p "$ICONSET_DIR"
  magick "$TMP_DESKTOP_MASTER" -resize 16x16 "$ICONSET_DIR/icon_16x16.png"
  magick "$TMP_DESKTOP_MASTER" -resize 32x32 "$ICONSET_DIR/icon_16x16@2x.png"
  magick "$TMP_DESKTOP_MASTER" -resize 32x32 "$ICONSET_DIR/icon_32x32.png"
  magick "$TMP_DESKTOP_MASTER" -resize 64x64 "$ICONSET_DIR/icon_32x32@2x.png"
  magick "$TMP_DESKTOP_MASTER" -resize 128x128 "$ICONSET_DIR/icon_128x128.png"
  magick "$TMP_DESKTOP_MASTER" -resize 256x256 "$ICONSET_DIR/icon_128x128@2x.png"
  magick "$TMP_DESKTOP_MASTER" -resize 256x256 "$ICONSET_DIR/icon_256x256.png"
  magick "$TMP_DESKTOP_MASTER" -resize 512x512 "$ICONSET_DIR/icon_256x256@2x.png"
  magick "$TMP_DESKTOP_MASTER" -resize 512x512 "$ICONSET_DIR/icon_512x512.png"
  magick "$TMP_DESKTOP_MASTER" -resize 1024x1024 "$ICONSET_DIR/icon_512x512@2x.png"
  iconutil -c icns "$ICONSET_DIR" -o "$DESKTOP_ASSETS_DIR/icon.icns"
else
  echo "Warning: iconutil is unavailable; desktop/assets/icon.icns was not generated." >&2
fi

echo "Generated icon assets in $PUBLIC_DIR and $DESKTOP_ASSETS_DIR"
