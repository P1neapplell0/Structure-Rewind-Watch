"""Deterministic hand-drawn 64px gold frame, with a genuinely empty alpha center."""
from pathlib import Path
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1]
image = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
draw = ImageDraw.Draw(image)
# Concentric metal bevel, including a dark edge on both sides of the opening.
colors = ['#372112', '#7d4a20', '#d59435', '#fff0a0', '#bb7925', '#e2b453', '#765020', '#302215']
for inset, color in enumerate(colors):
    draw.rectangle((inset, inset, 63-inset, 63-inset), outline=color)
# Fine chased ornament on each rail; all ornaments remain on the frame.
for shift in range(13, 54, 8):
    for flip in [False, True]:
        y = 4 if not flip else 59
        draw.line([(shift-2, y), (shift, y-2), (shift+2, y)], fill='#fff1ad')
        draw.point((shift, y+1), fill='#64380f')
        x = y
        draw.line([(x, shift-2), (x-2, shift), (x, shift+2)], fill='#fff1ad')
        draw.point((x+1, shift), fill='#64380f')
# Four corner stars: gilded, faceted, restrained pixel geometry.
for x, y in [(7,7),(56,7),(7,56),(56,56)]:
    draw.polygon([(x,y-6),(x+2,y-2),(x+6,y),(x+2,y+2),(x,y+6),(x-2,y+2),(x-6,y),(x-2,y-2)], fill='#563510')
    draw.polygon([(x,y-5),(x+2,y-1),(x+5,y),(x+1,y+2),(x,y+5),(x-2,y+1),(x-5,y),(x-1,y-2)], fill='#daa445')
    draw.polygon([(x,y-3),(x+3,y),(x,y+3),(x-3,y)], fill='#fff0a0')
    draw.line([(x,y-2),(x,y+2)], fill='#fff9da')
# Explicitly verify no generated background or glass fills the center.
assert image.getpixel((32, 32))[3] == 0
assert image.getchannel('A').crop((14,14,50,50)).getbbox() is None
out = root / 'src/main/resources/assets/structure_rewind_watch/textures/block/rewind_inhibitor.png'
image.save(out)
print(out)
