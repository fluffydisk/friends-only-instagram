#!/usr/bin/env python3
"""Generate own login monogram from the bundled font; no source artwork is edited."""
from pathlib import Path
import hashlib, json
from PIL import Image, ImageDraw, ImageFont
root = Path(__file__).resolve().parent
manifest = {}
for qualifier, size in [('xhdpi', 192), ('xxhdpi', 288)]:
    image = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((3, 3, size - 4, size - 4), radius=size * .23,
                           fill=(21, 22, 24, 255), outline=(104, 105, 109, 255), width=max(1, size // 144))
    font = ImageFont.truetype(str(root / 'Manrope.ttf'), int(size * .33))
    font.set_variation_by_axes([450])
    draw.text((size / 2, size / 2), 'FO', anchor='mm', font=font, fill=(237, 237, 239, 255))
    path = root / ('login-mark-' + qualifier + '.png')
    image.save(path)
    payload = path.read_bytes()
    manifest[path.name] = {'size': len(payload), 'sha256': hashlib.sha256(payload).hexdigest(), 'dimensions': [size, size]}
(root / 'login-mark-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
