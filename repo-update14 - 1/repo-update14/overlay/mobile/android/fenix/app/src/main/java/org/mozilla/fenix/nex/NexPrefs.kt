/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.nex

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Local-only app state: blocked counter, Premium entitlement cache, launch count.
 * Nothing here ever leaves the device.
 */
class NexPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    val blockedTotal: Long get() = prefs.getLong(KEY_BLOCKED_TOTAL, 0L)

    fun addBlocked(count: Long = 1L) {
        prefs.edit { putLong(KEY_BLOCKED_TOTAL, blockedTotal + count) }
    }

    /**
     * Premium if [PREMIUM_FREE], a Google Play purchase is active, or Shield Credits still cover today.
     * The setter only stores the Google Play state; credits are tracked in [creditPremiumUntil].
     */
    var isPremium: Boolean
        get() = PREMIUM_FREE || prefs.getBoolean(KEY_PREMIUM, false) ||
            System.currentTimeMillis() < creditPremiumUntil
        set(value) = prefs.edit { putBoolean(KEY_PREMIUM, value) }

    /** Shield Credits: Premium is covered until this time (epoch ms). Only refund codes add to it. */
    var creditPremiumUntil: Long
        get() = prefs.getLong(KEY_CREDIT_UNTIL, 0L)
        set(value) = prefs.edit { putLong(KEY_CREDIT_UNTIL, value) }

    /** Nonces of Shield Credits codes already redeemed on this phone (a code works once). */
    var redeemedCredits: Set<String>
        get() = prefs.getStringSet(KEY_CREDIT_USED, emptySet()) ?: emptySet()
        set(value) = prefs.edit { putStringSet(KEY_CREDIT_USED, value.toSet()) }

    var launchCount: Int
        get() = prefs.getInt(KEY_LAUNCHES, 0)
        set(value) = prefs.edit { putInt(KEY_LAUNCHES, value) }

    /** Premium: every new session starts in private (incognito) mode. */
    var alwaysIncognito: Boolean
        get() = prefs.getBoolean(KEY_ALWAYS_INCOGNITO, false)
        set(value) = prefs.edit { putBoolean(KEY_ALWAYS_INCOGNITO, value) }

    val alwaysIncognitoActive: Boolean get() = isPremium && alwaysIncognito

    /** True once we've set the privacy-respecting default search engine (only ever done once). */
    var searchDefaultApplied: Boolean
        get() = prefs.getBoolean(KEY_SEARCH_DEFAULT, false)
        set(value) = prefs.edit { putBoolean(KEY_SEARCH_DEFAULT, value) }

    var paywallShown: Boolean
        get() = prefs.getBoolean(KEY_PAYWALL_SHOWN, false)
        set(value) = prefs.edit { putBoolean(KEY_PAYWALL_SHOWN, value) }

    /** First-launch welcome screens were shown. */
    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit { putBoolean(KEY_ONBOARDING_DONE, value) }

    /** Premium Browser VPN switch (only routes traffic while Premium is active). */
    var vpnEnabled: Boolean
        get() = prefs.getBoolean(KEY_VPN_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_VPN_ENABLED, value) }

    /** Id of the chosen VPN location from the server list. */
    var vpnLocation: String
        get() = prefs.getString(KEY_VPN_LOCATION, "") ?: ""
        set(value) = prefs.edit { putString(KEY_VPN_LOCATION, value) }

    /** Last downloaded VPN server list (JSON), so the VPN works offline-at-start. */
    var vpnServersJson: String
        get() = prefs.getString(KEY_VPN_SERVERS, "") ?: ""
        set(value) = prefs.edit { putString(KEY_VPN_SERVERS, value) }

    /** Premium App lock: ask for fingerprint / screen-lock PIN whenever the browser is opened. */
    var appLock: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCK, false)
        set(value) = prefs.edit { putBoolean(KEY_APP_LOCK, value) }

    val appLockActive: Boolean get() = isPremium && appLock

    /** Premium accent colour (index into NexAccent; 0 = Mint). */
    var accent: Int
        get() = prefs.getInt(KEY_ACCENT, 0)
        set(value) = prefs.edit { putInt(KEY_ACCENT, value) }

    /** Premium Dark mode for every site (bundled Dark Reader). */
    var darkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK, false)
        set(value) = prefs.edit { putBoolean(KEY_DARK, value) }

    /** Premium Data saver: skip images, web fonts and video/audio downloads. */
    var dataSaver: Boolean
        get() = prefs.getBoolean(KEY_LITE, false)
        set(value) = prefs.edit { putBoolean(KEY_LITE, value) }

    /** Data saver estimate reported by the extension, in KB. */
    var liteSavedKb: Long
        get() = prefs.getLong(KEY_LITE_KB, 0L)
        set(value) = prefs.edit { putLong(KEY_LITE_KB, value) }

    /** Premium Hide anything you tap. */
    var hideItems: Boolean
        get() = prefs.getBoolean(KEY_HIDE, true)
        set(value) = prefs.edit { putBoolean(KEY_HIDE, value) }

    /** Number of saved "hide this" rules, as last reported by the extension. */
    var hiddenRules: Int
        get() = prefs.getInt(KEY_HIDE_COUNT, 0)
        set(value) = prefs.edit { putInt(KEY_HIDE_COUNT, value) }

    /** Premium strong anti-fingerprinting. */
    var strongFingerprint: Boolean
        get() = prefs.getBoolean(KEY_FP, false)
        set(value) = prefs.edit { putBoolean(KEY_FP, value) }

    /** Premium auto-wipe: 0 off, otherwise minutes away before wiping (WIPE_NOW = as soon as you leave). */
    var wipeAfter: Int
        get() = prefs.getInt(KEY_WIPE, 0)
        set(value) = prefs.edit { putInt(KEY_WIPE, value) }

    /** When the whole app last went to the background (epoch ms), for auto-wipe. */
    var leftAt: Long
        get() = prefs.getLong(KEY_LEFT_AT, 0L)
        set(value) = prefs.edit { putLong(KEY_LEFT_AT, value) }

    /** Premium Vault: move finished downloads into the locked vault. */
    var vaultAutoMove: Boolean
        get() = prefs.getBoolean(KEY_VAULT_AUTO, false)
        set(value) = prefs.edit { putBoolean(KEY_VAULT_AUTO, value) }

    companion object {
        /**
         * Closed testing: every tester gets all of Premium (App lock, Always incognito, Browser VPN).
         * MUST be false in the production build, or nobody is ever asked to pay.
         */
        const val PREMIUM_FREE = true
        private const val FILE = "nex_prefs"
        private const val KEY_BLOCKED_TOTAL = "blocked_total"
        private const val KEY_PREMIUM = "premium_active"
        private const val KEY_LAUNCHES = "launch_count"
        private const val KEY_PAYWALL_SHOWN = "paywall_shown"
        private const val KEY_ALWAYS_INCOGNITO = "always_incognito"
        private const val KEY_SEARCH_DEFAULT = "search_default_applied"
        private const val KEY_CREDIT_UNTIL = "credit_premium_until"
        private const val KEY_CREDIT_USED = "credit_codes_used"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_VPN_ENABLED = "vpn_enabled"
        private const val KEY_VPN_LOCATION = "vpn_location"
        private const val KEY_VPN_SERVERS = "vpn_servers_json"
        private const val KEY_APP_LOCK = "app_lock"
        private const val KEY_ACCENT = "accent"
        private const val KEY_DARK = "dark_mode"
        private const val KEY_LITE = "data_saver"
        private const val KEY_LITE_KB = "data_saver_kb"
        private const val KEY_HIDE = "hide_items"
        private const val KEY_HIDE_COUNT = "hide_rules"
        private const val KEY_FP = "strong_fingerprint"
        private const val KEY_WIPE = "wipe_after"
        private const val KEY_LEFT_AT = "left_at"
        private const val KEY_VAULT_AUTO = "vault_auto_move"
        const val WIPE_NOW = 1
    }
}
