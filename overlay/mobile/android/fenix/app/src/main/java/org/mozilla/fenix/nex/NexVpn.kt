/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.nex

import android.content.Context
import android.os.Handler
import android.os.Looper
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

        // The extension also polls with sendNativeMessage, so a lapsed subscription turns it off.
        override fun onMessage(message: Any, source: EngineSession?): Any? = config()
    }

    fun install(context: Context, engine: Engine) {
        appContext = context.applicationContext
        engine.installBuiltInWebExtension(
            id = EXT_ID,
            url = EXT_URL,
            onSuccess = { ext -> ext.registerBackgroundMessageHandler(CHANNEL, handler) },
            onError = { },
        )
        refreshServers(context) { }
    }

    fun servers(context: Context): List<Server> {
        val raw = NexPrefs(context).vpnServersJson
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
    fun push() {
        main.post {
            port?.postMessage(JSONObject().put("type", "config").put("config", config()))
        }
    }

    fun setEnabled(context: Context, on: Boolean) {
        NexPrefs(context).vpnEnabled = on
        push()
    }

    fun setLocation(context: Context, id: String) {
        NexPrefs(context).vpnLocation = id
        push()
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
