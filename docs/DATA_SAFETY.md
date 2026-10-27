# Google Play Data safety — suggested answers

Based on Play's definitions (support.google.com/googleplay/android-developer/answer/10787469):
"collected" = sent off the device by the app (including its libraries); data the user sends to websites
they browse on the open web does not need to be declared; data processed only on-device is not collected.

## Suggested form answers

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | Telemetry is off (patch 0001); stats, history and Premium status stay on-device; no analytics or ad SDKs. |
| Is all user data encrypted in transit? | Yes (if asked) | HTTPS-only mode is on by default (patch 0001). |
| Do you provide a way for users to request data deletion? | Not applicable / on-device | We hold no user data; users clear data in Settings. |
| Privacy policy URL | Host docs/PRIVACY_POLICY.md (e.g. GitHub Pages) | Required even when no data is collected. |

## Must confirm before submitting (see STATUS.md)

1. **Crash reporter**: Fenix asks before sending crash reports to Mozilla (user-initiated → not "sharing"). Keep it opt-in.
2. **Mozilla services still active**: remote settings, add-on update checks, push service, Safe Browsing.
   These are standard browser functions sent to Mozilla/Google, not to us; Play's form is about the
   developer's app as a whole, so if any of them sends identifiers, declare "App functionality" →
   "Device or other IDs". Best fix: disable what we don't need (Mozilla account/Sync, push) in a later patch.
3. **Play Billing**: purchase data handled by Google Play is not declared by the developer.
