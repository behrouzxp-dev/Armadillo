#!/usr/bin/env python3
"""Build launcher assets from the owner's original icon artwork.

The source artwork is never edited. This script only rescales it and adds the
margins that adaptive icons need, then regenerates the legacy mipmap PNGs.

Usage:
    python3 tools/build_icon.py
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
SOURCE = RES / "drawable-nodpi" / "armadillo_icon_art.jpg"
FOREGROUND = RES / "drawable-nodpi" / "armadillo_icon_foreground.png"
MONOCHROME = RES / "drawable-nodpi" / "armadillo_icon_monochrome.png"
HERO = RES / "drawable-nodpi" / "armadillo_icon_hero.png"
HOME_LOGO = RES / "drawable-nodpi" / "armadillo_home_logo.png"

CANVAS = 1024
# Adaptive icons are 108dp with a 72dp visible mask, so the artwork must sit
# inside the central safe zone. 0.62 keeps a comfortable margin on every shape.
ART_FRACTION = 0.62
BACKGROUND = (249, 203, 3)  # sampled from the artwork, reused as the app canvas

MIPMAP_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def background_bbox(image: Image.Image, tolerance: int = 26) -> tuple[int, int, int, int]:
    """Return the bounding box of everything that is not background colour."""
    rgb = image.convert("RGB")
    width, height = rgb.size
    pixels = rgb.load()
    left, top, right, bottom = width, height, 0, 0
    for y in range(height):
        for x in range(width):
            r, g, b = pixels[x, y]
            if abs(r - BACKGROUND[0]) + abs(g - BACKGROUND[1]) + abs(b - BACKGROUND[2]) > tolerance:
                left = min(left, x)
                right = max(right, x)
                top = min(top, y)
                bottom = max(bottom, y)
    if right <= left or bottom <= top:
        return (0, 0, width, height)
    return (left, top, right + 1, bottom + 1)


def scaled_art(source: Image.Image, fraction: float) -> Image.Image:
    rgb = source.convert("RGB")
    left, top, right, bottom = background_bbox(rgb)
    art = rgb.crop((left, top, right, bottom))
    target = int(CANVAS * fraction)
    scale = min(target / art.width, target / art.height)
    resized = art.resize((max(1, int(art.width * scale)), max(1, int(art.height * scale))), Image.LANCZOS)
    canvas = Image.new("RGB", (CANVAS, CANVAS), BACKGROUND)
    canvas.paste(resized, ((CANVAS - resized.width) // 2, (CANVAS - resized.height) // 2))
    return canvas


def monochrome_art(padded: Image.Image) -> Image.Image:
    """Silhouette of the artwork for the Android 13+ themed icon layer."""
    rgb = padded.convert("RGB")
    alpha = Image.new("L", rgb.size, 0)
    source_pixels = rgb.load()
    alpha_pixels = alpha.load()
    for y in range(rgb.height):
        for x in range(rgb.width):
            r, g, b = source_pixels[x, y]
            # The drawing is dark ink on a bright yellow field; keep the ink.
            if (r + g + b) / 3 < 150:
                alpha_pixels[x, y] = 255
    silhouette = Image.new("RGBA", rgb.size, (255, 255, 255, 0))
    silhouette.putalpha(alpha)
    return silhouette


def transparent_home_logo(padded: Image.Image) -> Image.Image:
    """Extract the navy ink as a transparent, antialiased home-screen PNG.

    The original artwork and the opaque launcher assets remain unchanged. The
    red channel distinguishes the dark ink from the yellow source background;
    intermediate values retain smooth edges when drawn over the brand canvas.
    """
    ink = (7, 34, 60)
    red = padded.convert("RGB").getchannel("R")
    alpha = red.point(lambda value: max(0, min(255, round((BACKGROUND[0] - value - 6) * 255 / (BACKGROUND[0] - ink[0] - 6)))))
    bounds = alpha.getbbox()
    if bounds is None:
        raise ValueError("No artwork found for the home logo")
    alpha = alpha.crop(bounds)
    alpha.thumbnail((320, 320), Image.LANCZOS)
    logo = Image.new("RGBA", alpha.size, (*ink, 0))
    logo.putalpha(alpha)
    return logo


def legacy_icon(padded: Image.Image, size: int, round_icon: bool) -> Image.Image:
    icon = padded.resize((size, size), Image.LANCZOS).convert("RGBA")
    if round_icon:
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
        icon.putalpha(mask)
    return icon


def main() -> None:
    source = Image.open(SOURCE)
    padded = scaled_art(source, ART_FRACTION)
    padded.save(FOREGROUND, optimize=True)
    monochrome_art(padded).save(MONOCHROME, optimize=True)
    # Keep the previous hero bitmap for compatibility with existing assets.
    padded.resize((320, 320), Image.LANCZOS).save(HERO, optimize=True)
    transparent_home_logo(padded).save(HOME_LOGO, optimize=True)
    for folder, size in MIPMAP_SIZES.items():
        target = RES / folder
        target.mkdir(parents=True, exist_ok=True)
        legacy_icon(padded, size, round_icon=False).save(target / "ic_launcher.png", optimize=True)
        legacy_icon(padded, size, round_icon=True).save(target / "ic_launcher_round.png", optimize=True)
    print(f"foreground {FOREGROUND.name}: {padded.size[0]}px, artwork at {ART_FRACTION:.0%}")
    print(f"mipmaps regenerated: {', '.join(f'{k}={v}px' for k, v in MIPMAP_SIZES.items())}")


if __name__ == "__main__":
    main()
