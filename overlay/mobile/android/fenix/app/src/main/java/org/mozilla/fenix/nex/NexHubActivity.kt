/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.nex

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mozilla.components.compose.base.theme.NexAccent
import org.mozilla.fenix.BuildConfig
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.ext.components
import java.text.NumberFormat

/** Privacy Shield: blocked count plus every privacy control in one place. Premium ones open the paywall. */
class NexHubActivity : ComponentActivity() {
    private var version by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = NexPrefs(this)
        NexVpn.refreshServers(this) { version++ }
        val paywall = { startActivity(Intent(this, PremiumActivity::class.java)) }
        setContent {
            @Suppress("UNUSED_EXPRESSION")
            version
            Hub(
                prefs = prefs,
                onClose = { finish() },
                onPremium = paywall,
                onOpen = { url -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(packageName)) },
                onRedeem = { code ->
                    val result = ShieldCredits.redeem(prefs, code)
                    version++
                    NexVpn.push()
                    when (result) {
                        is ShieldCredits.Result.Added -> true to NexStrings.get(this, "credit_added", result.days)
                        is ShieldCredits.Result.Error -> false to when {
                            result.message.contains("expired") -> NexStrings.get(this, "credit_expired")
                            result.message.contains("already") -> NexStrings.get(this, "credit_used")
                            else -> NexStrings.get(this, "credit_bad")
                        }
                    }
                },
                onAlwaysIncognito = { on ->
                    prefs.alwaysIncognito = on
                    components.settings.openLinksInAPrivateTab = on
                    version++
                },
                onAppLock = { on ->
                    if (on && !NexLock.canAuthenticate(this)) {
                        Toast.makeText(this, NexStrings.get(this, "applock_need"), Toast.LENGTH_LONG).show()
                    } else {
                        prefs.appLock = on
                        NexLock.unlocked = true
                    }
                    version++
                },
                onVpn = { on ->
                    NexVpn.setEnabled(this, on)
                    version++
                },
                onVpnLocation = { id ->
                    NexVpn.setLocation(this, id)
                    version++
                },
                onIcons = {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("${BuildConfig.DEEP_LINK_SCHEME}://settings_app_icon"))
                            .setClass(this, HomeActivity::class.java),
                    )
                    finish()
                },
                onAccent = { i ->
                    prefs.accent = i
                    NexAccent.index = i
                    version++
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        version++
    }
}

@Suppress("LongParameterList")
@Composable
private fun Hub(
    prefs: NexPrefs,
    onClose: () -> Unit,
    onPremium: () -> Unit,
    onOpen: (String) -> Unit,
    onRedeem: (String) -> Pair<Boolean, String>,
    onAlwaysIncognito: (Boolean) -> Unit,
    onAppLock: (Boolean) -> Unit,
    onVpn: (Boolean) -> Unit,
    onVpnLocation: (String) -> Unit,
    onIcons: () -> Unit,
    onAccent: (Int) -> Unit,
) {
    val context = LocalContext.current
    val isPremium = prefs.isPremium
    val locked = !isPremium
    var confirmIncognito by remember { mutableStateOf(false) }
    if (confirmIncognito) {
        AlertDialog(
            onDismissRequest = { confirmIncognito = false },
            containerColor = NexColors.Card,
            title = { Text(nt("incog_warn_title"), color = NexColors.Text, fontWeight = FontWeight.Bold) },
            text = { Text(nt("incog_warn"), color = NexColors.Muted, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmIncognito = false
                    onAlwaysIncognito(true)
                }) { Text(nt("incog_ok"), color = NexColors.Mint, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmIncognito = false }) { Text(nt("cancel"), color = NexColors.Muted) }
            },
        )
    }

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
            CloseButton(onClose)
            Spacer(Modifier.width(14.dp))
            Text(
                nt("hub_title"),
                color = NexColors.Text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            PremiumPill(isPremium, short = true, onClick = onPremium)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatsCard(prefs.blockedTotal)

            if (locked) UpsellCard(onPremium)

            SectionLabel(nt("sec_protect"))
            VpnCard(context, prefs, locked, onPremium, onVpn, onVpnLocation)
            ToggleCard(ShieldGlyph.Slash, nt("incog"), nt("incog_sub"), prefs.alwaysIncognito, locked, onPremium) { on ->
                if (on) confirmIncognito = true else onAlwaysIncognito(false)
            }
            ToggleCard(ShieldGlyph.Lock, nt("applock"), nt("applock_sub"), prefs.appLock, locked, onPremium, onAppLock)

            SectionLabel(nt("sec_look"))
            PersonalizeCard(locked, onPremium, onIcons, onAccent)

            SectionLabel(nt("sec_account"))
            AccountCard(isPremium, onPremium)
            CreditsCard(prefs, onRedeem)

            LegalLinks(onOpen)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StatsCard(blocked: Long) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(NexColors.premium)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShieldMark(56.dp, glyph = ShieldGlyph.Check)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    NumberFormat.getIntegerInstance().format(blocked),
                    color = NexColors.Text,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(nt("hub_blocked_label"), color = NexColors.Text.copy(alpha = 0.85f), fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(NexColors.Ink.copy(alpha = 0.35f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text("● " + nt("hub_on"), color = NexColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun UpsellCard(onPremium: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .nexCard(selected = true)
            .clickable(onClick = onPremium)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShieldMark(40.dp, glyph = ShieldGlyph.Crown)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(nt("get_premium"), color = NexColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(nt("upsell_sub"), color = NexColors.Muted, fontSize = 13.sp)
        }
        Text("›", color = NexColors.Mint, fontSize = 28.sp)
    }
}

@Composable
private fun AccountCard(isPremium: Boolean, onPremium: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .nexCard()
            .clickable(onClick = onPremium)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (isPremium) nt("premium_active") else nt("premium"),
                color = NexColors.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (isPremium) nt("manage_sub") else nt("see_plans"),
                color = if (isPremium) NexColors.Mint else NexColors.Muted,
                fontSize = 13.sp,
            )
        }
        Text("›", color = NexColors.Muted, fontSize = 26.sp)
    }
}
