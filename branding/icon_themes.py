"""Premium app icons: our shield in 23 designs, one per launcher alias Fenix already has.

Fenix ships Firefox fox artwork for its alternate icons (a Mozilla trademark), so every alias
gets new background + foreground + picker-preview drawables, the themed-icon (monochrome)
silhouette is replaced, and the featured designs are renamed in all 14 languages.

Usage: python3 branding/icon_themes.py <path to mobile/android/fenix/app/src/main>
"""
import pathlib
import re
import sys

SHIELD = "M256 92l140 56v92c0 88-58 156-140 182C174 396 116 328 116 240v-92z"
EYE = "M172 250c26-40 54-60 84-60s58 20 84 60c-26 40-54 60-84 60s-58-20-84-60z"
PUPIL = "M234,250a22,22 0,1 0,44 0a22,22 0,1 0,-44 0z"
SLASH = "M176,330L336,170"
RECT = "M0,0h108v108h-108z"

INK = "#0A0D1C"
MINT = "#3DF5B0"
WHITE = "#FFFFFF"

# bg: colour or list of stops (diagonal gradient). shield: colour or list of stops (top->bottom).
# accent: eye + pupil + slash. cut: outline around the slash (None = no outline).
# outline: (colour, width) stroke around the shield.
D = {
    "default": dict(bg=MINT, shield=INK, accent=MINT, cut=INK),
    "solid_light": dict(bg="#F4F6FB", shield=INK, accent=MINT, cut=INK),
    "solid_dark": dict(bg=INK, shield="#1C2447", accent=MINT, cut="#1C2447"),
    "solid_red": dict(bg="#E0245E", shield=WHITE, accent="#E0245E", cut=WHITE),
    "solid_green": dict(bg="#12B886", shield=WHITE, accent="#0C8A64", cut=WHITE),
    "solid_blue": dict(bg="#2F7BFF", shield=WHITE, accent="#2F7BFF", cut=WHITE),
    "solid_purple": dict(bg="#7C5CFF", shield=WHITE, accent="#7C5CFF", cut=WHITE),
    "solid_purple_dark": dict(bg="#2A1B6B", shield="#B39DFF", accent="#2A1B6B", cut="#B39DFF"),
    "gradient_sunrise": dict(bg=["#FF9A8B", "#FFD86F"], shield=WHITE, accent="#E8556D", cut=WHITE),
    "gradient_golden_hour": dict(bg=["#F7B733", "#FC4A1A"], shield=WHITE, accent="#C2410C", cut=WHITE),
    "gradient_sunset": dict(bg=["#FF5F6D", "#7B2FF7"], shield=WHITE, accent="#B4236B", cut=WHITE),
    "gradient_blue_hour": dict(bg=["#56CCF2", "#2F80ED"], shield=WHITE, accent="#1F5FBF", cut=WHITE),
    "gradient_twilight": dict(bg=["#3A1C71", "#D76D77", "#FFAF7B"], shield=WHITE, accent="#3A1C71", cut=WHITE),
    "gradient_midnight": dict(bg=["#0F2027", "#203A43", "#2C5364"], shield=MINT, accent="#0F2027", cut=MINT),
    "gradient_northern_lights": dict(bg=["#00C9A7", "#3D5AFE", "#7C4DFF"], shield=WHITE, accent="#3D5AFE", cut=WHITE),
    # Featured (Premium showcase)
    "retro_2004": dict(bg=["#2A2210", "#0B0904"], shield=["#FFE9A3", "#F5B331", "#A86F0C"],
                       accent="#17110A", cut="#F5B331"),  # Gold
    "pixelated": dict(bg="#070A16", shield="#0E1328", accent="#FF3DCB", cut="#0E1328",
                      outline=(MINT, 16)),  # Neon
    "cuddling": dict(bg=["#3DF5B0", "#1B6B73", "#0B1033"], shield=WHITE, accent="#0B1033", cut=WHITE),  # Aurora
    "pride": dict(bg="#FFFFFF", shield=["#E40303", "#FF8C00", "#FFED00", "#008026", "#004DFF", "#750787"],
                  accent=WHITE, cut=None),  # Rainbow
    "flaming": dict(bg="#120707", shield=["#FFD166", "#FF6B35", "#D7263D"], accent="#120707", cut=None),  # Ember
    "minimal": dict(bg=WHITE, shield=WHITE, accent=INK, cut=WHITE, outline=(INK, 14)),
    "momo": dict(bg="#000000", shield="#1A1A1A", accent="#606060", cut="#1A1A1A"),  # Stealth
    "cool": dict(bg=["#00F2FE", "#4FACFE"], shield="#59FFFFFF", accent=WHITE, cut=None,
                 outline=("#E6FFFFFF", 10)),  # Glass
}

