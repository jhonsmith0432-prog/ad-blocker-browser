/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.nex

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.widget.Toast
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.DownloadAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store
import java.io.File

/** Premium Vault: files kept in the app's private storage, opened only after fingerprint or PIN. */
object NexVault {
    const val DIR = "nexvault"

    fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    fun files(context: Context): List<File> =
        dir(context).listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()

    /** A name that doesn't clash with a file already in the vault. */
    fun target(context: Context, name: String): File {
        val clean = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "file" }
        var file = File(dir(context), clean)
        var n = 1
        while (file.exists()) {
            val dot = clean.lastIndexOf('.')
            val stem = if (dot > 0) clean.substring(0, dot) else clean
            val ext = if (dot > 0) clean.substring(dot) else ""
            file = File(dir(context), "$stem ($n)$ext")
            n++
        }
        return file
    }

    /** Copies a file the user picked (Storage Access Framework) into the vault. */
    fun import(context: Context, uri: Uri): Boolean {
        val resolver = context.contentResolver
        var name = "file"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) name = c.getString(0) ?: name
        }
        val out = target(context, name)
        return try {
            resolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } } != null
        } catch (e: java.io.IOException) {
            out.delete()
            false
        } catch (e: SecurityException) {
            out.delete()
            false
        }
    }

    /** Moves a finished download into the vault (copy, then delete the public copy). */
    fun moveDownload(context: Context, download: DownloadState): Boolean {
        val source = File(download.filePath)
        if (!source.isFile) return false
        val out = target(context, download.fileName ?: source.name)
        return try {
            source.inputStream().use { input -> out.outputStream().use { input.copyTo(it) } }
            source.delete()
            true
        } catch (e: java.io.IOException) {
            out.delete()
            false
        } catch (e: SecurityException) {
            out.delete()
            false
        }
    }
}

/** When "Move new downloads to the Vault" is on (Premium), each finished download goes into the vault. */
class NexVaultMiddleware(context: Context) : Middleware<BrowserState, BrowserAction> {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val moved = mutableSetOf<String>()

    override fun invoke(
        store: Store<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction,
    ) {
        next(action)
        if (action !is DownloadAction.UpdateDownloadAction) return
        val download = action.download
        if (download.status != DownloadState.Status.COMPLETED || download.id in moved) return
        val prefs = NexPrefs(app)
        if (!prefs.isPremium || !prefs.vaultAutoMove) return
        moved.add(download.id)
        Thread {
            if (NexVault.moveDownload(app, download)) {
                main.post {
                    Toast.makeText(app, NexStrings.get(app, "vault_moved", download.fileName ?: ""), Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }.start()
    }
}
