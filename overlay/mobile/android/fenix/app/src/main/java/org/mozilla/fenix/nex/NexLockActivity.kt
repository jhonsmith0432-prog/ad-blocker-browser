/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.nex

import android.content.Context
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Premium App lock state. Locks again whenever the whole app goes to the background. */
object NexLock {
    const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    @Volatile
    var unlocked = false

    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
}

/** Full-screen cover shown over the browser until the user unlocks with fingerprint or screen-lock PIN. */
class NexLockActivity : FragmentActivity() {
    private var prompting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    moveTaskToBack(true)
                }
            },
        )
        setContent {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NexColors.screen)
                    .padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ShieldMark(132.dp, glyph = ShieldGlyph.Lock)
                Spacer(Modifier.height(28.dp))
                Text(
                    NexStrings.get(this@NexLockActivity, "unlock_title"),
                    color = NexColors.Text,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                NexButton(NexStrings.get(this@NexLockActivity, "unlock")) { prompt() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        prompt()
    }

    private fun prompt() {
        if (NexLock.unlocked || !NexLock.canAuthenticate(this)) {
            NexLock.unlocked = true
            finish()
            return
        }
        if (prompting) return
        prompting = true
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                prompting = false
                NexLock.unlocked = true
                finish()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                prompting = false
            }
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(NexStrings.get(this, "unlock_title"))
            .setAllowedAuthenticators(NexLock.AUTHENTICATORS)
            .build()
        BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback).authenticate(info)
    }
}
