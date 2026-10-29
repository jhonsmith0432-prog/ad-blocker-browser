#!/usr/bin/env python3
"""Source fix-ups applied after the patches (scripts/prebuild.sh step 1b).

Each edit asserts that its anchor exists, so a Firefox bump that moves code fails the build
loudly instead of silently shipping the upstream behaviour.

  - Play policy: no "produced by Mozilla", no Mozilla account sign-in, no Pocket / sponsored
    content, no Firefox Suggest, crash reports never sent, telemetry/experiments hard off.
  - 64-bit ARM only (Gecko is only built for arm64-v8a; other ABIs would crash on launch).
  - 14 languages: prune the rest from the app and its libraries.
  - Register our onboarding activity and the built-in Browser VPN extension.
"""
import pathlib
import re
import shutil
import sys

SRC = pathlib.Path(sys.argv[1])
FENIX = SRC / "mobile/android/fenix/app"
MAIN = FENIX / "src/main"
APP_NAME = sys.argv[2] if len(sys.argv) > 2 else "Ad Blocker"

# Language folder suffixes we keep (Android resource qualifiers).
KEEP_LOCALES = {"es", "es-rES", "es-rMX", "es-rAR", "es-rCL", "pt-rBR", "fr", "de", "it", "ru", "tr",
                "ar", "hi-rIN", "in", "ja", "ko", "zh-rCN"}
LOCALE_FILTERS = ["en", "es", "pt", "fr", "de", "it", "ru", "tr", "ar", "hi", "in", "id", "ja", "ko", "zh"]


def edit(path, old, new, count=1, regex=False):
    p = pathlib.Path(path)
    s = p.read_text(encoding="utf-8")
    if regex:
        s2, n = re.subn(old, new, s, count=count, flags=re.S)
    else:
        n = s.count(old)
        s2 = s.replace(old, new, count)
    if n == 0:
        sys.exit(f"nex_fixups: anchor not found in {p}:\n{old[:200]}")
    p.write_text(s2, encoding="utf-8")


settings = MAIN / "java/org/mozilla/fenix/utils/Settings.kt"

# --- No Pocket stories / sponsored stories on the home screen --------------------------
edit(settings,
     r"(var showPocketRecommendationsFeature by.*?defaultValue = )\{[^}]*\}",
     r"\1{ false }", regex=True)
edit(settings,
     r"(val showPocketSponsoredStories by.*?defaultValue = )\{[^}]*\}",
     r"\1{ false }", regex=True)

# --- No Firefox Suggest (online suggestions + sponsored suggestions) -------------------
edit(settings, r"(var enableFxSuggest by.*?defaultValue = )\{[^}]*\}", r"\1{ false }", regex=True)
edit(settings, r"(var showSponsoredSuggestions by.*?defaultValue = )\{[^}]*\}", r"\1{ false }", regex=True)
edit(settings, r"(var showNonSponsoredSuggestions by.*?defaultValue = )\{[^}]*\}", r"\1{ false }", regex=True)

# --- Crash reports: never sent; telemetry/experiments/daily ping hard off --------------
edit(settings, "default = CrashReportOption.Ask.toString(),", "default = CrashReportOption.Never.toString(),")
edit(settings, r"(var isDailyUsagePingEnabled by\s+booleanPreference\(\s+appContext.getPreferenceKey\(R.string.pref_key_daily_usage_ping\),\s+default = )isTelemetryEnabled",
     r"\1false", regex=True)
edit(settings, r"(var isExperimentationEnabled by\s+booleanPreference\(\s+appContext.getPreferenceKey\(R.string.pref_key_experimentation_v2\),\s+default = )isTelemetryEnabled",
     r"\1false", regex=True)

# --- Mozilla account / sync: hide sign-in and account section in Settings --------------
prefs_xml = MAIN / "res/xml/preferences.xml"
edit(prefs_xml, 'android:key="@string/pref_key_sign_in"',
     'android:key="@string/pref_key_sign_in"\n        app:isPreferenceVisible="false"')
