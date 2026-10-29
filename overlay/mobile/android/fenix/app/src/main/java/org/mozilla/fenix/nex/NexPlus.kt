/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.nex

import android.content.Context
import android.os.Handler
import android.os.Looper
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.webextension.EnableSource
import mozilla.components.concept.engine.webextension.MessageHandler
import mozilla.components.concept.engine.webextension.Port
import mozilla.components.concept.engine.webextension.WebExtension
import org.json.JSONObject

/**
 * Premium page tools:
 *  - Dark mode for every site: the bundled Dark Reader extension (MIT), switched on/off as a whole.
 *  - Data saver and Hide anything you tap: our built-in extension (assets/extensions/nexplus).
 *  - Strong anti-fingerprinting: Gecko's fingerprinting protection with every target on.
 */
object NexPlus {
    private const val PLUS_ID = "nexplus@nexcraftgames.com"
    private const val PLUS_URL = "resource://android/assets/extensions/nexplus/"
    private const val DARK_ID = "addon@darkreader.org"
    private const val DARK_URL = "resource://android/assets/extensions/darkreader/"
    private const val CHANNEL = "nexplus"

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var port: Port? = null
    private var appContext: Context? = null
    private var engine: Engine? = null
    private var dark: WebExtension? = null

    private val handler = object : MessageHandler {
        override fun onPortConnected(port: Port) {
            NexPlus.port = port
            push()
        }

        override fun onPortDisconnected(port: Port) {
            if (NexPlus.port === port) NexPlus.port = null
        }

        override fun onPortMessage(message: Any, port: Port) {
            readStats(message)
        }

        override fun onMessage(message: Any, source: EngineSession?): Any? {
            readStats(message)
            return config()
        }
    }

    fun install(context: Context, engine: Engine) {
        appContext = context.applicationContext
        this.engine = engine
        engine.installBuiltInWebExtension(
            id = PLUS_ID,
            url = PLUS_URL,
            onSuccess = { ext -> ext.registerBackgroundMessageHandler(CHANNEL, handler) },
            onError = { },
        )
        engine.installBuiltInWebExtension(
            id = DARK_ID,
            url = DARK_URL,
            onSuccess = { ext ->
                dark = ext
                applyDark()
            },
            onError = { },
        )
        applyFingerprinting()
    }

    private fun prefs(): NexPrefs? = appContext?.let { NexPrefs(it) }

    private fun config(): JSONObject {
        val p = prefs() ?: return JSONObject().put("lite", false).put("hide", false)
        return JSONObject()
            .put("lite", p.isPremium && p.dataSaver)
            .put("hide", p.isPremium && p.hideItems)
    }

    private fun readStats(message: Any) {
        val m = message as? JSONObject ?: return
        if (m.optString("type") != "stats") return
        val p = prefs() ?: return
        m.optJSONObject("stats")?.let { p.liteSavedKb = it.optLong("kb", p.liteSavedKb) }
        if (m.has("rules")) p.hiddenRules = m.optInt("rules", p.hiddenRules)
    }

    /** Sends the current switches to the extension and applies Dark mode and anti-fingerprinting. */
    fun push() {
        main.post {
            port?.postMessage(JSONObject().put("type", "config").put("config", config()))
            applyDark()
            applyFingerprinting()
        }
    }

    /** Starts the "tap something to hide it" picker on the current page. */
    fun pickOnPage() {
        main.post { port?.postMessage(JSONObject().put("type", "pick")) }
    }

    fun clearHiddenRules(context: Context) {
        NexPrefs(context).hiddenRules = 0
        main.post { port?.postMessage(JSONObject().put("type", "clearRules")) }
    }

    private fun applyDark() {
        val ext = dark ?: return
        val eng = engine ?: return
        val p = prefs() ?: return
        val want = p.isPremium && p.darkMode
        if (want == ext.isEnabled()) return
        if (want) {
            eng.enableWebExtension(ext, EnableSource.USER, onSuccess = { dark = it })
        } else {
            eng.disableWebExtension(ext, EnableSource.USER, onSuccess = { dark = it })
        }
    }

    private fun applyFingerprinting() {
        val eng = engine ?: return
        val p = prefs() ?: return
        if (p.isPremium && p.strongFingerprint) {
            eng.settings.fingerprintingProtection = true
            eng.settings.fingerprintingProtectionPrivateBrowsing = true
            eng.settings.fingerprintingProtectionOverrides = STRONG_FP
        } else if (eng.settings.fingerprintingProtectionOverrides == STRONG_FP) {
            eng.settings.fingerprintingProtectionOverrides = ""
        }
    }

    private const val STRONG_FP = "+AllTargets"
}
