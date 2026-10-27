#!/usr/bin/env bash
# Runs the real app: first-run screens, ad blocking, cookie banners, Premium screen. Screenshots go to out/.
set -x
PKG=com.nexcraft.browser
mkdir -p out
exec > >(tee -a out/app.txt) 2>&1
adb wait-for-device
adb root || true
sleep 3
adb wait-for-device
adb logcat -G 16M || true
adb logcat -c || true
adb install-multiple -r -g --abi arm64-v8a splits/*.apk || exit 1
adb shell settings put global hide_error_dialogs 1 || true

shot() { adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png "out/$1.png" >/dev/null; }
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml ui.xml >/dev/null 2>&1
  local b
  b=$(python3 - "$1" <<'PY'
import re,sys
pat=re.compile(sys.argv[1],re.I)
x=open('ui.xml',encoding='utf-8',errors='ignore').read()
for m in re.finditer(r'<node [^>]*>',x):
    n=m.group(0)
    t=re.search(r' text="([^"]*)"',n); d=re.search(r'content-desc="([^"]*)"',n)
    if (t and pat.search(t.group(1))) or (d and pat.search(d.group(1))):
        b=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',n)
        if b:
            a=list(map(int,b.groups())); print((a[0]+a[2])//2,(a[1]+a[3])//2); break
PY
)
  [ -z "$b" ] && return 1
  adb shell input tap $b
}
screen_text() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "out/ui_$1.xml" >/dev/null 2>&1; grep -o 'text="[^"]\+"' "out/ui_$1.xml" | head -40 | tr '\n' ' '; echo; }
wait_anr() { for i in 1 2 3; do tap_text '^(Wait)$' || break; sleep 3; done; }
open_url() { adb shell am start -a android.intent.action.VIEW -d "$1" -p $PKG >/dev/null; }

adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1
sleep 60; wait_anr
shot 01_first_launch; echo "FIRST LAUNCH:"; screen_text 01
tap_text '^Next$'; sleep 3; shot 02_welcome2; screen_text 02
tap_text '^Next$'; sleep 3; shot 03_welcome3; screen_text 03
tap_text '^Start browsing$'; sleep 15; wait_anr
shot 04_home; echo "HOME:"; screen_text 04
tap_text '^(Not now|Close|Got it|Maybe later|No thanks)$' && sleep 3 && shot 04b_home

open_url "https://d3ward.github.io/toolz/adblock.html"
sleep 50; wait_anr; shot 05_adblock_test; screen_text 05
open_url "https://www.bbc.com/news"
sleep 45; wait_anr; shot 06_bbc_cookie; screen_text 06
open_url "https://www.theguardian.com/international"
sleep 45; wait_anr; shot 07_guardian_cookie; screen_text 07

adb shell am start -n $PKG/org.mozilla.fenix.nex.PremiumActivity || true
sleep 12; shot 08_premium; echo "PREMIUM:"; screen_text 08
adb shell input swipe 540 1800 540 600 400; sleep 2; shot 08b_premium_scrolled; screen_text 08b
adb shell input keyevent KEYCODE_BACK; sleep 3

echo "== process alive? =="; adb shell pidof $PKG || echo "NOT RUNNING"
echo "== crashes =="; adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: |Process: $PKG" -A6 | head -80 > out/crashes.txt; cat out/crashes.txt
echo "== uBlock / extension errors =="; adb logcat -d | grep -iE "ublock|content blocker|nexvpn|WebExtension.*(fail|error)" | head -40
adb logcat -d > out/logcat.txt
