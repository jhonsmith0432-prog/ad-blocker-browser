#!/usr/bin/env bash
# Screenshots of the real app (built from the Play bundle) on an emulator: home, tabs, tab options,
# private tabs, main menu, Privacy Shield, Premium. Output: out/*.png plus UI dumps for each step.
set -x
PKG=com.nexcraft.browser
mkdir -p out
exec > >(tee -a out/log.txt) 2>&1

adb wait-for-device
adb logcat -c || true
adb install-multiple -r -g --abi arm64-v8a splits/*.apk || adb install-multiple -r --abi arm64-v8a splits/*.apk || exit 1
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb shell settings put global hide_error_dialogs 1 || true

tap_text() {  # tap the first element whose text/desc matches $1 (regex); returns 1 if not found
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
shot() { sleep 3; adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "out/$1.xml" >/dev/null 2>&1
  adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png "out/$1.png" >/dev/null; }
back() { adb shell input keyevent KEYCODE_BACK; sleep 2; }
first_run() { for i in 1 2 3 4 5; do tap_text '^(Skip|Not now|Start browsing|Got it|No thanks|Continue|Wait)$' || break; sleep 2; done; }

adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1
sleep 45
first_run
shot 01_home

adb shell am start -a android.intent.action.VIEW -d "https://en.wikipedia.org/wiki/Bicycle" -p $PKG >/dev/null; sleep 15
adb shell am start -a android.intent.action.VIEW -d "https://www.theguardian.com/international" -p $PKG >/dev/null; sleep 15
first_run
shot 02_page

tap_text '(open tabs|Tabs open|tabs?, |^Tabs$|Tab counter)' && shot 03_tabs
tap_text '^(Tab options|More options|Open tabs menu|Menu)$' && shot 04_tab_options
back
tap_text '^Private' && shot 05_private_tabs
back

tap_text '^(Main menu|Menu|More options|Customize and control)' && shot 06_menu
tap_text '^Privacy Shield$' && shot 07_privacy_shield
back
tap_text '^(Get Premium|Premium)$' && shot 08_premium
back
tap_text '^(Get Premium|Premium)$' || { adb shell am start -a android.intent.action.MAIN -n $PKG/org.mozilla.fenix.HomeActivity; sleep 5; }
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1; sleep 5
tap_text '^(Get Premium|Premium)$' && shot 09_premium_from_home
back
adb logcat -d -b crash > out/logcat_crash.txt || true
ls -l out
