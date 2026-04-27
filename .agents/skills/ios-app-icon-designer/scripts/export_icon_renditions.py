#!/usr/bin/env python3
"""Export Icon Composer renditions and optionally build a contact sheet."""

from __future__ import annotations

import argparse
import shutil
import subprocess
from pathlib import Path


RENDITIONS = ["Default", "Dark", "TintedDark", "ClearDark", "ClearLight"]
ICTOOL = Path(
    "/Applications/Xcode.app/Contents/Applications/Icon Composer.app/Contents/Executables/ictool"
)


def run(command: list[str]) -> None:
    subprocess.run(command, check=True)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("icon", type=Path, help="Path to .icon package")
    parser.add_argument("--output", type=Path, default=Path("icon-renders"))
    parser.add_argument("--width", type=int, default=512)
    parser.add_argument("--height", type=int, default=512)
    parser.add_argument("--scale", type=int, default=1)
    args = parser.parse_args()

    if not args.icon.exists():
        raise SystemExit(f"Icon package not found: {args.icon}")
    if not ICTOOL.exists():
        raise SystemExit(f"ictool not found: {ICTOOL}")

    args.output.mkdir(parents=True, exist_ok=True)
    outputs: list[Path] = []
    for rendition in RENDITIONS:
        out = args.output / f"{rendition}.png"
        run(
            [
                str(ICTOOL),
                str(args.icon),
                "--export-image",
                "--output-file",
                str(out),
                "--platform",
                "iOS",
                "--rendition",
                rendition,
                "--width",
                str(args.width),
                "--height",
                str(args.height),
                "--scale",
                str(args.scale),
            ]
        )
        outputs.append(out)

    if shutil.which("magick"):
        contact = args.output / "contact-sheet.png"
        run(["magick", *map(str, outputs), "+append", str(contact)])
        print(contact)
    else:
        for out in outputs:
            print(out)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