# alias suffix -> slot (file stem used by Fenix's mipmap-anydpi/ic_launcher_<slot>.xml)
ALIASES = {
    "AppDefault": "default", "AppSolidLight": "solid_light", "AppSolidDark": "solid_dark",
    "AppSolidRed": "solid_red", "AppSolidGreen": "solid_green", "AppSolidBlue": "solid_blue",
    "AppSolidPurple": "solid_purple", "AppSolidPurpleDark": "solid_purple_dark",
    "AppGradientSunrise": "gradient_sunrise", "AppGradientGoldenHour": "gradient_golden_hour",
    "AppGradientSunset": "gradient_sunset", "AppGradientBlueHour": "gradient_blue_hour",
    "AppGradientTwilight": "gradient_twilight", "AppGradientMidnight": "gradient_midnight",
    "AppGradientNorthernLights": "gradient_northern_lights", "AppRetro2004": "retro_2004",
    "AppPixelated": "pixelated", "AppCuddling": "cuddling", "AppPride": "pride", "AppFlaming": "flaming",
    "AppMinimal": "minimal", "AppMomo": "momo", "AppCool": "cool",
}

# New names for the featured designs (the Firefox originals named fox artwork).
NAMES = {
    "retro_2004": {"values": "Gold", "es": "Oro", "pt-rBR": "Ouro", "fr": "Or", "de": "Gold", "it": "Oro",
                   "ru": "Золото", "tr": "Altın", "ar": "ذهبي", "hi-rIN": "गोल्ड", "in": "Emas",
                   "ja": "ゴールド", "ko": "골드", "zh-rCN": "金色"},
    "pixelated": {"values": "Neon", "es": "Neón", "pt-rBR": "Neon", "fr": "Néon", "de": "Neon", "it": "Neon",
                  "ru": "Неон", "tr": "Neon", "ar": "نيون", "hi-rIN": "नियॉन", "in": "Neon",
                  "ja": "ネオン", "ko": "네온", "zh-rCN": "霓虹"},
    "cuddling": {"values": "Aurora", "es": "Aurora", "pt-rBR": "Aurora", "fr": "Aurore", "de": "Polarlicht",
                 "it": "Aurora", "ru": "Сияние", "tr": "Kutup Işığı", "ar": "شفق", "hi-rIN": "ऑरोरा",
                 "in": "Aurora", "ja": "オーロラ", "ko": "오로라", "zh-rCN": "极光"},
    "pride": {"values": "Rainbow", "es": "Arcoíris", "pt-rBR": "Arco-íris", "fr": "Arc-en-ciel",
              "de": "Regenbogen", "it": "Arcobaleno", "ru": "Радуга", "tr": "Gökkuşağı", "ar": "قوس قزح",
              "hi-rIN": "इंद्रधनुष", "in": "Pelangi", "ja": "レインボー", "ko": "무지개", "zh-rCN": "彩虹"},
    "flaming": {"values": "Ember", "es": "Brasa", "pt-rBR": "Brasa", "fr": "Braise", "de": "Glut",
                "it": "Brace", "ru": "Пламя", "tr": "Kor", "ar": "جمر", "hi-rIN": "अंगार", "in": "Bara",
                "ja": "エンバー", "ko": "불씨", "zh-rCN": "余烬"},
    "momo": {"values": "Stealth", "es": "Sigilo", "pt-rBR": "Furtivo", "fr": "Furtif", "de": "Tarnung",
             "it": "Stealth", "ru": "Стелс", "tr": "Gizli", "ar": "خفي", "hi-rIN": "स्टेल्थ", "in": "Senyap",
             "ja": "ステルス", "ko": "스텔스", "zh-rCN": "隐身"},
    "cool": {"values": "Glass", "es": "Cristal", "pt-rBR": "Vidro", "fr": "Verre", "de": "Glas", "it": "Vetro",
             "ru": "Стекло", "tr": "Cam", "ar": "زجاج", "hi-rIN": "ग्लास", "in": "Kaca", "ja": "グラス",
             "ko": "글래스", "zh-rCN": "玻璃"},
}

HEADER = ('<?xml version="1.0" encoding="utf-8"?>\n'
          "<!-- Generated by branding/icon_themes.py -->\n")


def argb(c):
    c = c.lstrip("#")
    return "#" + (c if len(c) == 8 else "FF" + c)


def gradient(stops, x0, y0, x1, y1):
    n = len(stops) - 1
    items = "".join(f'<item android:offset="{i / n:.3f}" android:color="{argb(c)}"/>' for i, c in enumerate(stops))
    return (f'<gradient android:type="linear" android:startX="{x0}" android:startY="{y0}" '
            f'android:endX="{x1}" android:endY="{y1}">{items}</gradient>')


def fill(path, paint, box):
    """A path filled with a colour or a gradient (box = x0,y0,x1,y1 for the gradient line)."""
    if isinstance(paint, list):
        return (f'<path android:pathData="{path}"><aapt:attr name="android:fillColor">'
                f"{gradient(paint, *box)}</aapt:attr></path>")
    return f'<path android:fillColor="{argb(paint)}" android:pathData="{path}"/>'


