/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.nex

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.webextension.MessageHandler
import mozilla.components.concept.engine.webextension.Port
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Premium "Browser VPN": routes this browser's page traffic through a Nexcraft HTTPS proxy.
 * Only the browser is covered (no Android VpnService), so other apps are untouched.
 *
 * The routing itself happens in a built-in WebExtension (assets/extensions/nexvpn) using the
 * proxy API; this object feeds it the chosen server over native messaging. The server list is
 * downloaded from [SERVERS_URL] so locations can be added without an app update.
 */
object NexVpn {
    /** What the VPN is doing right now. Compose screens read these, so a tap updates every screen at once. */
    enum class State { OFF, CONNECTING, ON, FAILED }
    val state = mutableStateOf(State.OFF)
    val wanted = mutableStateOf(false)
    val ip = mutableStateOf("")
    private const val CHECK_TIMEOUT_MS = 15_000L
    private val giveUp = Runnable { if (state.value == State.CONNECTING) state.value = State.FAILED }

    const val EXT_ID = "nexvpn@nexcraftgames.com"
    private const val EXT_URL = "resource://android/assets/extensions/nexvpn/"
    private const val SERVERS_URL = "https://nexcraftgames.com/browser/vpn.json"
    private const val CHANNEL = "nexvpn"

    data class Server(val id: String, val name: String, val flag: String, val host: String, val port: Int, val auth: String)

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var port: Port? = null
    private var appContext: Context? = null

    private val handler = object : MessageHandler {
        override fun onPortConnected(port: Port) {
            NexVpn.port = port
            push()
        }

        override fun onPortDisconnected(port: Port) {
            if (NexVpn.port === port) NexVpn.port = null
        }

        override fun onPortMessage(message: Any, port: Port) {
            onStatus(message)
        }

        // The extension polls with sendNativeMessage (so a lapsed subscription turns it off) and
        // reports the result of its connection check the same way.
        override fun onMessage(message: Any, source: EngineSession?): Any? {
            if (onStatus(message)) return null
            return config()
        }
    }

    /** The extension checked the connection through the server: {type:"status", state:"on"|"failed", ip}. */
    private fun onStatus(message: Any): Boolean {
        val o = (message as? JSONObject) ?: (message as? String)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return false
        if (o.optString("type") != "status") return false
        main.post {
            main.removeCallbacks(giveUp)
            if (!wanted.value) {
                state.value = State.OFF
            } else if (o.optString("state") == "on") {
                state.value = State.ON
                ip.value = o.optString("ip")
            } else {
                state.value = State.FAILED
                ip.value = ""
            }
        }
        return true
    }

    /** Re-reads prefs into the UI state and, if the VPN should be on, starts a fresh connection check. */
    private fun sync(context: Context, check: Boolean) {
        wanted.value = NexPrefs(context).vpnEnabled
        main.removeCallbacks(giveUp)
        if (isActive(context)) {
            if (check || state.value == State.OFF) {
                state.value = State.CONNECTING
                ip.value = ""
                main.postDelayed(giveUp, CHECK_TIMEOUT_MS)
            }
        } else {
            state.value = State.OFF
            ip.value = ""
        }
    }

    fun install(context: Context, engine: Engine) {
        appContext = context.applicationContext
        sync(context, check = true)
        engine.installBuiltInWebExtension(
            id = EXT_ID,
            url = EXT_URL,
            onSuccess = { ext -> ext.registerBackgroundMessageHandler(CHANNEL, handler) },
            onError = { },
        )
        refreshServers(context) { }
    }

    /**
     * Shipped with the app so the VPN works before the list has ever been downloaded.
     * Filled in at build time from the NEXVPN_BUILTIN secret (never committed); empty = no fallback.
     */
    private const val BUILT_IN = "@NEXVPN_BUILTIN@"

    fun servers(context: Context): List<Server> {
        // A downloaded list always wins (an empty one hides the VPN); the built-in list is the fallback.
        val raw = NexPrefs(context).vpnServersJson.ifBlank { BUILT_IN.takeIf { it.startsWith("{") } ?: "" }
        if (raw.isBlank()) return emptyList()
        return try {
            val arr: JSONArray = JSONObject(raw).optJSONArray("servers") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val host = o.optString("host")
                if (host.isBlank()) return@mapNotNull null
                Server(
                    id = o.optString("id", host),
                    name = o.optString("name", host),
                    flag = o.optString("flag", ""),
                    host = host,
                    port = o.optInt("port", 443),
                    auth = o.optString("auth", ""),
                )
            }
        } catch (e: org.json.JSONException) {
            emptyList()
        }
    }

    fun selected(context: Context): Server? {
        val list = servers(context)
        val id = NexPrefs(context).vpnLocation
        return list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }

    fun isActive(context: Context): Boolean {
        val prefs = NexPrefs(context)
        return prefs.isPremium && prefs.vpnEnabled && selected(context) != null
    }

    private fun config(): JSONObject {
        val ctx = appContext ?: return JSONObject().put("enabled", false)
        val server = selected(ctx)
        if (server == null || !isActive(ctx)) return JSONObject().put("enabled", false); return JSONObject().put("enabled", true).put("host", server.host).put("port", server.port).put("auth", server.auth)
    }

    /** Sends the current state to the extension (call after any change to Premium or VPN prefs). */
    fun push(check: Boolean = false) {
        main.post {
            appContext?.let { if (check || isActive(it) != (state.value != State.OFF)) sync(it, check) }
            port?.postMessage(JSONObject().put("type", "config").put("config", config()).put("check", check))
        }
    }

    fun setEnabled(context: Context, on: Boolean) {
        NexPrefs(context).vpnEnabled = on
        wanted.value = on
        appContext = appContext ?: context.applicationContext
        push(check = on)
    }

    /** One-tap switch used by the home button and the main menu. */
    fun toggle(context: Context) = setEnabled(context, !NexPrefs(context).vpnEnabled)

    fun setLocation(context: Context, id: String) {
        NexPrefs(context).vpnLocation = id
        push(check = true)
    }

    /** Downloads the server list (no personal data is sent) and caches it. */
    fun refreshServers(context: Context, done: (List<Server>) -> Unit) {
        val app = context.applicationContext
        Thread {
            try {
                val conn = URL(SERVERS_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("Cache-Control", "no-cache")
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    JSONObject(body) // validate before caching
                    NexPrefs(app).vpnServersJson = body
                }
                conn.disconnect()
            } catch (e: java.io.IOException) {
                // offline: keep the cached list
            } catch (e: org.json.JSONException) {
                // bad file on the server: keep the cached list
            }
            main.post {
                push()
                done(servers(app))
            }
        }.start()
    }
}
