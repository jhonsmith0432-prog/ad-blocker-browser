/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.nex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mozilla.fenix.ext.components

private val Ink = Color(0xFF0A0D1C)
private val Surface = Color(0xFF141832)
private val Line = Color(0xFF232A52)
private val Mint = Color(0xFF3DF5B0)
private val Violet = Color(0xFF7C5CFF)
private val TextMain = Color(0xFFF4F6FF)
private val TextMuted = Color(0xFFA3A9C7)

/** Premium paywall with 7-day free trial (yearly/monthly) and lifetime, plus the user's blocked count. */
class PremiumActivity : ComponentActivity() {
    private lateinit var premium: PremiumManager
    private var version by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        premium = PremiumManager(this)
        premium.onChanged = { runOnUiThread { version++ } }
        premium.connect()
        val prefs = NexPrefs(this)
        val blocked = prefs.blockedTotal
        setContent {
            @Suppress("UNUSED_EXPRESSION")
            version // recompose when Play data arrives
            Paywall(
                blocked = blocked,
                premium = premium,
                onBuy = { plan ->
                    if (plan == Plan.LIFETIME) {
                        premium.buyLifetime(this)
                    } else {
                        premium.buySubscription(this, plan.planId)
                    }
                },
                onRestore = { premium.restore() },
                onClose = { finish() },
                alwaysIncognito = prefs.alwaysIncognito,
                onAlwaysIncognito = { on ->
                    prefs.alwaysIncognito = on
                    // Links opened from other apps also go to private tabs while this is on.
                    components.settings.openLinksInAPrivateTab = on
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

enum class Plan(val planId: String, val title: String) {
    YEARLY(PremiumManager.PLAN_YEARLY, "Yearly"),
    MONTHLY(PremiumManager.PLAN_MONTHLY, "Monthly"),
    LIFETIME(PremiumManager.LIFETIME_ID, "Lifetime"),
}

@Composable
private fun Paywall(
    blocked: Long,
    premium: PremiumManager,
    onBuy: (Plan) -> Unit,
    onRestore: () -> Unit,
    onClose: () -> Unit,
    alwaysIncognito: Boolean,
    onAlwaysIncognito: (Boolean) -> Unit,
) {
    var selected by remember { mutableStateOf(Plan.YEARLY) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClose) { Text("Not now", color = TextMuted) }
        }
        Text("Premium", color = TextMain, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        if (premium.isPremium) {
            Text("You're Premium. Thank you!", color = Mint, fontSize = 16.sp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Always incognito", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Open every session and link in private mode", color = TextMuted, fontSize = 13.sp)
                }
                Switch(
                    checked = alwaysIncognito,
                    onCheckedChange = onAlwaysIncognito,
                    colors = SwitchDefaults.colors(checkedTrackColor = Mint, checkedThumbColor = Ink),
                )
            }
        }
        if (blocked > 0) {
            Text(
                "%,d trackers blocked on this phone so far".format(blocked),
                color = Mint,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text("Total protection. Still zero data collected.", color = TextMuted, fontSize = 15.sp)
        listOf(
            "Always incognito: every session private",
            "Stronger fingerprint protection",
            "Custom filters and per-site rules",
            "Support an independent, no-tracking browser",
        ).forEach { Text("✓  $it", color = TextMain, fontSize = 15.sp) }

        Spacer(Modifier.height(4.dp))
        Plan.entries.forEach { plan ->
            val price = when (plan) {
                Plan.LIFETIME -> premium.lifetimePrice()?.let { "One payment of $it" }
                else -> premium.planPrice(plan.planId)?.let { p ->
                    val per = if (plan == Plan.YEARLY) "year" else "month"
                    if (premium.hasTrial(plan.planId)) "7 days free, then $p/$per" else "$p/$per"
                }
            } ?: "Loading price…"
            PlanRow(plan.title, price, plan == selected) { selected = plan }
        }

        val cta = when {
            selected == Plan.LIFETIME -> "Buy lifetime"
            premium.hasTrial(selected.planId) -> "Start 7-day free trial"
            else -> "Subscribe"
        }
        Button(
            onClick = { onBuy(selected) },
            enabled = !premium.isPremium,
            colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text(cta, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
        Text(
            if (selected == Plan.LIFETIME) {
                "One-time purchase via Google Play."
            } else {
                "Cancel anytime in Google Play. No charge during the trial."
            },
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = onRestore, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Restore purchase", color = TextMuted)
        }
    }
}

@Composable
private fun PlanRow(title: String, subtitle: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(18.dp))
            .border(2.dp, if (isSelected) Violet else Line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextMuted, fontSize = 13.sp)
        }
    }
}