# pref_key_account_category is already isPreferenceVisible=false upstream.
# Settings code toggles these at runtime; force them hidden after it runs.
sf = MAIN / "java/org/mozilla/fenix/settings/SettingsFragment.kt"
s = sf.read_text(encoding="utf-8")
hook = "override fun onResume() {\n        super.onResume()\n"
if hook not in s:
    sys.exit("nex_fixups: SettingsFragment.onResume anchor missing")
s = s.replace(hook, hook + (
    "        // Nexcraft: no Mozilla account / sync entry points.\n"
    "        findPreference<androidx.preference.Preference>(getString(R.string.pref_key_sign_in))?.isVisible = false\n"
    "        findPreference<androidx.preference.Preference>(getString(R.string.pref_key_account_category))?.isVisible = false\n"
), 1)
sf.write_text(s, encoding="utf-8")

# --- About screen: never "produced by Mozilla" ------------------------------------------
ABOUT = {
    "values": "%1$s is an independent browser by Nexcraft, built on Mozilla’s open-source engine.",
    "values-es": "%1$s es un navegador independiente de Nexcraft, creado con el motor de código abierto de Mozilla.",
    "values-es-rES": "%1$s es un navegador independiente de Nexcraft, creado con el motor de código abierto de Mozilla.",
    "values-pt-rBR": "%1$s é um navegador independente da Nexcraft, feito com o motor de código aberto da Mozilla.",
    "values-fr": "%1$s est un navigateur indépendant de Nexcraft, basé sur le moteur open source de Mozilla.",
    "values-de": "%1$s ist ein unabhängiger Browser von Nexcraft, gebaut auf Mozillas Open-Source-Engine.",
    "values-it": "%1$s è un browser indipendente di Nexcraft, basato sul motore open source di Mozilla.",
    "values-ru": "%1$s — независимый браузер от Nexcraft на открытом движке Mozilla.",
    "values-tr": "%1$s, Nexcraft'ın Mozilla'nın açık kaynaklı motoru üzerine kurulu bağımsız tarayıcısıdır.",
    "values-ar": "‏%1$s متصفح مستقل من Nexcraft، مبني على محرك Mozilla مفتوح المصدر.",
    "values-hi-rIN": "%1$s, Nexcraft का एक स्वतंत्र ब्राउज़र है, जो Mozilla के ओपन-सोर्स इंजन पर बना है।",
    "values-in": "%1$s adalah browser independen dari Nexcraft, dibangun di atas mesin open source Mozilla.",
    "values-ja": "%1$s は Nexcraft の独立系ブラウザで、Mozilla のオープンソースエンジンで動作します。",
    "values-ko": "%1$s은(는) Mozilla의 오픈 소스 엔진으로 만든 Nexcraft의 독립 브라우저입니다.",
    "values-zh-rCN": "%1$s 是 Nexcraft 推出的独立浏览器，基于 Mozilla 开源引擎打造。",
}
for folder, text in ABOUT.items():
    f = MAIN / "res" / folder / "strings.xml"
    if not f.exists():
        continue
    s = f.read_text(encoding="utf-8")
    s2, n = re.subn(r'(<string name="about_content"[^>]*>).*?(</string>)',
                    lambda m: m.group(1) + text.replace("'", "\\'") + m.group(2), s, count=1, flags=re.S)
    if folder == "values" and n == 0:
        sys.exit("nex_fixups: about_content missing")
    f.write_text(s2, encoding="utf-8")

# --- 14 languages: drop the other app translations -------------------------------------
loc_re = re.compile(r"^values-([a-z]{2,3}(?:-r[A-Z]{2})?)$")
removed = 0
for d in (MAIN / "res").iterdir():
    m = loc_re.match(d.name)
    if d.is_dir() and m and m.group(1) not in KEEP_LOCALES:
        shutil.rmtree(d)
        removed += 1
print(f"nex_fixups: removed {removed} locale folders")

gradle = FENIX / "build.gradle"
edit(gradle, "    androidResources {\n",
     "    androidResources {\n        localeFilters += " +
     "[" + ", ".join(f'"{l}"' for l in LOCALE_FILTERS) + "]\n")

