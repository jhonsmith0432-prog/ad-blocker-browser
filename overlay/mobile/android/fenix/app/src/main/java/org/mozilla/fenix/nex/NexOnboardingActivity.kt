/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.nex

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

/** Three welcome screens on first launch: ad blocking, privacy, Premium. */
class NexOnboardingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = NexPrefs(this)
        setContent {
            Welcome(
                onFinish = { showPremium ->
                    prefs.onboardingDone = true
                    if (showPremium) startActivity(Intent(this, PremiumActivity::class.java))
                    finish()
                },
            )
        }
    }
}

private data class Page(val glyph: ShieldGlyph, val title: String, val body: String)

@Composable
private fun Welcome(onFinish: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val pages = listOf(
        Page(ShieldGlyph.Slash, NexStrings.get(ctx, "ob1_title"), NexStrings.get(ctx, "ob1_body")),
        Page(ShieldGlyph.Lock, NexStrings.get(ctx, "ob2_title"), NexStrings.get(ctx, "ob2_body")),
        Page(ShieldGlyph.Crown, NexStrings.get(ctx, "ob3_title"), NexStrings.get(ctx, "ob3_body")),
    )
    var index by remember { mutableIntStateOf(0) }
    BackHandler(enabled = index > 0) { index-- }
    val last = index == pages.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexColors.screen)
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onFinish(false) }) { Text(NexStrings.get(ctx, "skip"), color = NexColors.Muted) }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Crossfade(targetState = index, label = "welcome") { i ->
                val page = pages[i]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ShieldMark(168.dp, glyph = page.glyph)
                    Spacer(Modifier.height(36.dp))
                    Text(
                        page.title,
                        color = NexColors.Text,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        lineHeight = 36.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        page.body,
                        color = NexColors.Muted,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 23.sp,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.Center) {
            pages.indices.forEach { i ->
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .height(8.dp)
                        .width(if (i == index) 26.dp else 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (i == index) NexColors.Mint else NexColors.Line),
                )
            }
        }
        if (last) {
            NexButton(NexStrings.get(ctx, "see_premium"), brush = NexColors.premium) { onFinish(true) }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { onFinish(false) }, modifier = Modifier.fillMaxWidth()) {
                Text(NexStrings.get(ctx, "start"), color = NexColors.Text, fontSize = 16.sp)
            }
        } else {
            NexButton(NexStrings.get(ctx, "next")) { index++ }
            Spacer(Modifier.size(48.dp))
        }
    }
}
