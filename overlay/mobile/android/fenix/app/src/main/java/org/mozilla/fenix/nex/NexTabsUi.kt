/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.nex

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import mozilla.components.browser.state.selector.selectedTab
import org.mozilla.fenix.R
import org.mozilla.fenix.components.menu.compose.MenuGroup
import org.mozilla.fenix.components.menu.compose.MenuItem
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.tabstray.redux.state.Page
import java.text.NumberFormat

private val PrivateViolet = Color(0xFF7C5CFF)

/** Tabs / Private (and Groups when enabled) segmented switch at the top of the tab manager. */
@Suppress("LongParameterList")
@Composable
fun NexTabSwitch(
    selectedPage: Page,
    normalTabCount: Int,
    privateTabCount: Int,
    showGroups: Boolean,
    tabGroupCount: Int,
    hasTabDataLoaded: Boolean,
    onPick: (Page) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainerHigh)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val count = { n: Int -> if (hasTabDataLoaded) " · $n" else "" }
        SwitchSegment(
            text = nt("tabs_label") + count(normalTabCount),
            selected = selectedPage == Page.NormalTabs,
            selectedBg = colors.primary,
            selectedFg = colors.onPrimary,
            modifier = Modifier.weight(1f),
        ) { onPick(Page.NormalTabs) }
        SwitchSegment(
            text = nt("private_label") + count(privateTabCount),
            selected = selectedPage == Page.PrivateTabs,
            selectedBg = PrivateViolet,
            selectedFg = Color.White,
            modifier = Modifier.weight(1f),
        ) { onPick(Page.PrivateTabs) }
        if (showGroups) {
            SwitchSegment(
                text = nt("groups_label") + count(tabGroupCount),
                selected = selectedPage == Page.TabGroups,
                selectedBg = colors.primary,
                selectedFg = colors.onPrimary,
                modifier = Modifier.weight(1f),
            ) { onPick(Page.TabGroups) }
        }
    }
}

@Composable
private fun SwitchSegment(
    text: String,
    selected: Boolean,
    selectedBg: Color,
    selectedFg: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) selectedBg else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Tab
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) selectedFg else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "14 blocked" pill drawn on a tab's thumbnail. Nothing is drawn when nothing was blocked. */
@Composable
fun NexBlockedBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier
            .padding(6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(NexColors.Ink.copy(alpha = 0.82f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(nt("blocked_short", count), color = NexColors.Mint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Home screen card: total blocked on this phone. Opens Privacy Shield. */
@Composable
fun NexHomeStatsCard() {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val blocked = NexPrefs(context).blockedTotal
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surfaceContainerHigh)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(22.dp))
            .clickable { context.startActivity(Intent(context, NexHubActivity::class.java)) }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShieldMark(40.dp, glyph = ShieldGlyph.Slash)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                NumberFormat.getIntegerInstance().format(blocked),
                color = colors.primary,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(nt("hub_blocked_label"), color = colors.onSurfaceVariant, fontSize = 13.sp)
        }
        Text("›", color = colors.onSurfaceVariant, fontSize = 26.sp)
    }
}

/** Top of the main menu: Privacy Shield (with this page's blocked count) and Premium. */
@Composable
fun NexMenuEntries() {
    val context = LocalContext.current
    val onPage = context.components.core.store.state.selectedTab
        ?.trackingProtection?.blockedTrackers?.size ?: 0
    val isPremium = NexPrefs(context).isPremium
    MenuGroup {
        MenuItem(
            label = nt("hub_title"),
            beforeIconPainter = painterResource(R.drawable.nex_ic_shield_24),
            description = if (onPage > 0) nt("menu_shield_page", onPage) else nt("hub_on"),
            onClick = { open(context, Intent(context, NexHubActivity::class.java)) },
        )
        MenuItem(
            label = if (isPremium) nt("premium_active") else nt("get_premium"),
            beforeIconPainter = painterResource(R.drawable.nex_ic_crown_24),
            description = if (isPremium) null else nt("upsell_sub"),
            onClick = { open(context, Intent(context, PremiumActivity::class.java)) },
        )
    }
}

private fun open(context: Context, intent: Intent) {
    (context as? FragmentActivity)?.supportFragmentManager?.fragments?.forEach { host ->
        host.childFragmentManager.fragments.filterIsInstance<DialogFragment>().forEach { it.dismissAllowingStateLoss() }
    }
    context.startActivity(intent)
}

