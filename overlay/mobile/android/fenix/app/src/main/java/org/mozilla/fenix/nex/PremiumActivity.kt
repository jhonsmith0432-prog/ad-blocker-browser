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
import androidx.compose.foundation.shape.CircleShape
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
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ShieldMark(88.dp, glyph = ShieldGlyph.Crown)
                Spacer(Modifier.height(10.dp))
                Text(nt("premium"), color = NexColors.Text, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isPremium) nt("is_premium") else nt("tagline"),
                    color = if (isPremium) NexColors.Mint else NexColors.Muted,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
                val blocked = prefs.blockedTotal
                if (blocked > 0) {
                    Spacer(Modifier.height(10.dp))
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
            }

            Column(Modifier.fillMaxWidth().nexCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (NexVpn.servers(LocalContext.current).isNotEmpty()) {
                    Benefit(ShieldGlyph.Globe, nt("b_vpn_t"), nt("b_vpn_s"))
                }
                Benefit(ShieldGlyph.Lock, nt("b_lock_t"), nt("b_lock_s"))
                Benefit(ShieldGlyph.Slash, nt("b_incog_t"), nt("b_incog_s"))
                Benefit(ShieldGlyph.Crown, nt("b_style_t"), nt("b_style_s"))
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
                Plan.entries.forEach { plan ->
                    val price = when (plan) {
                        Plan.LIFETIME -> premium.lifetimePrice()?.let { nt("one_payment", it) }
                        else -> premium.planPrice(plan.planId)?.let { p ->
                            val per = if (plan == Plan.YEARLY) nt("per_year", p) else nt("per_month", p)
                            if (premium.hasTrial(plan.planId)) nt("trial_then", per) else per
                        }
                    } ?: nt("loading")
                    PlanCard(nt(plan.key), price, plan == selected, badge = if (plan == Plan.YEARLY) nt("best_value") else null) {
                        selected = plan
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
                NexButton(cta) { onBuy(selected) }
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

@Composable
private fun Benefit(glyph: ShieldGlyph, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ShieldMark(34.dp, glyph = glyph)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = NexColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = NexColors.Muted, fontSize = 13.sp)
        }
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
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Box(Modifier.size(8.dp).clip(CircleShape).background(NexColors.Ink))
            }
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
