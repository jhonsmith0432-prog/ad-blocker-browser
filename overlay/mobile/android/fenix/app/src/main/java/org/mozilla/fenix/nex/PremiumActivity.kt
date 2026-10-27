/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.nex

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mozilla.fenix.ext.components
import java.text.NumberFormat

/** Premium paywall: 3-day trial (yearly/monthly) or lifetime, App lock, Always incognito, Browser VPN, Shield Credits. */
class PremiumActivity : ComponentActivity() {
    private lateinit var premium: PremiumManager
    private var version by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        premium = PremiumManager(this)
        premium.onChanged = {
            runOnUiThread {
                version++
                NexVpn.push()
            }
        }
        premium.connect()
        NexVpn.refreshServers(this) { version++ }
        val prefs = NexPrefs(this)
        setContent {
            @Suppress("UNUSED_EXPRESSION")
            version // recompose when Play data or VPN servers arrive
            Paywall(
                prefs = prefs,
                premium = premium,
                onBuy = { plan ->
                    if (plan == Plan.LIFETIME) premium.buyLifetime(this) else premium.buySubscription(this, plan.planId)
                },
                onRestore = { premium.restore() },
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
                onOpen = { url -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(packageName)) },
                onClose = { finish() },
                onAlwaysIncognito = { on ->
                    prefs.alwaysIncognito = on
                    // Links opened from other apps also go to private tabs while this is on.
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
            )
        }
    }

    override fun onDestroy() {
        premium.end()
        super.onDestroy()
    }
}

enum class Plan(val planId: String, val key: String) {
    YEARLY(PremiumManager.PLAN_YEARLY, "yearly"),
    MONTHLY(PremiumManager.PLAN_MONTHLY, "monthly"),
    LIFETIME(PremiumManager.LIFETIME_ID, "lifetime"),
}

@Composable
private fun t(key: String, vararg args: Any): String = NexStrings.get(LocalContext.current, key, *args)

@Composable
private fun Paywall(
    prefs: NexPrefs,
    premium: PremiumManager,
    onBuy: (Plan) -> Unit,
    onRestore: () -> Unit,
    onRedeem: (String) -> Pair<Boolean, String>,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
    onAlwaysIncognito: (Boolean) -> Unit,
    onAppLock: (Boolean) -> Unit,
    onVpn: (Boolean) -> Unit,
    onVpnLocation: (String) -> Unit,
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(Plan.YEARLY) }
    val isPremium = premium.isPremium
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexColors.screen)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClose) { Text(t("not_now"), color = NexColors.Muted) }
        }

        // Hero
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ShieldMark(96.dp, glyph = ShieldGlyph.Crown)
            Spacer(Modifier.height(12.dp))
            Text(t("premium"), color = NexColors.Text, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (isPremium) t("is_premium") else t("tagline"),
                color = if (isPremium) NexColors.Mint else NexColors.Muted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
            val blocked = prefs.blockedTotal
            if (blocked > 0) {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(NexColors.Mint.copy(alpha = 0.14f))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        t("blocked", NumberFormat.getIntegerInstance().format(blocked)),
                        color = NexColors.Mint,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // Browser VPN only appears once real VPN locations are published.
        val vpnLive = NexVpn.servers(context).isNotEmpty()
        if (isPremium) {
            if (vpnLive) VpnCard(context, prefs, onVpn, onVpnLocation)
            ToggleCard(t("applock"), t("applock_sub"), prefs.appLock, onAppLock)
            ToggleCard(t("incog"), t("incog_sub"), prefs.alwaysIncognito, onAlwaysIncognito)
        } else {
            Column(Modifier.fillMaxWidth().nexCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (vpnLive) Feature(ShieldGlyph.Globe, t("f_vpn"))
                Feature(ShieldGlyph.Lock, t("f_applock"))
                Feature(ShieldGlyph.Crown, t("f_incog"))
            }

            Plan.entries.forEach { plan ->
                val price = when (plan) {
                    Plan.LIFETIME -> premium.lifetimePrice()?.let { t("one_payment", it) }
                    else -> premium.planPrice(plan.planId)?.let { p ->
                        val per = if (plan == Plan.YEARLY) t("per_year", p) else t("per_month", p)
                        if (premium.hasTrial(plan.planId)) t("trial_then", per) else per
                    }
                } ?: t("loading")
                PlanCard(t(plan.key), price, plan == selected, badge = if (plan == Plan.YEARLY) t("best_value") else null) {
                    selected = plan
                }
            }

            val cta = when {
                selected == Plan.LIFETIME -> t("cta_life")
                premium.hasTrial(selected.planId) -> t("cta_trial")
                else -> t("cta_sub")
            }
            NexButton(cta, enabled = !isPremium) { onBuy(selected) }
            Text(
                if (selected == Plan.LIFETIME) t("life_note") else t("renew_note"),
                color = NexColors.Muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onRestore, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(t("restore"), color = NexColors.Muted)
            }
        }

        CreditsCard(prefs, onRedeem)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { onOpen(TERMS_URL) }) { Text(t("terms"), color = NexColors.Muted, fontSize = 12.sp) }
            TextButton(onClick = { onOpen(REFUNDS_URL) }) { Text(t("refunds"), color = NexColors.Muted, fontSize = 12.sp) }
            TextButton(onClick = { onOpen(PRIVACY_URL) }) { Text(t("privacy"), color = NexColors.Muted, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun Feature(glyph: ShieldGlyph, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ShieldMark(30.dp, glyph = glyph)
        Spacer(Modifier.width(12.dp))
        Text(text, color = NexColors.Text, fontSize = 15.sp)
    }
}

@Composable
private fun PlanCard(title: String, subtitle: String, isSelected: Boolean, badge: String?, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (badge != null) 10.dp else 0.dp)
                .nexCard(selected = isSelected)
                .clickable(onClick = onClick)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) NexColors.Mint else NexColors.Line),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = NexColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = NexColors.Muted, fontSize = 13.sp)
            }
        }
        if (badge != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(NexColors.Gold)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                Text(badge, color = NexColors.Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ToggleCard(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().nexCard().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = NexColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = NexColors.Muted, fontSize = 13.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = NexColors.Mint, checkedThumbColor = NexColors.Ink),
        )
    }
}

