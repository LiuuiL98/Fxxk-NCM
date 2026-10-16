#!/usr/bin/env python3
"""Generate a safe-area launcher PNG for Fxxk-NCM.

The final asset is a normal RGB PNG (not an adaptive-icon foreground layer),
with the complete charcoal background and a centered burgundy music-note mark.
"""
from pathlib import Path
from PIL import Image, ImageDraw

SIZE = 512
SCALE = 4
HI = SIZE * SCALE
BACKGROUND = (41, 42, 46)
LIGHT_BURGUNDY = (207, 35, 77)
DEEP_BURGUNDY = (105, 0, 32)

# Rasterize the glyph at 4x then downsample for smooth edges.
mask_hi = Image.new("L", (HI, HI), 0)
draw = ImageDraw.Draw(mask_hi)

def p(points):
    return [(round(x * SCALE), round(y * SCALE)) for x, y in points]

# A single double-eighth-note symbol. Its full shape is kept within a safe margin.
draw.polygon(p([(199, 135), (354, 94), (354, 137), (211, 178)]), fill=255)
draw.polygon(p([(199, 135), (224, 142), (224, 337), (199, 337)]), fill=255)
draw.polygon(p([(329, 101), (354, 94), (354, 315), (329, 315)]), fill=255)
draw.ellipse((118*SCALE, 298*SCALE, 230*SCALE, 397*SCALE), fill=255)
draw.ellipse((275*SCALE, 275*SCALE, 387*SCALE, 374*SCALE), fill=255)
mask = mask_hi.resize((SIZE, SIZE), Image.Resampling.LANCZOS)

# Flat charcoal tile, with a subtle diagonal burgundy-to-crimson gradient on the note.
icon = Image.new("RGB", (SIZE, SIZE), BACKGROUND)
gradient = Image.new("RGB", (SIZE, SIZE))
pixels = gradient.load()
for y in range(SIZE):
    for x in range(SIZE):
        t = max(0.0, min(1.0, 0.62 * (1 - x / (SIZE - 1)) + 0.38 * (y / (SIZE - 1))))
        pixels[x, y] = tuple(
            round(LIGHT_BURGUNDY[c] * (1 - t) + DEEP_BURGUNDY[c] * t)
            for c in range(3)
        )
icon.paste(gradient, (0, 0), mask)

output = Path("app/src/main/res/mipmap-nodpi/ic_launcher.png")
output.parent.mkdir(parents=True, exist_ok=True)
icon.save(output, format="PNG", optimize=True, compress_level=9)

# Validate the exact saved asset so corrupt/truncated data cannot reach AAPT2.
with Image.open(output) as check:
    check.verify()
with Image.open(output) as check:
    assert check.format == "PNG" and check.mode == "RGB" and check.size == (SIZE, SIZE)
print(f"Generated launcher icon: {output} ({output.stat().st_size} bytes, {SIZE}x{SIZE} RGB PNG)")