# --- 64-bit ARM only ---------------------------------------------------------------------
edit(gradle, "    defaultConfig {\n", "    defaultConfig {\n        ndk { abiFilters \"arm64-v8a\" }\n")
edit(gradle, 'include "armeabi-v7a", "arm64-v8a", "x86_64"', 'include "arm64-v8a"')

# --- Our activities ----------------------------------------------------------------------
manifest = MAIN / "AndroidManifest.xml"
edit(manifest, '            android:name=".nex.PremiumActivity"',
     '            android:name=".nex.PremiumActivity"')  # sanity: patch 0005 applied
edit(manifest, "    </application>",
     "        <activity\n"
     "            android:name=\".nex.NexOnboardingActivity\"\n"
     "            android:exported=\"false\"\n"
     "            android:theme=\"@android:style/Theme.Material.NoActionBar\" />\n"
     "        <activity\n"
     "            android:name=\".nex.NexHubActivity\"\n"
     "            android:exported=\"false\"\n"
     "            android:theme=\"@android:style/Theme.Material.NoActionBar\" />\n"
     "        <activity\n"
     "            android:name=\".nex.NexLockActivity\"\n"
     "            android:exported=\"false\"\n"
     "            android:launchMode=\"singleTop\"\n"
     "            android:theme=\"@style/Theme.AppCompat.NoActionBar\" />\n"
     "    </application>")

# --- Only our welcome screens: skip Firefox's onboarding and its Terms of Use sheet -------
edit(MAIN / "java/org/mozilla/fenix/HomeActivity.kt",
     "hasUserBeenOnboarded = fenixOnboarding.userHasBeenOnboarded(),",
     "hasUserBeenOnboarded = true,")
edit(settings, "default = { FxNimbus.features.termsOfUsePrompt.value().enabled },", "default = { false },")

# --- No cloud page summaries (sends page text to Mozilla) and no "shake to summarize" tip -
edit(settings, "default = { FxNimbus.features.shakeToSummarize.value().enabled },", "default = { false },")

# --- uBlock Origin: built-in extensions must be an unpacked folder, not an .xpi ------------
# (prebuild unpacks the XPI to assets/extensions/ublock/ via nex_ubo_defaults.py)
edit(MAIN / "java/org/mozilla/fenix/FenixApplication.kt",
     'url = "resource://android/assets/extensions/ublock_origin.xpi",',
     'url = "resource://android/assets/extensions/ublock/",')

# --- Home background: our navy (dark) and a cool mint-white (light), not Firefox's pink ---
for colors_xml, start, end in [("values/colors.xml", "#FFE9F7F1", "#FFF5F8FC"),
                               ("values-night/colors.xml", "#FF0B0F1E", "#FF19213F")]:
    f = MAIN / "res" / colors_xml
    t = f.read_text(encoding="utf-8")
    t, n1 = re.subn(r'(<color name="homepage_edge_to_edge_gradient_start">)[^<]+', rf"\g<1>{start}", t)
    t, n2 = re.subn(r'(<color name="homepage_edge_to_edge_gradient_end">)[^<]+', rf"\g<1>{end}", t)
    if n1 != 1 or n2 != 1:
        sys.exit(f"nex_fixups: homepage gradient colours missing in {colors_xml}")
    f.write_text(t, encoding="utf-8")

# --- Built-in Browser VPN extension (Premium) ---------------------------------------------
app = MAIN / "java/org/mozilla/fenix/FenixApplication.kt"
edit(app, "            installBundledContentBlocker()\n",
     "            installBundledContentBlocker()\n"
     "            org.mozilla.fenix.nex.NexVpn.install(this, components.core.engine)\n")

