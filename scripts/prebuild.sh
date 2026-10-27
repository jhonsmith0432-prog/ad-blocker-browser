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
find "$FENIX/src/main/res" -name strings.xml -path '*values*' \
  -exec sed -i "s/\bFirefox\b/${APP_NAME}/g" {} +

# 4. Bundled uBlock Origin (installed at startup by patch 0004).
mkdir -p "$FENIX/src/main/assets/extensions"
# Built-in Browser VPN extension (Premium).
mkdir -p "$FENIX/src/main/assets/extensions/nexvpn"
cp "$ROOT/scripts/nexvpn-manifest.json" "$FENIX/src/main/assets/extensions/nexvpn/manifest.json"
cp "$ROOT/scripts/nexvpn-background.js" "$FENIX/src/main/assets/extensions/nexvpn/background.js"
XPI="$FENIX/src/main/assets/extensions/ublock_origin.xpi"
curl -fL --retry 3 -o "$XPI" \
  "https://github.com/gorhill/uBlock/releases/download/${UBO_VERSION}/uBlock0_${UBO_VERSION}.firefox.signed.xpi"
unzip -tq "$XPI" >/dev/null || { echo "Downloaded uBlock Origin XPI is not a valid archive." >&2; exit 1; }

# 5. Build config.
cp "$ROOT/scripts/mozconfig" "$SRC/mozconfig"
sed -i "s/@TARGET@/${TARGET}/" "$SRC/mozconfig"

echo "Prebuild done."
