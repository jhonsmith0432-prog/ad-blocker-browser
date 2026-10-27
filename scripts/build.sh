#!/usr/bin/env bash
# Bootstrap toolchain (artifact mode) and build the Play Store bundle (AAB).
# Usage: scripts/build.sh [release|debug]   (default: release)
set -euo pipefail
cd "$(dirname "$0")/../src"
VARIANT="${1:-release}"

./mach --no-interactive bootstrap \
  --application-choice "GeckoView/Firefox for Android Artifact Mode"

./mach build

if [ "$VARIANT" = "debug" ]; then
  ./mach gradle fenix:assembleDebug
else
  ./mach gradle fenix:bundleRelease
fi

echo "Outputs:"
find mobile/android/fenix/app/build/outputs objdir-frontend/gradle/build/mobile/android/fenix 2>/dev/null \
  \( -name '*.aab' -o -name '*.apk' \) -print || true
