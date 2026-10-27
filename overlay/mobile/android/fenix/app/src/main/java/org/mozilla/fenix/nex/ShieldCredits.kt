/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.nex

import android.util.Base64
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shield Credits: the app's in-app currency. 1 credit = 1 day of Premium.
 *
 * Credits are never sold and never earned in normal use. They are only issued by us, as a signed
 * code, when a customer asks for a refund outside Google Play's own refund window and we offer
 * credit instead of cash (see nexcraftgames.com/browser/refunds). The code is checked on the device
 * against our public key, so no server and no account are needed and nothing leaves the phone.
 *
 * Code format: NEX1-<base64url("v1|days|nonce|yyyyMMdd")>.<base64url(ECDSA P-256 / SHA-256 signature)>
 */
object ShieldCredits {
    private const val PUBLIC_KEY =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEYODS+Hczxh9EL0aFR+opY3TGwO1FwPJf6xcDjSXOKf5GAFu+p0Ci" +
            "nwiQB7XrL7paFmBCN8O+4aY7AzJa5wGnkA=="
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    sealed class Result {
        data class Added(val days: Int) : Result()
        data class Error(val message: String) : Result()
    }

    /** Days of Premium still covered by credits (0 when none). */
    fun daysLeft(prefs: NexPrefs, now: Long = System.currentTimeMillis()): Int {
        val left = prefs.creditPremiumUntil - now
        return if (left <= 0L) 0 else ((left + DAY_MS - 1) / DAY_MS).toInt()
    }

    fun redeem(prefs: NexPrefs, rawCode: String, now: Long = System.currentTimeMillis()): Result {
        val code = rawCode.trim().replace(" ", "").replace("\n", "")
        if (!code.startsWith("NEX1-")) return Result.Error("That code isn't valid.")
        val parts = code.removePrefix("NEX1-").split(".")
        if (parts.size != 2) return Result.Error("That code isn't valid.")
        val payload: ByteArray
        val sig: ByteArray
        try {
            payload = Base64.decode(parts[0], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            sig = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            return Result.Error("That code isn't valid.")
        }
        if (!verify(payload, sig)) return Result.Error("That code isn't valid.")

        val fields = String(payload, Charsets.UTF_8).split("|")
        if (fields.size != 4 || fields[0] != "v1") return Result.Error("That code isn't valid.")
        val days = fields[1].toIntOrNull() ?: return Result.Error("That code isn't valid.")
        val nonce = fields[2]
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(now))
        if (today > fields[3]) return Result.Error("This code has expired. Contact us for a new one.")
        if (nonce in prefs.redeemedCredits) return Result.Error("This code has already been used.")
        if (days !in 1..3650) return Result.Error("That code isn't valid.")

        prefs.redeemedCredits = prefs.redeemedCredits + nonce
        val start = maxOf(now, prefs.creditPremiumUntil)
        prefs.creditPremiumUntil = start + days * DAY_MS
        return Result.Added(days)
    }

    private fun verify(payload: ByteArray, sig: ByteArray): Boolean = try {
        val key = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(Base64.decode(PUBLIC_KEY, Base64.DEFAULT)))
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(key)
            update(payload)
            verify(sig)
        }
    } catch (e: Exception) {
        false
    }
}
