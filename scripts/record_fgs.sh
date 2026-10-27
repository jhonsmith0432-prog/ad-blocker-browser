#!/usr/bin/env bash
# Records the real app (universal APK built from the Play bundle) using its foreground services:
#  1) file download  -> FOREGROUND_SERVICE_DATA_SYNC (download notification)
#  2) media playback -> FOREGROUND_SERVICE_MEDIA_PLAYBACK (media notification after leaving the app)
# Output: out/*.mp4 (+ screenshots and a log)
set -x
PKG=com.nexcraft.browser
mkdir -p out
LOG=out/log.txt
exec > >(tee -a "$LOG") 2>&1

adb wait-for-device
adb logcat -G 16M || true
adb logcat -c || true
adb shell settings put global window_animation_scale 1
adb install-multiple -r -g --abi arm64-v8a splits/*.apk || adb install-multiple -r --abi arm64-v8a splits/*.apk || exit 1
adb shell getprop ro.product.cpu.abilist
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb shell dumpsys deviceidle whitelist +$PKG || true

tap_text() {  # tap the first element whose text/desc matches $1 (regex); returns 1 if not found
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml ui.xml >/dev/null 2>&1
  local b
  b=$(python3 - "$1" <<'EOF'
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
EOF
)
  [ -z "$b" ] && return 1
  adb shell input tap $b
}

dismiss_first_run() {  # clear onboarding / dialogs if any show up
  for i in 1 2 3 4 5; do
    tap_text '^(Not now|Skip|Maybe later|Close|Got it|Continue|Start browsing|No thanks|Allow)$' || break
    sleep 2
  done
}

open_url() { adb shell am start -a android.intent.action.VIEW -d "$1" -p $PKG >/dev/null; }

# warm-up launch so first-run screens don't land in the videos
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1
sleep 15
dismiss_first_run
adb shell screencap -p /sdcard/s0.png && adb pull /sdcard/s0.png out/00_launch.png

########## 1) download ##########
adb shell screenrecord --time-limit 40 /sdcard/fgs_download.mp4 &
REC=$!
sleep 2
open_url "https://speed.cloudflare.com/__down?bytes=300000000"
sleep 6
dismiss_first_run
tap_text '^Download$' || tap_text 'Download'
sleep 6
adb shell cmd statusbar expand-notifications
sleep 12
adb shell screencap -p /sdcard/s1.png && adb pull /sdcard/s1.png out/01_download_notification.png
adb shell cmd statusbar collapse
sleep 4
wait $REC
adb pull /sdcard/fgs_download.mp4 out/fgs_download.mp4
adb shell dumpsys activity services $PKG | grep -iE 'ServiceRecord|foregroundServiceType|isForeground' > out/services_download.txt || true

########## 2) media playback ##########
adb shell screenrecord --time-limit 45 /sdcard/fgs_media.mp4 &
REC=$!
sleep 2
open_url "https://archive.org/download/BigBuckBunny_124/Content/big_buck_bunny_720p_surround.mp4"
sleep 8
dismiss_first_run
# media document: tap the video to start it
adb shell screencap -p /sdcard/m0.png && adb pull /sdcard/m0.png out/02a_media_before_tap.png
tap_text '^(Play|play)$' || adb shell input tap 540 1100
sleep 8
adb shell screencap -p /sdcard/m1.png && adb pull /sdcard/m1.png out/02b_media_playing.png
adb shell input keyevent KEYCODE_HOME
sleep 4
adb shell cmd statusbar expand-notifications
sleep 12
adb shell screencap -p /sdcard/s2.png && adb pull /sdcard/s2.png out/02_media_notification.png
adb shell cmd statusbar collapse
wait $REC
adb pull /sdcard/fgs_media.mp4 out/fgs_media.mp4
adb shell dumpsys activity services $PKG | grep -iE 'ServiceRecord|foregroundServiceType|isForeground' > out/services_media.txt || true


########## 3) private tabs notification (special use: PrivateNotificationService) ##########
adb shell screenrecord --time-limit 40 /sdcard/fgs_private.mp4 &
REC=$!
sleep 2
adb shell am start -n $PKG/org.mozilla.fenix.HomeActivity --ez private_browsing_mode true >/dev/null
sleep 6
dismiss_first_run
adb shell am start -n $PKG/org.mozilla.fenix.HomeActivity --ez private_browsing_mode true -a android.intent.action.VIEW -d "https://nexcraftgames.com/browser/privacy" >/dev/null
sleep 10
adb shell screencap -p /sdcard/p0.png && adb pull /sdcard/p0.png out/03a_private_tab.png
adb shell input keyevent KEYCODE_HOME
sleep 4
adb shell cmd statusbar expand-notifications
sleep 12
adb shell screencap -p /sdcard/p1.png && adb pull /sdcard/p1.png out/03_private_notification.png
adb shell cmd statusbar collapse
wait $REC
adb pull /sdcard/fgs_private.mp4 out/fgs_private.mp4
adb shell dumpsys activity services $PKG | grep -iE 'ServiceRecord|isForeground' > out/services_private.txt || true

adb logcat -b crash -d > out/logcat_crash.txt 2>&1 || true
adb logcat -d -v time > out/logcat_full.txt 2>&1 || true
grep -nE "FATAL|AndroidRuntime|Exception|GeckoView|MOZ_CRASH|signal 11|SIGSEGV" out/logcat_full.txt | head -80 > out/logcat_errors.txt || true
ls -l out
exit 0
