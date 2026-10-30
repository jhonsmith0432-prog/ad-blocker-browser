# Status

## 30 Oct 2026: VPN one tap + live status

- **VPN button on the home screen** (first button in the header): one tap turns the Browser VPN on or off,
  the button shows Off / Connecting (spinner) / VPN on / VPN error. Non-Premium users get the paywall.
- **VPN row at the top of the main menu**: same one-tap toggle while browsing, shows "Connected: New York · IP".
- **Live state**: `NexVpn.state` / `wanted` / `ip` are Compose state, so the home button, menu row and the
  Privacy Shield card all update the moment you tap (no need to leave and come back).
- **Real connection check**: after every change the VPN extension fetches Cloudflare's /cdn-cgi/trace through
  the proxy and compares the IP with the server's address (`dns` permission). Match = Connected (shows the IP
  sites see), no answer in 15 s or wrong IP = "Couldn't connect" with Try again.
- Premium pill on home is only shown before buying. "Not now" is gone (X close since the 29 Oct UI rework;
  build 34 came from `main`, which did not have that rework yet).
- 9 new strings in all 14 languages.


## 29 Oct 2026 (later): v2 polish from the "Nex Browser UI" design canvas (row "v2 polish")

- **Tabs**: Tabs / Private segmented switch replaces the icon row (Tabs first). Synced tabs page removed.
  Each grid tab shows "N blocked" (trackers blocked in that tab).
