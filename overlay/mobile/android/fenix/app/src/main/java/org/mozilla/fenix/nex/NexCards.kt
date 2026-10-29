/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.nex

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mozilla.components.compose.base.theme.NexAccent
import org.mozilla.fenix.iconpicker.AppIcon
import org.mozilla.fenix.iconpicker.DefaultAppIconRepository
import org.mozilla.fenix.iconpicker.DefaultPackageManagerWrapper
import org.mozilla.fenix.iconpicker.IconBackground

const val TERMS_URL = "https://nexcraftgames.com/browser/terms"
const val REFUNDS_URL = "https://nexcraftgames.com/browser/refunds"
const val PRIVACY_URL = "https://nexcraftgames.com/browser/privacy"
const val MANAGE_SUBS_URL =
    "https://play.google.com/store/account/subscriptions?sku=premium&package=com.nexcraft.browser"

@Composable
fun nt(key: String, vararg args: Any): String = NexStrings.get(LocalContext.current, key, *args)

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = NexColors.Muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
fun LockChip() {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(NexColors.Gold.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShieldMark(14.dp, glyph = ShieldGlyph.Lock)
        Spacer(Modifier.width(5.dp))
        Text(nt("locked"), color = NexColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** A setting row. When [locked], the switch is replaced by a Premium chip and the whole row opens the paywall. */
@Composable
fun ToggleCard(
    glyph: ShieldGlyph,
    title: String,
    subtitle: String,
    checked: Boolean,
    locked: Boolean,
    onLocked: () -> Unit,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .nexCard()
            .clickable { if (locked) onLocked() else onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShieldMark(34.dp, glyph = glyph)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = NexColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = NexColors.Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.width(10.dp))
        if (locked) {
            LockChip()
        } else {
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(checkedTrackColor = NexColors.Mint, checkedThumbColor = NexColors.Ink),
            )
        }
    }
}

@Composable
fun VpnCard(
    context: Context,
    prefs: NexPrefs,
    locked: Boolean,
    onLocked: () -> Unit,
    onVpn: (Boolean) -> Unit,
    onVpnLocation: (String) -> Unit,
) {
    val servers = NexVpn.servers(context)
    val current = NexVpn.selected(context)
    val on = !locked && prefs.vpnEnabled && servers.isNotEmpty()
    Column(
        Modifier
            .fillMaxWidth()
            .nexCard(selected = on)
            .clickable(enabled = locked) { onLocked() }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShieldMark(34.dp, glyph = ShieldGlyph.Globe)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(nt("vpn"), color = NexColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        servers.isEmpty() -> nt("vpn_soon")
                        on && current != null -> nt("vpn_on", "${current.flag} ${current.name}".trim())
                        else -> nt("vpn_off")
                    },
                    color = if (on) NexColors.Mint else NexColors.Muted,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.width(10.dp))
            if (locked) {
                LockChip()
            } else {
                Switch(
                    checked = on,
                    enabled = servers.isNotEmpty(),
                    onCheckedChange = onVpn,
                    colors = SwitchDefaults.colors(checkedTrackColor = NexColors.Mint, checkedThumbColor = NexColors.Ink),
                )
            }
        }
        if (!locked && servers.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                servers.forEach { s ->
                    val picked = s.id == current?.id
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (picked) NexColors.Mint else NexColors.CardHi)
                            .clickable { onVpnLocation(s.id) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            "${s.flag} ${s.name}".trim(),
                            color = if (picked) NexColors.Ink else NexColors.Text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        if (!locked && servers.isNotEmpty()) {
            Text(nt("vpn_note"), color = NexColors.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
fun PersonalizeCard(locked: Boolean, onLocked: () -> Unit, onIcons: () -> Unit, onAccent: (Int) -> Unit) {
    val context = LocalContext.current
    val icon = DefaultAppIconRepository(
        packageManager = DefaultPackageManagerWrapper(context.packageManager),
        packageName = context.packageName,
    ).selectedAppIcon
    Column(Modifier.fillMaxWidth().nexCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { if (locked) onLocked() else onIcons() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconPreview(icon)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(nt("icons"), color = NexColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(nt("icons_sub"), color = NexColors.Muted, fontSize = 13.sp)
            }
            Spacer(Modifier.width(10.dp))
            if (locked) LockChip() else Text("›", color = NexColors.Muted, fontSize = 26.sp)
        }
        Column {
            Text(nt("accent"), color = NexColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(nt("accent_sub"), color = NexColors.Muted, fontSize = 13.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (i in 0 until NexAccent.count) {
                val picked = i == NexAccent.index
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(NexAccent.swatch(i))
                        .border(if (picked) 3.dp else 0.dp, if (picked) NexColors.Text else NexAccent.swatch(i), CircleShape)
                        .clickable { if (locked) onLocked() else onAccent(i) },
                )
            }
        }
    }
}

@Composable
private fun IconPreview(icon: AppIcon) {
    Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))) {
        when (val bg = icon.iconBackground) {
            is IconBackground.Color -> Box(Modifier.fillMaxSize().background(colorResource(bg.colorResId)))
            is IconBackground.Drawable -> Image(painterResource(bg.drawableResId), null, Modifier.fillMaxSize())
        }
        Image(painterResource(icon.iconForegroundId), null, Modifier.fillMaxSize())
    }
}

/** Shield Credits (support codes), folded away until the user asks for it. */
@Composable
fun CreditsCard(prefs: NexPrefs, onRedeem: (String) -> Pair<Boolean, String>) {
    val days = ShieldCredits.daysLeft(prefs)
    var open by remember { mutableStateOf(days > 0) }
    Column(
        modifier = Modifier.fillMaxWidth().nexCard().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(nt("have_code"), color = NexColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (days > 0) Text(nt("credits_left", days), color = NexColors.Mint, fontSize = 13.sp)
            }
            Text(if (open) "–" else "+", color = NexColors.Muted, fontSize = 22.sp)
        }
        if (open) {
            var code by remember { mutableStateOf("") }
            var message by remember { mutableStateOf("") }
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                singleLine = true,
                placeholder = { Text(nt("credit_hint"), color = NexColors.Muted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = NexColors.Text,
                    unfocusedTextColor = NexColors.Text,
                    focusedBorderColor = NexColors.Mint,
                    unfocusedBorderColor = NexColors.Line,
                    cursorColor = NexColors.Mint,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    val (ok, text) = onRedeem(code)
                    message = text
                    if (ok) code = ""
                }) { Text(nt("redeem"), color = NexColors.Mint, fontWeight = FontWeight.Bold) }
                if (message.isNotEmpty()) Text(message, color = NexColors.Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun LegalLinks(onOpen: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        TextButton(onClick = { onOpen(TERMS_URL) }) { Text(nt("terms"), color = NexColors.Muted, fontSize = 12.sp) }
        TextButton(onClick = { onOpen(REFUNDS_URL) }) { Text(nt("refunds"), color = NexColors.Muted, fontSize = 12.sp) }
        TextButton(onClick = { onOpen(PRIVACY_URL) }) { Text(nt("privacy"), color = NexColors.Muted, fontSize = 12.sp) }
    }
}

/** Round close button used at the top-left of our full-screen pages. */
@Composable
fun CloseButton(onClose: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(NexColors.Card)
            .border(1.dp, NexColors.Line, CircleShape)
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Text("✕", color = NexColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/** Gold-violet "Premium" pill; shows a check once Premium is active. */
@Composable
fun PremiumPill(isPremium: Boolean, short: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(NexColors.premium)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShieldMark(22.dp, glyph = if (isPremium) ShieldGlyph.Check else ShieldGlyph.Crown)
        Spacer(Modifier.width(6.dp))
        Text(
            if (isPremium || short) nt("premium") else nt("get_premium"),
            color = NexColors.Text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
