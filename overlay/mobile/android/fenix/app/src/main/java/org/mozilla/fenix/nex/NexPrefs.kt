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

    /** While [PREMIUM_FREE] is true every user gets Premium; no paywall is shown. */
    var isPremium: Boolean
        get() = PREMIUM_FREE || prefs.getBoolean(KEY_PREMIUM, false)
        set(value) = prefs.edit { putBoolean(KEY_PREMIUM, value) }

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

    companion object {
        /** v1 / testing: Premium unlocked for everyone. Set to false when Play products go live. */
        const val PREMIUM_FREE = true
        private const val FILE = "nex_prefs"
        private const val KEY_BLOCKED_TOTAL = "blocked_total"
        private const val KEY_PREMIUM = "premium_active"
        private const val KEY_LAUNCHES = "launch_count"
        private const val KEY_PAYWALL_SHOWN = "paywall_shown"
        private const val KEY_ALWAYS_INCOGNITO = "always_incognito"
        private const val KEY_SEARCH_DEFAULT = "search_default_applied"
    }
}
