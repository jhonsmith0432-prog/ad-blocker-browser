/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.nex

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Privacy Shield rows for anti-fingerprinting, auto-wipe and the Vault. */
@Composable
fun PrivacyPlusCards(prefs: NexPrefs, locked: Boolean, onPremium: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    ToggleCard(ShieldGlyph.Slash, nt("fp"), nt("fp_sub"), prefs.strongFingerprint, locked, onPremium) {
        prefs.strongFingerprint = it
        NexPlus.push()
        onChanged()
    }
    WipeCard(prefs, locked, onPremium, onChanged)
    Row(
        Modifier
            .fillMaxWidth()
            .nexCard()
            .clickable {
                if (locked) onPremium() else context.startActivity(Intent(context, NexVaultActivity::class.java))
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShieldMark(34.dp, glyph = ShieldGlyph.Lock)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(nt("vault"), color = NexColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(nt("vault_sub"), color = NexColors.Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.width(10.dp))
        if (locked) LockChip() else Text("›", color = NexColors.Muted, fontSize = 26.sp)
    }
}

/** Privacy Shield rows for Dark mode, Data saver and Hide anything you tap. */
@Composable
fun PagePlusCards(prefs: NexPrefs, locked: Boolean, onPremium: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    ToggleCard(ShieldGlyph.Crown, nt("dark"), nt("dark_sub"), prefs.darkMode, locked, onPremium) {
        prefs.darkMode = it
        NexPlus.push()
        onChanged()
    }
    val mb = prefs.liteSavedKb / KB_PER_MB
    ToggleCard(
        ShieldGlyph.Globe,
        nt("lite"),
        if (prefs.dataSaver && mb > 0) nt("lite_saved", mb) else nt("lite_sub"),
        prefs.dataSaver,
        locked,
        onPremium,
    ) {
        prefs.dataSaver = it
        NexPlus.push()
        onChanged()
    }
    ToggleRowFlat(prefs, locked, onPremium, onChanged)
    if (!locked && prefs.hiddenRules > 0) {
        TextButton(
            onClick = {
                NexPlus.clearHiddenRules(context)
                onChanged()
            },
        ) { Text(nt("hide_clear"), color = NexColors.Gold, fontSize = 13.sp) }
    }
}

@Composable
private fun ToggleRowFlat(prefs: NexPrefs, locked: Boolean, onPremium: () -> Unit, onChanged: () -> Unit) {
    ToggleCard(
        ShieldGlyph.Check,
        nt("hide"),
        if (prefs.hiddenRules > 0) nt("hide_count", prefs.hiddenRules) else nt("hide_sub"),
        prefs.hideItems,
        locked,
        onPremium,
    ) {
        prefs.hideItems = it
        NexPlus.push()
        onChanged()
    }
}

@Composable
private fun WipeCard(prefs: NexPrefs, locked: Boolean, onPremium: () -> Unit, onChanged: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().nexCard(selected = !locked && prefs.wipeAfter > 0).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShieldMark(34.dp, glyph = ShieldGlyph.Check)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(nt("wipe"), color = NexColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(nt("wipe_sub"), color = NexColors.Muted, fontSize = 13.sp)
            }
            if (locked) {
                Spacer(Modifier.width(10.dp))
                LockChip()
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NexWipe.CHOICES.forEach { minutes ->
                val picked = !locked && prefs.wipeAfter == minutes
                val label = when (minutes) {
                    0 -> nt("wipe_off")
                    NexPrefs.WIPE_NOW -> nt("wipe_now")
                    else -> nt("wipe_min", minutes)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (picked) NexColors.Mint else NexColors.CardHi)
                        .border(1.dp, if (picked) NexColors.Mint else NexColors.Line, RoundedCornerShape(50))
                        .clickable {
                            if (locked) {
                                onPremium()
                            } else {
                                prefs.wipeAfter = minutes
                                onChanged()
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        label,
                        color = if (picked) NexColors.Ink else NexColors.Text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

private const val KB_PER_MB = 1024L