def vector(body):
    return (HEADER + '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt"\n'
            '    android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n'
            f"  {body}\n</vector>\n")


def art(d, s):
    tx, ty = 54 - 256 * s, 54 - 257 * s
    parts = [fill(SHIELD, d["shield"], (256, 92, 256, 422))]
    if d.get("outline"):
        col, w = d["outline"]
        parts.append(f'<path android:strokeColor="{argb(col)}" android:strokeWidth="{w}" '
                     f'android:strokeLineJoin="round" android:pathData="{SHIELD}"/>')
    a = argb(d["accent"])
    parts.append(f'<path android:strokeColor="{a}" android:strokeWidth="20" android:strokeLineJoin="round" '
                 f'android:pathData="{EYE}"/>')
    parts.append(f'<path android:fillColor="{a}" android:pathData="{PUPIL}"/>')
    if d.get("cut"):
        parts.append(f'<path android:strokeColor="{argb(d["cut"])}" android:strokeWidth="34" '
                     f'android:strokeLineCap="round" android:pathData="{SLASH}"/>')
    parts.append(f'<path android:strokeColor="{a}" android:strokeWidth="18" android:strokeLineCap="round" '
                 f'android:pathData="{SLASH}"/>')
    return (f'<group android:scaleX="{s}" android:scaleY="{s}" android:translateX="{tx:.2f}" '
            f'android:translateY="{ty:.2f}">\n    ' + "\n    ".join(parts) + "\n  </group>")


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def main(main_dir):
    res = main_dir / "res"
    draw = res / "drawable"
    for slot, d in D.items():
        write(draw / f"nex_icon_bg_{slot}.xml", vector(fill(RECT, d["bg"], (0, 0, 108, 108))))
        write(draw / f"nex_icon_fg_{slot}.xml", vector(art(d, 0.17)))   # launcher: inside 66dp safe zone
        write(draw / f"nex_icon_pv_{slot}.xml", vector(art(d, 0.25)))   # picker preview
        if slot == "default":
            continue
        mip = res / "mipmap-anydpi" / f"ic_launcher_{slot}.xml"
        if not mip.exists():
            sys.exit(f"icon_themes: {mip} missing")
        write(mip, HEADER + '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
              f'    <background android:drawable="@drawable/nex_icon_bg_{slot}"/>\n'
              f'    <foreground android:drawable="@drawable/nex_icon_fg_{slot}"/>\n'
              '    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>\n'
              "</adaptive-icon>\n")

    # Themed icons (Android 13+): shield silhouette instead of the fox.
    s = 0.17
    write(draw / "ic_launcher_monochrome.xml", vector(
        f'<group android:scaleX="{s}" android:scaleY="{s}" android:translateX="{54 - 256 * s:.2f}" '
        f'android:translateY="{54 - 257 * s:.2f}"><path android:fillColor="#FFFFFFFF" '
        f'android:pathData="{SHIELD}"/></group>'))

    # Picker entries: our background + preview art; drop the Momo artist credit.
    kt = main_dir / "java/org/mozilla/fenix/iconpicker/AppIcon.kt"
    src = kt.read_text(encoding="utf-8")
    for alias, slot in ALIASES.items():
        pat = re.compile(rf"(\n    {alias}\(\n)(.*?)(\n    \)[,;])", re.S)
        m = pat.search(src)
        if not m:
            sys.exit(f"icon_themes: enum entry {alias} missing")
        title = re.search(r"titleId = (R\.string\.\w+),", m.group(2))
        if not title:
            sys.exit(f"icon_themes: titleId missing for {alias}")
        body = (f'        aliasSuffix = "{alias}",\n'
                f"        iconForegroundId = R.drawable.nex_icon_pv_{slot},\n"
                f"        iconBackground = IconBackground.Drawable(R.drawable.nex_icon_bg_{slot}),\n"
                f"        titleId = {title.group(1)},")
        src = src[:m.start(2)] + body + src[m.end(2):]
    kt.write_text(src, encoding="utf-8")

    # New names for the featured designs, in every kept language.
    for slot, names in NAMES.items():
        for loc, name in names.items():
            folders = ["values"] if loc == "values" else [p.name for p in res.glob(f"values-{loc}*")
                                                          if re.fullmatch(rf"values-{loc}(-r[A-Z]{{2}})?", p.name)]
            for folder in folders:
                f = res / folder / "strings.xml"
                if not f.exists():
                    continue
                t = f.read_text(encoding="utf-8")
                t2, n = re.subn(rf'(<string name="alternative_app_icon_option_{slot}"[^>]*>)[^<]*(</string>)',
                                lambda mm: mm.group(1) + name + mm.group(2), t, count=1)
                if folder == "values" and n == 0:
                    sys.exit(f"icon_themes: string for {slot} missing")
                f.write_text(t2, encoding="utf-8")
    print(f"icon_themes: {len(D)} icon designs written")


if __name__ == "__main__":
    main(pathlib.Path(sys.argv[1]))
