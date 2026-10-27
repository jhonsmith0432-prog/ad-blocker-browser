"""Replace Firefox / Kit (fox mascot) artwork in Fenix resources with our shield mark.

Firefox, its logos and the Kit mascot are Mozilla trademarks, so none may ship.
Vector drawables keep their original width/height (layouts stay intact); the shield is drawn
centred inside the original viewport. Raster files are re-rendered at their original size.

Usage: python3 branding/replace_logos.py <path to src/mobile/android/fenix/app/src/main/res>
"""
import glob
import io
import os
import re
import sys

MINT = "#3DF5B0"
INK = "#0A0D1C"
SHIELD = "M256 92l140 56v92c0 88-58 156-140 182C174 396 116 328 116 240v-92z"
SLASH = "M176,330L336,170"

VECTOR_PATTERNS = re.compile(
    r"(fox|firefox|^kit_|^ic_kit_|ic_splash_logo|ic_status_logo|ic_onboarding_welcome|ic_logo_wordmark)"
)
MONO = {"ic_status_logo"}  # status-bar icons must be a single white silhouette


def attr(text, name, default):
    m = re.search(rf'android:{name}="([0-9.]+)(dp)?"', text)
    return float(m.group(1)) if m else default


def shield_vector(width, height, vw, vh, mono):
    # Shield art spans ~280x330 of a 512 box (x116-396, y92-422). Fit it to 80% of the viewport.
    s = min(vw * 0.8 / 280.0, vh * 0.8 / 330.0)
    tx = vw / 2 - 256 * s
    ty = vh / 2 - 257 * s
    if mono:
        body = f'<path android:fillColor="#FFFFFFFF" android:pathData="{SHIELD}"/>'
    else:
        body = (
            f'<path android:fillColor="{INK}" android:pathData="{SHIELD}"/>'
            f'<path android:strokeColor="{MINT}" android:strokeWidth="26" android:strokeLineCap="round" '
            f'android:pathData="{SLASH}"/>'
        )
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Replaced by branding/replace_logos.py (original was Mozilla trademark artwork). -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{width:g}dp" android:height="{height:g}dp"
    android:viewportWidth="{vw:g}" android:viewportHeight="{vh:g}">
  <group android:scaleX="{s:.4f}" android:scaleY="{s:.4f}" android:translateX="{tx:.3f}" android:translateY="{ty:.3f}">
    {body}
  </group>
</vector>
"""


def replace_vectors(res):
    changed = []
    for path in glob.glob(os.path.join(res, "drawable*", "*.xml")):
        name = os.path.splitext(os.path.basename(path))[0]
        if not VECTOR_PATTERNS.search(name):
            continue
        text = open(path, encoding="utf-8").read()
        if "<vector" not in text:
            # e.g. drawable-night/ic_logo_wordmark_normal.xml: point it at our light-text wordmark.
            if "ic_logo_wordmark" in name:
                with open(path, "w", encoding="utf-8") as f:
                    f.write('<?xml version="1.0" encoding="utf-8"?>\n'
                            '<bitmap xmlns:android="http://schemas.android.com/apk/res/android"\n'
                            '    android:src="@drawable/ic_logo_wordmark_private" />\n')
                changed.append(path)
            continue
        w = attr(text, "width", 24)
        h = attr(text, "height", 24)
        vw = attr(text, "viewportWidth", w)
        vh = attr(text, "viewportHeight", h)
        with open(path, "w", encoding="utf-8") as f:
            f.write(shield_vector(w, h, vw, vh, name in MONO))
        changed.append(path)
    return changed


def replace_rasters(res):
    """Raster Kit/fox art (e.g. kit_sleeping_under_laptop.webp) -> our mark at the same size."""
    try:
        import cairosvg
        from PIL import Image
    except ImportError:
        print("WARNING: cairosvg/Pillow missing; raster mascot art not replaced", file=sys.stderr)
        return []
    changed = []
    for path in glob.glob(os.path.join(res, "drawable*", "*.webp")) + glob.glob(os.path.join(res, "drawable*", "*.png")):
        name = os.path.splitext(os.path.basename(path))[0]
        if not re.search(r"(fox|firefox|^kit_)", name):
            continue
        w, h = Image.open(path).size
        side = min(w, h)
        svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">'
               f'<g transform="translate({(w - side) / 2},{(h - side) / 2}) scale({side / 512})">'
               f'<path d="{SHIELD}" fill="{INK}"/><path d="M176 330l160-160" stroke="{MINT}" '
               f'stroke-width="26" stroke-linecap="round"/></g></svg>')
        img = Image.open(io.BytesIO(cairosvg.svg2png(bytestring=svg.encode()))).convert("RGBA")
        img.save(path, "WEBP" if path.endswith(".webp") else "PNG")
        changed.append(path)
    return changed


if __name__ == "__main__":
    res_dir = sys.argv[1]
    done = replace_vectors(res_dir) + replace_rasters(res_dir)
    print(f"Replaced {len(done)} trademark drawables")
    for p in done:
        print("  ", os.path.relpath(p, res_dir))
