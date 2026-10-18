#!/usr/bin/env python3
"""Offline developer utility: native-resolution wordmarks from the bundled OFL font.

Requires Pillow + fonttools; not run in the Android app or the APK build.
It does not edit user photographs, use an image model, or download anything.
"""
from pathlib import Path
import hashlib
import json
import zlib
from PIL import Image, ImageFont, ImageDraw

ROOT = Path(__file__).resolve().parent
manifest = {}
for qualifier, dimensions in {"xhdpi": (233, 66), "night-xhdpi": (233, 66),
                              "xxhdpi": (349, 99), "night-xxhdpi": (349, 99)}.items():
    image = Image.new("RGBA", dimensions, (0, 0, 0, 0))
    text = "Friends Only"
    size = int(image.height * .75)
    while size > 8:
        font = ImageFont.truetype(str(ROOT / "Manrope.ttf"), size)
        font.set_variation_by_axes([450])
        spacing = size * .08
        width = sum(font.getlength(character) for character in text) + spacing * (len(text) - 1)
        if width <= image.width * .98:
            break
        size -= 1
    box = font.getbbox(text)
    y = (image.height - (box[3] - box[1])) / 2 - box[1]
    x = (image.width - width) / 2
    draw = ImageDraw.Draw(image)
    color = (237, 237, 239, 255) if qualifier.startswith("night") else (40, 41, 44, 255)
    for character in text:
        draw.text((x, y), character, font=font, fill=color)
        x += font.getlength(character) + spacing
    path = ROOT / ("wordmark-" + qualifier + ".webp")
    image.save(path, format="WEBP", lossless=True)
    payload = path.read_bytes()
    manifest[path.name] = {"size": len(payload), "sha256": hashlib.sha256(payload).hexdigest(),
                           "crc32": zlib.crc32(payload), "dimensions": dimensions}
(ROOT / "wordmark-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
