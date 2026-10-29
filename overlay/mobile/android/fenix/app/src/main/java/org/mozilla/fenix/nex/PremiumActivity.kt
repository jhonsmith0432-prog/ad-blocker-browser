/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.nex

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat

/** Premium paywall only: what you get, the three plans, one button. Settings live in [NexHubActivity]. */
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
        val prefs = NexPrefs(this)
        setContent {
            @Suppress("UNUSED_EXPRESSION")
            version
            Paywall(
                prefs = prefs,
                premium = premium,
                onBuy = { plan ->
                    if (plan == Plan.LIFETIME) premium.buyLifetime(this) else premium.buySubscription(this, plan.planId)
                },
                onRestore = { premium.restore() },
                onOpen = { url -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(packageName)) },
                onManage = { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MANAGE_SUBS_URL))) },
                onShield = {
                    startActivity(Intent(this, NexHubActivity::class.java))
                    finish()
                },
                onClose = { finish() },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        version++
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
private fun Paywall(
    prefs: NexPrefs,
    premium: PremiumManager,
    onBuy: (Plan) -> Unit,
    onRestore: () -> Unit,
    onOpen: (String) -> Unit,
    onManage: () -> Unit,
    onShield: () -> Unit,
    onClose: () -> Unit,
) {
    var selected by remember { mutableStateOf(Plan.YEARLY) }
    val isPremium = premium.isPremium
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
            Spacer(Modifier.weight(1f))
            if (!isPremium) {
                TextButton(onClick = onRestore) { Text(nt("restore"), color = NexColors.Muted, fontSize = 14.sp) }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ShieldMark(60.dp, glyph = ShieldGlyph.Crown, tint = NexColors.premium)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(nt("premium"), color = NexColors.Text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (isPremium) nt("is_premium") else nt("tagline"),
                        color = if (isPremium) NexColors.Mint else NexColors.Muted,
                        fontSize = 14.sp,
                    )
                }
            }
            val blocked = prefs.blockedTotal
            if (blocked > 0) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(NexColors.Mint.copy(alpha = 0.14f))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        nt("blocked", NumberFormat.getIntegerInstance().format(blocked)),
                        color = NexColors.Mint,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            val perks = buildList {
                if (NexVpn.servers(LocalContext.current).isNotEmpty()) {
                    add(Perk(ShieldGlyph.Globe, nt("b_vpn_t"), nt("b_vpn_s"), false))
                }
                add(Perk(ShieldGlyph.Crown, nt("dark"), nt("b_dark_s"), true))
                add(Perk(ShieldGlyph.Globe, nt("lite"), nt("b_lite_s"), true))
                add(Perk(ShieldGlyph.Check, nt("hide"), nt("b_hide_s"), true))
                add(Perk(ShieldGlyph.Lock, nt("vault"), nt("b_vault_s"), true))
                add(Perk(ShieldGlyph.Slash, nt("fp"), nt("b_fp_s"), true))
                add(Perk(ShieldGlyph.Check, nt("wipe"), nt("b_wipe_s"), true))
                add(Perk(ShieldGlyph.Lock, nt("b_lock_t"), nt("b_lock_s"), false))
                add(Perk(ShieldGlyph.Slash, nt("b_incog_t"), nt("b_incog_s"), false))
                add(Perk(ShieldGlyph.Crown, nt("b_style_t"), nt("b_style_s"), false))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                perks.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { PerkTile(it, Modifier.weight(1f).fillMaxHeight()) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Text(
                nt("free_note"),
                color = NexColors.Mint,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            if (isPremium) {
                Column(Modifier.fillMaxWidth().nexCard(selected = true).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(nt("premium_active"), color = NexColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    if (NexPrefs.PREMIUM_FREE) Text(nt("tester_note"), color = NexColors.Muted, fontSize = 13.sp)
                    Text(nt("manage_sub"), color = NexColors.Muted, fontSize = 13.sp)
                }
            } else {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Plan.entries.forEach { plan ->
                        val price = when (plan) {
                            Plan.LIFETIME -> premium.lifetimePrice()?.let { nt("one_payment", it) }
                            else -> premium.planPrice(plan.planId)?.let { p ->
                                val per = if (plan == Plan.YEARLY) nt("per_year", p) else nt("per_month", p)
                                if (premium.hasTrial(plan.planId)) nt("trial_then", per) else per
                            }
                        } ?: nt("loading")
                        PlanCard(
                            nt(plan.key),
                            price,
                            plan == selected,
                            badge = if (plan == Plan.YEARLY) nt("best_value") else null,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) { selected = plan }
                    }
                }
            }
            LegalLinks(onOpen)
            Spacer(Modifier.height(4.dp))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(NexColors.Ink)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isPremium) {
                NexButton(nt("open_shield")) { onShield() }
                TextButton(onClick = onManage, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(nt("manage"), color = NexColors.Muted)
                }
            } else {
                val cta = when {
                    selected == Plan.LIFETIME -> nt("cta_life")
                    premium.hasTrial(selected.planId) -> nt("cta_trial")
                    else -> nt("cta_sub")
                }
                NexButton(cta, brush = NexColors.violetCta, textColor = Color.White) { onBuy(selected) }
                Text(
                    if (selected == Plan.LIFETIME) nt("life_note") else nt("renew_note"),
                    color = NexColors.Muted,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private class Perk(val glyph: ShieldGlyph, val title: String, val subtitle: String, val isNew: Boolean)

@Composable
private fun PerkTile(perk: Perk, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(NexColors.Card)
            .border(1.dp, if (perk.isNew) NexColors.Violet.copy(alpha = 0.55f) else NexColors.Line, RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShieldMark(24.dp, glyph = perk.glyph)
            Spacer(Modifier.width(8.dp))
            Text(perk.title, color = NexColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)
        }
        Text(perk.subtitle, color = NexColors.Muted, fontSize = 11.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun PlanCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    badge: String?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) NexColors.VioletSoft else NexColors.Card)
            .border(2.dp, if (isSelected) NexColors.Violet else NexColors.Line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (badge != null) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(NexColors.Gold)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(badge, color = NexColors.Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        Text(title, color = NexColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(subtitle, color = NexColors.Muted, fontSize = 11.sp, lineHeight = 14.sp)
    }
}
