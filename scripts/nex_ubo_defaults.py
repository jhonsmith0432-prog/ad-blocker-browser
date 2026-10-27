#!/usr/bin/env python3
"""Unpack the bundled uBlock Origin XPI into assets/extensions/ublock/ and turn on its
cookie-notice and app-banner lists for new installs.

GeckoView installs built-in extensions only from an unpacked folder (resource://.../ublock/);
a packed .xpi fails with "This URI does not point to a folder". Built-in extensions are not
signature-checked, so the Mozilla signature files are left out.
uBO picks its starting filter lists from assets/assets.json (lists without "off": true).
"""
import json
import pathlib
import sys
import zipfile

ENABLE = [
    "fanboy-cookiemonster",
    "ublock-cookies-easylist",
    "adguard-mobile-app-banners",
]

xpi = pathlib.Path(sys.argv[1])
out = xpi.parent / "ublock"
if out.exists():
    import shutil
    shutil.rmtree(out)
with zipfile.ZipFile(xpi) as src:
    for item in src.infolist():
        if item.is_dir() or item.filename.startswith("META-INF/"):
            continue
        data = src.read(item.filename)
        if item.filename == "assets/assets.json":
            assets = json.loads(data)
            for key in ENABLE:
                if key not in assets:
                    sys.exit(f"nex_ubo_defaults: {key} missing from assets.json")
                assets[key].pop("off", None)
            data = json.dumps(assets, indent=1).encode("utf-8")
        target = out / item.filename
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
if not (out / "manifest.json").exists():
    sys.exit("nex_ubo_defaults: manifest.json missing after unpacking")
xpi.unlink()
print(f"nex_ubo_defaults: unpacked to {out}; enabled " + ", ".join(ENABLE))
