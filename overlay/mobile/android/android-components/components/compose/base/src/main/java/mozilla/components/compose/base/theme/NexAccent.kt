/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.compose.base.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Premium accent colour. The Acorn colour schemes read it, so every Compose screen recolours
 * the moment [index] changes (it is Compose state).
 */
object NexAccent {
    var index by mutableIntStateOf(0)

    // dark primary, on primary, container, on container | light primary, container, on container,
    // secondary container | deep (gradient end)
    private val TABLE = arrayOf(
        longArrayOf(0xFF3DF5B0, 0xFF04261A, 0xFF0E5C46, 0xFFD8FFF0, 0xFF00876A, 0xFFC6F6E4, 0xFF00382B, 0xFFE3F5EF, 0xFF19C6A0),
        longArrayOf(0xFF5AB4FF, 0xFF00243F, 0xFF0F4A7A, 0xFFD6ECFF, 0xFF1565C0, 0xFFD3E6FF, 0xFF0A2E5C, 0xFFE3EEFB, 0xFF2F7BFF),
        longArrayOf(0xFFB39DFF, 0xFF1E0F5C, 0xFF4A3A9E, 0xFFECE6FF, 0xFF5B3DF5, 0xFFE3DCFF, 0xFF24166B, 0xFFECE8FB, 0xFF7C5CFF),
        longArrayOf(0xFFFF6B88, 0xFF3D0614, 0xFF8A1F3A, 0xFFFFE0E7, 0xFFC2185B, 0xFFFFD9E3, 0xFF5C0A28, 0xFFFBE7EE, 0xFFE0245E),
        longArrayOf(0xFFFFC85A, 0xFF2E1F00, 0xFF6B4D00, 0xFFFFF0CC, 0xFF8A5F00, 0xFFFFE7B0, 0xFF3D2A00, 0xFFFBF1DC, 0xFFFF9F1A),
        longArrayOf(0xFFFF8F5A, 0xFF3A1300, 0xFF8A3A12, 0xFFFFE3D6, 0xFFC2410C, 0xFFFFDCCB, 0xFF5C1E04, 0xFFFBEAE1, 0xFFFF5A36),
    )

    /** Number of accents: Mint, Ocean, Violet, Ruby, Gold, Sunset. */
    val count: Int get() = TABLE.size

    /** Swatch shown in the picker for accent [i]. */
    fun swatch(i: Int): Color = Color(TABLE[i.coerceIn(0, TABLE.size - 1)][0])

    private fun c(k: Int): Color = Color(TABLE[index.coerceIn(0, TABLE.size - 1)][k])

    val darkPrimary: Color get() = c(0)
    val darkOnPrimary: Color get() = c(1)
    val darkContainer: Color get() = c(2)
    val darkOnContainer: Color get() = c(3)
    val lightPrimary: Color get() = c(4)
    val lightContainer: Color get() = c(5)
    val lightOnContainer: Color get() = c(6)
    val lightSecondaryContainer: Color get() = c(7)
    val deep: Color get() = c(8)
}
