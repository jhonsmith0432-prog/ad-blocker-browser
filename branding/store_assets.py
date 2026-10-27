"""Google Play listing graphics.

  python3 branding/store_assets.py                 -> feature graphic + 4 screenshot frames (with empty phone slot)
  python3 branding/store_assets.py shots/*.png     -> frames with your real app captures placed in the phone

Play rules: feature graphic 1024x500, JPEG or 24-bit PNG, no alpha. For recommendation eligibility, at least
4 portrait screenshots, 9:16, min 1080x1920. Screenshots must show the real app, so pass actual captures
from the built app (adb exec-out screencap -p > shot1.png).
"""
import io
import os
import sys

import cairosvg
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "store")
INK, SURFACE, MINT, VIOLET, TEXT, MUTED = "#0A0D1C", "#141832", "#3DF5B0", "#7C5CFF", "#F4F6FF", "#A3A9C7"
SHIELD = "M256 92l140 56v92c0 88-58 156-140 182C174 396 116 328 116 240v-92z"
EYE = "M172 250c26-40 54-60 84-60s58 20 84 60c-26 40-54 60-84 60s-58-20-84-60z"
FONT = "Inter, DejaVu Sans, sans-serif"

HEADLINES = [
    ("Block ads", "and popups on every site"),
    ("Stop trackers", "before they load"),
    ("Go incognito", "no history, nothing kept"),
    ("Zero data", "collected. Not even by us."),
]


def render(svg, size, path):
    png = cairosvg.svg2png(bytestring=svg.encode(), output_width=size[0], output_height=size[1])
    img = Image.open(io.BytesIO(png)).convert("RGB")  # 24-bit, no alpha
    img.save(path, "PNG")
    return img


def icon_group(x, y, scale):
    return (f'<g transform="translate({x},{y}) scale({scale})">'
            f'<rect width="512" height="512" rx="154" fill="{MINT}"/>'
            f'<path d="{SHIELD}" fill="{INK}"/>'
            f'<path d="{EYE}" fill="none" stroke="{MINT}" stroke-width="20" stroke-linejoin="round"/>'
            f'<circle cx="256" cy="250" r="22" fill="{MINT}"/>'
            f'<path d="M176 330l160-160" stroke="{INK}" stroke-width="34" stroke-linecap="round"/>'
            f'<path d="M176 330l160-160" stroke="{MINT}" stroke-width="18" stroke-linecap="round"/></g>')


def feature_graphic():
    svg = f"""<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500">
<rect width="1024" height="500" fill="{INK}"/>
<circle cx="230" cy="250" r="190" fill="{MINT}" opacity="0.10"/>
{icon_group(110, 130, 240 / 512)}
<text x="420" y="215" font-family="{FONT}" font-weight="700" font-size="54" fill="{TEXT}">Ad Blocker &amp;</text>
<text x="420" y="280" font-family="{FONT}" font-weight="700" font-size="54" fill="{TEXT}">Private Browser</text>
<text x="420" y="335" font-family="{FONT}" font-weight="500" font-size="26" fill="{MINT}">Blocks ads &amp; trackers. Keeps nothing.</text>
</svg>"""
    return render(svg, (1024, 500), os.path.join(OUT, "feature-graphic-1024x500.png"))


def frame(i, title, sub):
    """1080x1920 frame: headline on top, phone slot below (720x1440 screen area)."""
    svg = f"""<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="1920" viewBox="0 0 1080 1920">
<rect width="1080" height="1920" fill="{INK}"/>
<text x="540" y="200" text-anchor="middle" font-family="{FONT}" font-weight="700" font-size="92" fill="{MINT}">{title}</text>
<text x="540" y="290" text-anchor="middle" font-family="{FONT}" font-weight="500" font-size="54" fill="{TEXT}">{sub}</text>
<rect x="160" y="380" width="760" height="1500" rx="72" fill="{SURFACE}" stroke="#2A3058" stroke-width="6"/>
<rect x="180" y="400" width="720" height="1460" rx="56" fill="#1B2042"/>
<text x="540" y="1140" text-anchor="middle" font-family="{FONT}" font-size="36" fill="{MUTED}">[ app screenshot {i + 1} ]</text>
</svg>"""
    return render(svg, (1080, 1920), os.path.join(OUT, f"screenshot-{i + 1}-frame.png"))


def place(frame_img, shot_path, out_path):
    shot = Image.open(shot_path).convert("RGB")
    # Fit into the 720x1460 screen slot (keep aspect, crop overflow at the bottom).
    w = 720
    h = int(shot.height * w / shot.width)
    shot = shot.resize((w, h), Image.LANCZOS).crop((0, 0, w, min(h, 1460)))
    mask = Image.new("L", shot.size, 0)
    from PIL import ImageDraw
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, shot.size[0], shot.size[1]), radius=56, fill=255)
    out = frame_img.copy()
    out.paste(shot, (180, 400), mask)
    out.save(out_path, "PNG")


def main():
    os.makedirs(OUT, exist_ok=True)
    feature_graphic()
    frames = [frame(i, t, s) for i, (t, s) in enumerate(HEADLINES)]
    for i, shot in enumerate(sys.argv[1:5]):
        place(frames[i], shot, os.path.join(OUT, f"screenshot-{i + 1}.png"))
    print("Store assets written to", OUT)


if __name__ == "__main__":
    main()