# --- Nexcraft look: mint + navy instead of Firefox violet (all Compose UI) ----------------
colors = SRC / "mobile/android/android-components/components/compose/base/src/main/java/mozilla/components/compose/base/theme/AcornColors.kt"
NAVY_0, NAVY_1, NAVY_2, NAVY_3 = "Color(0xFF0B0F1E)", "Color(0xFF121830)", "Color(0xFF19213F)", "Color(0xFF222B50)"
# Accent colours come from NexAccent (Premium accent picker; Mint by default).
THEMES = {
    "fun acornDarkColorScheme(): ColorScheme": {
        "primary": "NexAccent.darkPrimary", "onPrimary": "NexAccent.darkOnPrimary",
        "primaryContainer": "NexAccent.darkContainer", "onPrimaryContainer": "NexAccent.darkOnContainer",
        "inversePrimary": "NexAccent.lightPrimary", "tertiary": "NexAccent.darkPrimary",
        "secondaryContainer": NAVY_3, "background": NAVY_1, "surface": NAVY_1,
        "surfaceVariant": NAVY_3, "surfaceContainer": NAVY_1, "surfaceContainerHigh": NAVY_2,
        "surfaceContainerHighest": NAVY_3, "surfaceContainerLow": NAVY_0, "surfaceContainerLowest": NAVY_0,
        "surfaceBright": NAVY_3, "surfaceDim": NAVY_0,
    },
    "fun acornLightColorScheme(): ColorScheme": {
        "primary": "NexAccent.lightPrimary", "onPrimary": "Color(0xFFFFFFFF)",
        "primaryContainer": "NexAccent.lightContainer", "onPrimaryContainer": "NexAccent.lightOnContainer",
        "inversePrimary": "NexAccent.darkPrimary", "tertiary": "NexAccent.lightPrimary",
        "secondaryContainer": "NexAccent.lightSecondaryContainer",
    },
    "fun acornPrivateColorScheme(): ColorScheme": {
        "primary": "Color(0xFF9C86FF)", "primaryContainer": "Color(0xFF3B2C8C)",
        "secondaryContainer": "Color(0xFF3B2C8C)", "tertiary": "NexAccent.darkPrimary",
    },
}
cs = colors.read_text(encoding="utf-8")
for header, values in THEMES.items():
    start = cs.find(header)
    if start < 0:
        sys.exit(f"nex_fixups: {header} missing")
    end = cs.find("\n    )\n", start)
    body = cs[start:end]
    for key, val in values.items():
        body, n = re.subn(rf"(\n        {key} = )[^,\n]+,", rf"\g<1>{val},", body, count=1)
        if n == 0:
            sys.exit(f"nex_fixups: {key} missing in {header}")
    cs = cs[:start] + body + cs[end:]
colors.write_text(cs, encoding="utf-8")

# Pure-black (OLED) theme: our accent instead of Firefox violet, and offered to everyone.
edit(colors, "            primaryContainer = NovaColors.Violet70,\n",
     "            primaryContainer = NexAccent.darkContainer,\n")
edit(colors, "            secondaryContainer = NovaColors.VioletDesaturated80,\n",
     "            secondaryContainer = Color(0xFF1C1C1C),\n")
edit(settings, "default = { FxNimbus.features.oledTheme.value().enabled },", "default = { true },")

# --- Premium app icons: the picker opens the paywall for free users ------------------------
edit(MAIN / "java/org/mozilla/fenix/iconpicker/ui/AppIconPreference.kt",
     "                CustomizationSettings.appIconSelectionTapped.record()\n",
     "                if (!org.mozilla.fenix.nex.NexPrefs(context).isPremium) {\n"
     "                    context.startActivity(\n"
     "                        android.content.Intent(context, org.mozilla.fenix.nex.PremiumActivity::class.java),\n"
     "                    )\n"
     "                    return@SelectAppIcon\n"
     "                }\n"
     "                CustomizationSettings.appIconSelectionTapped.record()\n")

# --- Home screen: Premium pill + Privacy Shield button in the header ----------------------
home_ui = MAIN / "java/org/mozilla/fenix/home/ui"
edit(home_ui / "HomepageHeader.kt",
     "            if (showStoriesButton) {\n                StoriesButton(onClick = onStoriesTapped)\n            }\n",
     "            org.mozilla.fenix.nex.NexHomeEntries()\n\n"
     "            if (showStoriesButton) {\n                StoriesButton(onClick = onStoriesTapped)\n            }\n")
edit(home_ui / "ExperimentalHomepageHeader.kt",
     "            PrivateModeButton(onPrivateModeTapped)\n",
     "            PrivateModeButton(onPrivateModeTapped)\n\n            org.mozilla.fenix.nex.NexHomeEntries()\n")

print("nex_fixups: done")
