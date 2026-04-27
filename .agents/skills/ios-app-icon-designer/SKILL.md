---
name: ios-app-icon-designer
description: Design, generate, refine, and install native iOS app icons with designer-style exploration sheets and iOS 26 Icon Composer layering. Use when the user asks to redesign an app icon, generate icon directions, create a design sheet, refine a chosen direction, separate foreground/background layers, support dark/tinted/clear glass modes, edit a .icon package, export Icon Composer renditions, or run/install the app icon on an iOS device.
---

# iOS App Icon Designer

## Workflow

1. Inspect the app before designing:
   - Read the current icon source, usually an `.icon` folder or `Assets.xcassets/AppIcon`.
   - Review app screenshots or run the app to understand color, contrast, texture, and brand feel.
   - Note the app category, primary metaphor, and colors that should survive at Home Screen size.

2. Start with a design exploration sheet:
   - Generate a single sheet, not one isolated final icon.
   - Include the current icon, rough sketches, and 6-10 labeled directions.
   - Vary metaphor, silhouette, palette, foreground density, timer/progress language, and material treatment.
   - Keep annotations short, like a designer board: `warm grain`, `dark glass`, `timer arc`, `breath spiral`, `foreground only`.
   - Ask for or infer a preferred direction, then create refined variations around that direction.

3. Produce final artwork as layers:
   - Generate the background and foreground separately once the direction is chosen.
   - Keep the foreground as generated artwork when the user likes it; do not replace it with procedural redraws unless explicitly requested.
   - Preserve texture by generating or editing a dedicated background layer instead of extracting it from a flattened icon.
   - Use a transparent foreground PNG where possible. If transparent generation fails, generate on a solid chroma color and key it carefully.

4. Build the iOS `.icon` package:
   - Use Icon Composer layering, not a single flattened PNG, for iOS 26 dark/tinted/clear behavior.
   - Put foreground first and background behind it. Icon Composer JSON layer order is front-to-back.
   - Add separate layers for appearance-specific backgrounds. Prefer opacity specializations over image-name specializations.
   - Hide custom background layers in tinted/clear glass modes so the system material shows through behind the foreground.

5. Verify before installing:
   - Export `Default`, `Dark`, `TintedDark`, `ClearDark`, and `ClearLight` with Icon Composer's `ictool`.
   - Create a contact sheet and inspect the actual rendered results.
   - Compile the asset catalog or build the app so Xcode proves the `.icon` package is valid.
   - Install/run on device when the user asks for `rod` or the repo requires physical-device verification.

## Production Rules

- Prefer generated bitmap artwork for the icon's actual visual layers. Use procedural image operations only for trimming, scaling, color grading, alpha cleanup, and contact sheets.
- Keep the icon full-bleed. Do not leave a white border or unintentional transparent margin in the default rendition.
- Scale the foreground inside a transparent 1024px canvas so masks do not crop the silhouette or timer arc.
- Keep dark mode mostly black with subtle retained grain and restrained color hints.
- Treat `glass mode`, `transparent mode`, and `clear mode` as Icon Composer clear/tinted renditions. The custom background should usually be hidden there.
- If Xcode or Icon Composer GUI automation is unreliable, use the bundled `ictool` CLI and `actool` for deterministic verification.

## Useful Commands

Read `references/icon-composer.md` before editing `.icon` JSON or debugging Icon Composer renditions.

Export renditions and a contact sheet:

```bash
python3 .agents/skills/ios-app-icon-designer/scripts/export_icon_renditions.py \
  /path/to/AppIcon.icon \
  --output /tmp/icon-renders
```

Common foreground scale operation:

```bash
magick foreground-source.png -resize 840x840 -background none -gravity center -extent 1024x1024 foreground.png
```

Common dark grain derivation:

```bash
magick background.png -evaluate multiply 0.22 background-dark.png
```

Common chroma-key cleanup when transparent generation is unavailable:

```bash
magick foreground-magenta.png -resize 1024x1024 -alpha set -fuzz 28% -transparent '#ff00ff' foreground.png
```