@Composable
private fun VpnCard(context: Context, prefs: NexPrefs, onVpn: (Boolean) -> Unit, onVpnLocation: (String) -> Unit) {
    val servers = NexVpn.servers(context)
    val current = NexVpn.selected(context)
    Column(Modifier.fillMaxWidth().nexCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShieldMark(34.dp, glyph = ShieldGlyph.Globe)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t("vpn"), color = NexColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        servers.isEmpty() -> t("vpn_soon")
                        prefs.vpnEnabled && current != null -> t("vpn_on", "${current.flag} ${current.name}".trim())
                        else -> t("vpn_off")
                    },
                    color = if (prefs.vpnEnabled && current != null) NexColors.Mint else NexColors.Muted,
                    fontSize = 13.sp,
                )
            }
            Switch(
                checked = prefs.vpnEnabled && servers.isNotEmpty(),
                enabled = servers.isNotEmpty(),
                onCheckedChange = onVpn,
                colors = SwitchDefaults.colors(checkedTrackColor = NexColors.Mint, checkedThumbColor = NexColors.Ink),
            )
        }
        if (servers.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                servers.forEach { s ->
                    val on = s.id == current?.id
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (on) NexColors.Mint else NexColors.CardHi)
                            .clickable { onVpnLocation(s.id) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text("${s.flag} ${s.name}".trim(), color = if (on) NexColors.Ink else NexColors.Text, fontSize = 13.sp)
                    }
                }
            }
        }
        Text(t("vpn_note"), color = NexColors.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun CreditsCard(prefs: NexPrefs, onRedeem: (String) -> Pair<Boolean, String>) {
    val days = ShieldCredits.daysLeft(prefs)
    Column(
        modifier = Modifier.fillMaxWidth().nexCard().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(t("credits"), color = NexColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            if (days > 0) t("credits_left", days) else t("no_credits"),
            color = if (days > 0) NexColors.Mint else NexColors.Muted,
            fontSize = 13.sp,
        )
        var code by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("") }
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            singleLine = true,
            placeholder = { Text(t("credit_hint"), color = NexColors.Muted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = NexColors.Text,
                unfocusedTextColor = NexColors.Text,
                focusedBorderColor = NexColors.Mint,
                unfocusedBorderColor = NexColors.Line,
                cursorColor = NexColors.Mint,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = {
            val (ok, text) = onRedeem(code)
            message = text
            if (ok) code = ""
        }) {
            Text(t("redeem"), color = NexColors.Mint)
        }
        if (message.isNotEmpty()) Text(message, color = NexColors.Muted, fontSize = 13.sp)
    }
}

private const val TERMS_URL = "https://nexcraftgames.com/browser/terms"
private const val REFUNDS_URL = "https://nexcraftgames.com/browser/refunds"
private const val PRIVACY_URL = "https://nexcraftgames.com/browser/privacy"
