/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.nex

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mozilla.components.compose.base.theme.NexAccent

/** Nexcraft browser palette (matches the launcher icon and the store listing). */
object NexColors {
    val Ink = Color(0xFF070A16)
    val Navy = Color(0xFF0E1328)
    val Card = Color(0xFF151B36)
    val CardHi = Color(0xFF1C2447)
    val Line = Color(0xFF283260)
    // Mint and Teal follow the Premium accent colour (Compose state, so screens recolour live).
    val Mint: Color get() = NexAccent.darkPrimary
    val Teal: Color get() = NexAccent.deep
    val Violet = Color(0xFF7C5CFF)
    val Gold = Color(0xFFFFC85A)
    val Text = Color(0xFFF4F6FF)
    val Muted = Color(0xFFA3A9C7)

    val screen: Brush = Brush.verticalGradient(listOf(Color(0xFF111A3A), Ink, Ink))
    val cta: Brush get() = Brush.horizontalGradient(listOf(Mint, Teal))
    val premium: Brush get() = Brush.linearGradient(listOf(Violet, Color(0xFF3A7BFF), Teal))
    val violetCta: Brush = Brush.horizontalGradient(listOf(Violet, Color(0xFF5B3DF5)))
    val VioletSoft = Color(0xFF1E1844)
    val Lilac = Color(0xFFC3B4FF)
}

/** The app's shield mark, drawn in code so it stays crisp at any size. */
@Composable
fun ShieldMark(size: Dp, modifier: Modifier = Modifier, glyph: ShieldGlyph = ShieldGlyph.Check, tint: Brush? = null) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val shield = Path().apply {
            moveTo(w * 0.5f, h * 0.04f)
            cubicTo(w * 0.68f, h * 0.15f, w * 0.84f, h * 0.17f, w * 0.94f, h * 0.17f)
            cubicTo(w * 0.94f, h * 0.55f, w * 0.80f, h * 0.82f, w * 0.5f, h * 0.97f)
            cubicTo(w * 0.20f, h * 0.82f, w * 0.06f, h * 0.55f, w * 0.06f, h * 0.17f)
            cubicTo(w * 0.16f, h * 0.17f, w * 0.32f, h * 0.15f, w * 0.5f, h * 0.04f)
            close()
        }
        // soft glow
        drawCircle(
            brush = Brush.radialGradient(
                listOf(NexColors.Mint.copy(alpha = 0.35f), Color.Transparent),
                center = Offset(w / 2, h / 2),
                radius = w * 0.75f,
            ),
            radius = w * 0.75f,
            center = Offset(w / 2, h / 2),
        )
        drawPath(shield, tint ?: Brush.verticalGradient(listOf(NexColors.Mint, NexColors.Teal), 0f, h))
        val stroke = Stroke(width = w * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val ink = NexColors.Ink
        when (glyph) {
            ShieldGlyph.Check -> drawPath(
                Path().apply {
                    moveTo(w * 0.30f, h * 0.52f)
                    lineTo(w * 0.45f, h * 0.66f)
                    lineTo(w * 0.72f, h * 0.38f)
                },
                ink, style = stroke,
            )
            ShieldGlyph.Slash -> {
                drawCircle(ink, radius = w * 0.2f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
                drawLine(ink, Offset(w * 0.36f, h * 0.64f), Offset(w * 0.64f, h * 0.36f), strokeWidth = w * 0.085f, cap = StrokeCap.Round)
            }
            ShieldGlyph.Lock -> {
                drawRoundRect(
                    ink,
                    topLeft = Offset(w * 0.33f, h * 0.46f),
                    size = androidx.compose.ui.geometry.Size(w * 0.34f, h * 0.26f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.05f),
                )
                drawArc(
                    ink, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(w * 0.39f, h * 0.30f),
                    size = androidx.compose.ui.geometry.Size(w * 0.22f, h * 0.30f),
                    style = Stroke(width = w * 0.07f, cap = StrokeCap.Round),
                )
            }
            ShieldGlyph.Crown -> drawPath(
                Path().apply {
                    moveTo(w * 0.28f, h * 0.66f)
                    lineTo(w * 0.30f, h * 0.38f)
                    lineTo(w * 0.42f, h * 0.52f)
                    lineTo(w * 0.5f, h * 0.32f)
                    lineTo(w * 0.58f, h * 0.52f)
                    lineTo(w * 0.70f, h * 0.38f)
                    lineTo(w * 0.72f, h * 0.66f)
                    close()
                },
                ink,
            )
            ShieldGlyph.Globe -> {
                drawCircle(ink, radius = w * 0.21f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(w * 0.06f))
                drawLine(ink, Offset(w * 0.29f, h * 0.5f), Offset(w * 0.71f, h * 0.5f), strokeWidth = w * 0.05f)
                drawOval(
                    ink,
                    topLeft = Offset(w * 0.41f, h * 0.29f),
                    size = androidx.compose.ui.geometry.Size(w * 0.18f, h * 0.42f),
                    style = Stroke(w * 0.05f),
                )
            }
        }
    }
}

enum class ShieldGlyph { Check, Slash, Lock, Crown, Globe }

/** Full-width gradient call-to-action button. */
@Composable
fun NexButton(
    text: String,
    enabled: Boolean = true,
    brush: Brush = NexColors.cta,
    textColor: Color = NexColors.Ink,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) brush else Brush.linearGradient(listOf(NexColors.Line, NexColors.Line)))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) textColor else NexColors.Muted, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

/** Rounded card surface used across our screens. */
fun Modifier.nexCard(selected: Boolean = false): Modifier = this
    .clip(RoundedCornerShape(20.dp))
    .background(if (selected) NexColors.CardHi else NexColors.Card)
    .border(if (selected) 2.dp else 1.dp, if (selected) NexColors.Mint else NexColors.Line, RoundedCornerShape(20.dp))
