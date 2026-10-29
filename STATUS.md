# Status

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