- **Menu**: Privacy Shield (with this page's blocked count) and Premium at the top; Mozilla account row
  only shows if an account exists (it never does, sign-in is hidden).
- **Home**: blocked-count card under the header, opens Privacy Shield.
- **Tab groups** (like Brave): on by default. Several tabs stack into one card under "Groups", a strip at
  the bottom switches between tabs of the current group, drag a tab onto another to group them, and the
  main menu has "Add to tab group". Firefox ships this code; Mozilla only enables it by remote experiment.
- **Premium**: violet plan selection and buy button, as in the design.
- **Check the real build**: run the new "Screenshot the UI" workflow with the Build run id; it saves
  home, tabs, tab options, private tabs, menu, Privacy Shield and Premium screenshots as `ui-screens`.

## 29 Oct 2026: UI rework (needs a CI build)

- **Premium has its own button now.** Home screen header: a "Get Premium" pill (opens the paywall) and a
  shield button (opens Privacy Shield). Settings keeps one entry, "Privacy Shield & Premium".
- **Privacy Shield** (`NexHubActivity`): blocked count, then every control in sections (Protection: VPN,
  Always incognito, App lock; Look and feel; Premium and account; support code folded away). Premium
  controls show a gold "Premium" chip and open the paywall instead of failing silently.
- **Paywall** (`PremiumActivity`) is only a paywall now: close (X), 4 benefits, plans, and the buy button
  pinned to the bottom so it is always visible. Premium users see "Premium is active", Manage subscription
  (Google Play) and Open Privacy Shield.
- Screens respect the status and navigation bars (Android 15 edge-to-edge).
- Shared pieces live in `NexCards.kt`; home buttons in `NexHomeEntry.kt` (inserted by `nex_fixups.py`).
- 25 new strings in all 14 languages (`NexStrings.kt`).
- `.gitignore`: `src/` changed to `/src/` so new overlay files are not ignored.

# Status — overnight work (27 Oct 2026)

## Done tonight

- **Private tabs lock** forced on (Firefox hid it behind Mozilla's remote experiments, which we disabled) — patch 0006.
- **Always incognito** (Premium): toggle on the Premium screen; every session starts private and links from
  other apps open in private tabs — patch 0006 + `overlay/.../nex/`.
- **Settings → "Premium & privacy stats"** entry opens the Premium/stats screen — patch 0006.
- **DuckDuckGo default search** set once on first run (user can change it; we never override again).
- **Trademark clean-up**: 22 Firefox/Kit (fox mascot) drawables replaced with our shield
  (`branding/replace_logos.py`), and "Firefox" in UI strings replaced with our name (all languages) in prebuild.
- **New source files moved to `overlay/`** (copied in by prebuild) so patches only touch upstream files.
- **Artifact build on the GitHub repo**: Mozilla's own code (`python/mozbuild/mozbuild/artifacts.py`) supports the
  GitHub "firefox" repo directly with git hashes — git-cinnabar is not needed. Clone depth raised to 50 so the
  build can find a commit Mozilla has prebuilt engine files for.
- **Store assets**: feature graphic 1024x500 and 4 screenshot frames (1080x1920) in `branding/store/`.
- **Privacy policy** (`docs/PRIVACY_POLICY.md`) and **Data safety answers** (`docs/DATA_SAFETY.md`).
- **ASO**: new short description with "incognito" (74/80), Premium bullets updated (no YouTube / tab lock).
- **Design canvas**: paywall perks updated.
- All 6 patches apply cleanly, in order, on Firefox 158.0.1.

## Stuck — need you

1. **First real build.** Nothing has been compiled yet. This workspace can't reach Mozilla/Google download
   servers, and I can't push to GitHub (no token / folder access). Create a public GitHub repo, upload the
   project, run the "Build" workflow, send me the log. Most likely first errors: Kotlin compile errors in
   `overlay/.../nex/*.kt` (written without a compiler) or the Play Billing dependency not resolving.
2. **Google Play screenshots must show the real app.** Frames are ready; after the first build, take 4 phone
   screenshots (`adb exec-out screencap -p > shot1.png`) and run
   `python3 branding/store_assets.py shot1.png shot2.png shot3.png shot4.png`.
3. **Onboarding is still Firefox's** (Mozilla Terms of Use + Mozilla data consent pages). Needs our own
   onboarding matching the canvas; it's driven by Mozilla's experiment config, so it's a bigger patch.
   Decide: build our own onboarding now, or ship v1 with onboarding skipped.
4. **Mozilla services still on**: Mozilla account / Sync, push service, Pocket-style content, crash
   reporter prompt. For a "no data" promise I recommend turning off account/Sync and push in v1. Your call.
5. **What Premium sells.** uBlock already blocks YouTube ads free and Firefox already locks private tabs free.
   Premium now = Always incognito (built) + stronger fingerprint protection + custom filters (not built yet).
   Confirm the list before I build the last two.
6. **32-bit phones**: build is arm64 only. Older/cheap Android phones (common in Pakistan) need a second
   (armeabi-v7a) build bundled into the same AAB. Needs a working first build first.
7. **Placeholders to fill**: developer/company name, support email, privacy policy URL (host
   `docs/PRIVACY_POLICY.md` e.g. on GitHub Pages), Play products (`premium` with base plans `monthly`/`yearly`
   + 7-day trial offers, `premium_lifetime`), release keystore secrets.
8. **AppTweak** was logged out — still no search volume numbers for the keyword sheet.
9. **Canvas note**: when I updated the paywall artboard, the canvas had a version saved from the page about
   30 minutes earlier. If you edited the Premium screen in the canvas around then, check it's still there.
10. I tried to download a Kotlin linter to sanity-check the new code; that download was blocked here, so the
    first CI build is the check.

## Round: Premium features (29 Oct)
Added behind the paywall (PREMIUM_FREE still true for testers):
- Dark mode for every site: bundled Dark Reader 4.9.134 (MIT, downloaded in prebuild, help/uninstall tabs patched out).
- Data saver: built-in `nexplus` extension blocks images, web fonts and media; KB saved shown in Privacy Shield.
- Hide anything you tap: menu item "Hide something on this page" opens a picker; rules saved per site, clearable.
- Private Vault: app-private folder behind fingerprint/PIN; optional auto-move of finished downloads; add/share/delete.
- Strong anti-fingerprinting: Gecko fingerprinting protection with +AllTargets.
- Auto-wipe: Off / when I leave / after 5 / after 30 minutes (tabs, history, cookies, cache).
- More VPN locations: code already lists servers from vpn.json; needs a Europe server rented by Jhon.
Strings in all 14 languages. kotlinc syntax check and ffsrc dry-run of patches + nex_fixups pass.
