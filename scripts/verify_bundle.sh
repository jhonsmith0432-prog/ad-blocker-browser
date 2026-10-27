#!/usr/bin/env bash
# Static checks on the signed Play bundle. Writes out/bundle.txt and prepares out/../splits for the emulator.
set -u
mkdir -p out
R=out/bundle.txt
exec > >(tee -a "$R") 2>&1
AAB=$(find art -name 'fenix-release.aab' | head -1)
[ -z "$AAB" ] && { echo 'fenix-release.aab not found'; find art -name '*.aab'; exit 1; }
echo "AAB: $AAB"
ls -la art art/* 2>/dev/null | head -40
echo "== AAB size (bytes) =="; stat -c %s "$AAB"
curl -sSL -o bundletool.jar https://github.com/google/bundletool/releases/download/1.17.2/bundletool-all-1.17.2.jar
java -jar bundletool.jar dump manifest --bundle "$AAB" > out/manifest.xml
echo "== versionCode / versionName =="; grep -o 'android:version\(Code\|Name\)="[^"]*"' out/manifest.xml | head -2
echo "== permissions =="; grep -o 'uses-permission[^>]*android:name="[^"]*"' out/manifest.xml | sed 's/.*name="//;s/"$//' | sort -u
echo "== restricted permissions present? (should be empty) =="
grep -E 'REQUEST_INSTALL_PACKAGES|QUERY_ALL_PACKAGES|REQUEST_DELETE_PACKAGES|AD_ID' out/manifest.xml || echo "none"
echo "== our activities =="; grep -o 'android:name="org.mozilla.fenix.nex[^"]*"' out/manifest.xml | sort -u
echo "== native lib ABIs =="; unzip -l "$AAB" | grep -o 'base/lib/[^/]*/' | sort -u
echo "== signers (should be only the upload key) =="; unzip -l "$AAB" | grep -E 'META-INF/.*\.(SF|RSA|DSA|EC)$'
jarsigner -verify -verbose -certs "$AAB" 2>/dev/null | grep -o 'CN=[^,]*' | sort | uniq -c | head
echo "== uBlock Origin bundled defaults =="
unzip -p "$AAB" base/assets/extensions/ublock_origin.xpi > ubo.xpi 2>/dev/null && unzip -p ubo.xpi assets/assets.json | python3 -c "
import json,sys
d=json.load(sys.stdin)
for k in ['fanboy-cookiemonster','ublock-cookies-easylist','adguard-mobile-app-banners','easylist','easyprivacy']:
    print(k, 'ON' if not d.get(k,{}).get('off') else 'off')
" ; unzip -l ubo.xpi | grep -c META-INF || true
echo "== VPN extension present =="; unzip -l "$AAB" | grep 'extensions/nexvpn' || echo "MISSING"
echo "== app locales (values-*) in bundle resources =="
java -jar bundletool.jar dump resources --bundle "$AAB" --resource string/app_name 2>/dev/null | grep -o 'locale: "[^"]*"' | sort -u | tr '\n' ' '; echo
keytool -genkeypair -keystore rec.jks -storepass recpass -keypass recpass -alias rec -keyalg RSA -keysize 2048 -validity 3650 -dname "CN=rec" -noprompt >/dev/null 2>&1
java -jar bundletool.jar build-apks --bundle "$AAB" --output app.apks --ks rec.jks --ks-pass pass:recpass --ks-key-alias rec --key-pass pass:recpass
echo '{"supportedAbis":["arm64-v8a"],"supportedLocales":["en-US"],"screenDensity":420,"sdkVersion":34}' > spec.json
echo "== estimated Play download size for a typical arm64 phone (bytes) =="
java -jar bundletool.jar get-size total --apks app.apks --device-spec spec.json
java -jar bundletool.jar extract-apks --apks app.apks --output-dir splits --device-spec spec.json
ls -l splits
rm -rf art
