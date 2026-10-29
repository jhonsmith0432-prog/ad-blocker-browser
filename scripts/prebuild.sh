#!/usr/bin/env bash
# Apply our patches and assets on top of the pinned Firefox source in ./src.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$PWD"
source config.env
SRC="$ROOT/src"
FENIX="$SRC/mobile/android/fenix/app"

[ -d "$SRC/.git" ] || { echo "Run scripts/get-source.sh first." >&2; exit 1; }

# 0. Our own new source files (no upstream counterpart) are copied in as-is.
cp -r "$ROOT/overlay/." "$SRC/"

# 1. Patches (fail loudly if any no longer applies after a Firefox bump).
for p in "$ROOT"/patches/*.patch; do
  echo "Applying $(basename "$p")"
  git -C "$SRC" apply --check "$p"
  git -C "$SRC" apply "$p"
done

# 1b. Source fix-ups (Play policy, arm64-only, 14 languages, Nexcraft theme, VPN, onboarding).
python3 "$ROOT/scripts/nex_fixups.py" "$SRC" "$APP_NAME"

# 1c. Built-in VPN server list (from the NEXVPN_BUILTIN secret; it holds the proxy login, so it never lives in git).
if [ -n "${NEXVPN_BUILTIN:-}" ]; then
  python3 - "$FENIX/src/main/java/org/mozilla/fenix/nex/NexVpn.kt" <<'PY'
import json, os, sys
p = sys.argv[1]
val = json.dumps(json.loads(os.environ["NEXVPN_BUILTIN"]), separators=(",", ":"))
kt = json.dumps(val).replace("$", "\\$")  # a valid Kotlin string literal
s = open(p, encoding="utf-8").read()
assert '"@NEXVPN_BUILTIN@"' in s, "NexVpn.kt placeholder missing"
open(p, "w", encoding="utf-8").write(s.replace('"@NEXVPN_BUILTIN@"', kt))
PY
fi

# 2. App name.
sed -i "s/@APP_NAME@/${APP_NAME}/" "$FENIX/src/release/res/values/static_strings.xml"

# 3. Branding assets (launcher icons, wordmarks). Firefox's logos are trademarks and
#    must not ship, so every file in branding/release/res replaces the upstream one.
if [ -d "$ROOT/branding/release/res" ]; then
  cp -r "$ROOT/branding/release/res/." "$FENIX/src/release/res/"
  # The in-app icon picker ships Firefox fox artwork; replace every alternate foreground with ours.
  for f in "$FENIX"/src/main/res/drawable/ic_launcher_foreground*.xml; do
    cp "$ROOT/branding/main/res/drawable/ic_launcher_foreground.xml" "$f"
  done
else
  echo "WARNING: branding/release/res missing - build would still carry Firefox logos." >&2
fi

# 3b. Trademark clean-up: Firefox/Kit artwork -> our shield; "Firefox" in UI text -> our name.
python3 "$ROOT/branding/replace_logos.py" "$FENIX/src/main/res"
# Premium app icons: our shield in every icon-picker design (replaces the fox artwork).
python3 "$ROOT/branding/icon_themes.py" "$FENIX/src/main"
find "$FENIX/src/main/res" -name strings.xml -path '*values*' \
  -exec sed -i "s/\bFirefox\b/${APP_NAME}/g" {} +

# 4. Bundled uBlock Origin (installed at startup by patch 0004).
mkdir -p "$FENIX/src/main/assets/extensions"
# Built-in Browser VPN extension (Premium).
mkdir -p "$FENIX/src/main/assets/extensions/nexvpn"
cp "$ROOT/scripts/nexvpn-manifest.json" "$FENIX/src/main/assets/extensions/nexvpn/manifest.json"
cp "$ROOT/scripts/nexvpn-background.js" "$FENIX/src/main/assets/extensions/nexvpn/background.js"
# Premium tools (Data saver + Hide anything you tap).
mkdir -p "$FENIX/src/main/assets/extensions/nexplus"
cp "$ROOT"/scripts/nexplus/* "$FENIX/src/main/assets/extensions/nexplus/"
# Dark mode for every site: Dark Reader (MIT), unpacked; its "open the help page on install" tab is removed.
DR="$FENIX/src/main/assets/extensions/darkreader"
rm -rf "$DR" && mkdir -p "$DR"
curl -fL --retry 3 -o "$ROOT/darkreader.xpi" \
  "https://github.com/darkreader/darkreader/releases/download/v${DARKREADER_VERSION}/darkreader-firefox.xpi"
unzip -q "$ROOT/darkreader.xpi" -d "$DR"
rm -f "$ROOT/darkreader.xpi"
cp "$ROOT/scripts/darkreader-LICENSE.txt" "$DR/LICENSE"
python3 - "$DR/background/index.js" <<'PY'
import sys
p = sys.argv[1]
s = open(p, encoding="utf-8").read()
for old, new in [("chrome.tabs.create({url: getHelpURL()});", "void 0;"),
                 ("chrome.runtime.setUninstallURL(UNINSTALL_URL);", "void 0;")]:
    assert old in s, "Dark Reader changed: " + old
    s = s.replace(old, new)
open(p, "w", encoding="utf-8").write(s)
PY
XPI="$FENIX/src/main/assets/extensions/ublock_origin.xpi"
curl -fL --retry 3 -o "$XPI" \
  "https://github.com/gorhill/uBlock/releases/download/${UBO_VERSION}/uBlock0_${UBO_VERSION}.firefox.signed.xpi"
unzip -tq "$XPI" >/dev/null || { echo "Downloaded uBlock Origin XPI is not a valid archive." >&2; exit 1; }
# Cookie-notice and app-banner lists on by default.
python3 "$ROOT/scripts/nex_ubo_defaults.py" "$XPI"

# 5. Build config.
cp "$ROOT/scripts/mozconfig" "$SRC/mozconfig"
sed -i "s/@TARGET@/${TARGET}/" "$SRC/mozconfig"

echo "Prebuild done."
