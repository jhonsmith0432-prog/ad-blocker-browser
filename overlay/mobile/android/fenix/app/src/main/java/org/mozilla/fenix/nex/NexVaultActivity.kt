/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.nex

import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Premium Vault: private files behind fingerprint or PIN. Locks again every time it's opened. */
class NexVaultActivity : FragmentActivity() {
    private var version by mutableIntStateOf(0)
    private var unlocked by mutableStateOf(false)
    private var prompting = false

    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        Thread {
            uris.forEach { NexVault.import(this, it) }
            runOnUiThread { version++ }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = NexPrefs(this)
        if (!prefs.isPremium) {
            startActivity(Intent(this, PremiumActivity::class.java))
            finish()
            return
        }
        setContent {
            @Suppress("UNUSED_EXPRESSION")
            version
            Column(
                Modifier
                    .fillMaxSize()
                    .background(NexColors.screen)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CloseButton { finish() }
                    Spacer(Modifier.width(14.dp))
                    Text(nt("vault"), color = NexColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                if (!unlocked) {
                    Column(
                        Modifier.fillMaxSize().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ShieldMark(110.dp, glyph = ShieldGlyph.Lock)
                        Spacer(Modifier.height(20.dp))
                        Text(nt("vault_locked"), color = NexColors.Muted, fontSize = 15.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(24.dp))
                        NexButton(nt("unlock")) { prompt() }
                    }
                } else {
                    VaultList(prefs)
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun VaultList(prefs: NexPrefs) {
        val files = NexVault.files(this)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ToggleCard(ShieldGlyph.Lock, nt("vault_auto"), nt("vault_auto_sub"), prefs.vaultAutoMove, false, {}) {
                prefs.vaultAutoMove = it
                version++
            }
            NexButton(nt("vault_add")) { picker.launch(arrayOf("*/*")) }
            if (files.isEmpty()) {
                Text(
                    nt("vault_empty"),
                    color = NexColors.Muted,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                )
            }
            files.forEach { file -> FileRow(file) }
            Spacer(Modifier.height(12.dp))
        }
    }

    @androidx.compose.runtime.Composable
    private fun FileRow(file: File) {
        var menu by androidx.compose.runtime.remember { mutableStateOf(false) }
        Column(Modifier.fillMaxWidth().nexCard().clickable { open(file) }.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(NexColors.CardHi),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(file.extension.take(4).uppercase(), color = NexColors.Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(file.name, color = NexColors.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        Formatter.formatShortFileSize(this@NexVaultActivity, file.length()) + " · " +
                            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(file.lastModified())),
                        color = NexColors.Muted,
                        fontSize = 12.sp,
                    )
                }
                TextButton(onClick = { menu = !menu }) { Text("⋯", color = NexColors.Muted, fontSize = 20.sp) }
            }
            if (menu) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { share(file) }) { Text(nt("share"), color = NexColors.Mint) }
                    TextButton(onClick = {
                        file.delete()
                        version++
                    }) { Text(nt("delete"), color = NexColors.Gold) }
                }
            }
        }
    }

    private fun uriFor(file: File) = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

    private fun mime(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"

    private fun open(file: File) {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uriFor(file), mime(file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            share(file)
        }
    }

    private fun share(file: File) {
        val intent = Intent(Intent.ACTION_SEND).setType(mime(file))
            .putExtra(Intent.EXTRA_STREAM, uriFor(file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, file.name))
    }

    override fun onResume() {
        super.onResume()
        if (!unlocked) prompt()
    }

    private fun prompt() {
        if (!NexLock.canAuthenticate(this)) {
            unlocked = true
            return
        }
        if (prompting) return
        prompting = true
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                prompting = false
                unlocked = true
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                prompting = false
            }
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(NexStrings.get(this, "vault"))
            .setAllowedAuthenticators(NexLock.AUTHENTICATORS)
            .build()
        BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback).authenticate(info)
    }
}
