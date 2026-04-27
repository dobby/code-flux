# Icon Composer Notes

## Layer Model

- A native iOS 26 app icon can be a `.icon` directory with `icon.json` and `Assets/`.
- Put layer image files at `Assets/<name>.png`. Root-level filenames resolve more reliably than nested paths.
- Layer order in `icon.json` is front-to-back. Use foreground first, then background layers.
- Keep images at 1024x1024 unless the app has an established different source size.

## Recommended Structure

Use three visual assets:

- `foreground.png`: transparent silhouette, glyph, dial, or logo.
- `background.png`: default/light full-bleed grain or color field.
- `background-dark.png`: dark-mode full-bleed grain, mostly black with restrained color.

Template:

```json
{
  "fill": "automatic",
  "groups": [
    {
      "layers": [
        {
          "glass": false,
          "image-name": "foreground.png",
          "name": "foreground",
          "opacity": 1
        },
        {
          "glass": false,
          "image-name": "background.png",
          "name": "background",
          "opacity-specializations": [
            { "appearance": "dark", "value": 0 },
            { "appearance": "tinted", "value": 0 }
          ]
        },
        {
          "glass": false,
          "image-name": "background-dark.png",
          "name": "background-dark",
          "opacity": 0,
          "opacity-specializations": [
            { "appearance": "dark", "value": 1 },
            { "appearance": "tinted", "value": 0 }
          ]
        }
      ],
      "shadow": { "kind": "neutral", "opacity": 0.5 },
      "translucency": { "enabled": true, "value": 0.5 }
    }
  ],
  "supported-platforms": {
    "circles": ["watchOS"],
    "squares": "shared"
  }
}
```

## Appearance Gotchas

- `image-name-specializations` may parse but not visibly swap images in `ictool` exports. Use separate layers plus `opacity-specializations` for appearance-specific assets.
- `ClearDark` and `ClearLight` use the `tinted` specialization path. To show only the foreground over system glass, set custom background layers to opacity `0` for `appearance: tinted`.
- `TintedDark` will recolor the foreground. Check whether the shape still reads without warm background contrast.
- The gray/black rounded plate in clear exports is Icon Composer's system material, not necessarily your image background.

## Verification

Prefer Icon Composer's bundled CLI:

```bash
ICTOOL="/Applications/Xcode.app/Contents/Applications/Icon Composer.app/Contents/Executables/ictool"
"$ICTOOL" AppIcon.icon --export-image --output-file default.png --platform iOS --rendition Default --width 1024 --height 1024 --scale 1
"$ICTOOL" AppIcon.icon --export-image --output-file dark.png --platform iOS --rendition Dark --width 1024 --height 1024 --scale 1
"$ICTOOL" AppIcon.icon --export-image --output-file tinted.png --platform iOS --rendition TintedDark --width 1024 --height 1024 --scale 1
"$ICTOOL" AppIcon.icon --export-image --output-file clear-dark.png --platform iOS --rendition ClearDark --width 1024 --height 1024 --scale 1
"$ICTOOL" AppIcon.icon --export-image --output-file clear-light.png --platform iOS --rendition ClearLight --width 1024 --height 1024 --scale 1
```

If a full Xcode build hangs but only icon assets changed, compile the asset catalog directly with `actool`, copy the produced `Assets.car` and app icon PNGs into an existing Debug app bundle, re-sign, install, and launch. Prefer a full project build when it is healthy.
