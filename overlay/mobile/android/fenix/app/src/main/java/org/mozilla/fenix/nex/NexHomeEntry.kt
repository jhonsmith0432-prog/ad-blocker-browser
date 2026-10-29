/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.nex

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Home screen buttons: the Premium pill (paywall) and the shield (Privacy Shield). */
@Composable
fun NexHomeEntries() {
    val context = LocalContext.current
    val isPremium = NexPrefs(context).isPremium
    val shieldLabel = nt("hub_title")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        PremiumPill(isPremium) { context.startActivity(Intent(context, PremiumActivity::class.java)) }
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(NexColors.Navy)
                .border(1.dp, NexColors.Line, CircleShape)
                .clickable { context.startActivity(Intent(context, NexHubActivity::class.java)) }
                .semantics { contentDescription = shieldLabel },
            contentAlignment = Alignment.Center,
        ) {
            ShieldMark(26.dp, glyph = ShieldGlyph.Check)
        }
    }
}
