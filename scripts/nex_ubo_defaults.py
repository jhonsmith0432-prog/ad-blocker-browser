#!/usr/bin/env python3
"""Turn on uBlock Origin's cookie-notice and app-banner lists for new installs.

uBO picks its starting filter lists from assets/assets.json (lists without "off": true).
The bundled copy is installed as a built-in extension, so its Mozilla signature is not
needed and is dropped once the archive is modified.
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
tmp = xpi.with_suffix(".tmp")
with zipfile.ZipFile(xpi) as src, zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as dst:
    for item in src.infolist():
        if item.filename.startswith("META-INF/"):
            continue
        data = src.read(item.filename)
        if item.filename == "assets/assets.json":
            assets = json.loads(data)
            for key in ENABLE:
                if key not in assets:
                    sys.exit(f"nex_ubo_defaults: {key} missing from assets.json")
                assets[key].pop("off", None)
            data = json.dumps(assets, indent=1).encode("utf-8")
        dst.writestr(item, data)
tmp.replace(xpi)
print("nex_ubo_defaults: enabled " + ", ".join(ENABLE))
