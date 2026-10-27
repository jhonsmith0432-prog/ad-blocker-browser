# Ad Blocker & Private Browser

A privacy browser for Android: ads and trackers blocked by default, nothing collected.
Built on Firefox for Android (GeckoView + Fenix) as a patch set, the same way
privacy forks like IronFox are maintained.

## How it's built

1. `scripts/get-source.sh` – fetches the pinned Firefox release (`config.env`).
2. `scripts/prebuild.sh` – applies `patches/`, branding, and the bundled uBlock Origin.
3. `scripts/build.sh` – artifact-mode build: Mozilla's prebuilt engine is downloaded,
   only our Kotlin/resources/prefs are compiled. Outputs a Play AAB (or debug APK).

CI (`.github/workflows/build.yml`) runs all three on free GitHub runners.

## Patches

| Patch | What it does |
|---|---|
| 0001-privacy-defaults | Telemetry off, strict tracking protection, Global Privacy Control on, HTTPS-only on, search suggestions off, sponsored shortcuts off, bottom toolbar |
| 0002-app-id | Package `com.nexcraft.browser`, no Firefox sharedUserId |
| 0003-app-name | App name from `config.env` |
| 0004-bundle-ublock-origin | Installs bundled uBlock Origin at startup |
| 0005-premium-billing-and-stats | Hooks for blocked counter, Premium paywall (Play Billing 9.1.0), paywall once on 3rd launch |
| 0006-private-lock-and-always-incognito | Private-tab lock always available, Premium "Always incognito", Settings entry to Premium |

New source files (Premium, stats, search default) live in `overlay/` and are copied in by prebuild.
Trademark clean-up (`branding/replace_logos.py` + string rename) also runs in prebuild.

See **STATUS.md** for current blockers.

## Play Console setup for Premium

Create these products (IDs must match `PremiumManager.kt`):
- Subscription `premium` with base plans `monthly` and `yearly`; add a 7-day free-trial offer to each.
- One-time product `premium_lifetime`.
Prices: see the Pricing tab of the ASO workbook.

## Branding

`python3 branding/build_assets.py` regenerates launcher icons, wordmarks, the adaptive icon and
`branding/store/icon-512.png` (Play listing icon) from one source.

## Updating to a new Firefox release

1. Change `FIREFOX_TAG` in `config.env`.
2. Run get-source + prebuild. If a patch fails, fix it against the new source and regenerate it.
3. Ship. Falling behind on Firefox security releases is not optional.

## Licensing

- Firefox code is MPL-2.0: modified files must stay available as source (this repo + upstream).
- uBlock Origin is GPLv3: link its source (https://github.com/gorhill/uBlock) in the app's About/licenses.
- "Firefox" and its logos are Mozilla trademarks: `branding/` must replace every logo before release.

## Still to do

- See STATUS.md.
