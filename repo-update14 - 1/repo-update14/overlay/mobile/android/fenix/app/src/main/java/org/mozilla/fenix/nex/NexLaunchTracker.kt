/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.nex

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import mozilla.components.compose.base.theme.NexAccent
import org.mozilla.fenix.HomeActivity

/**
 * Counts app launches and shows the Premium paywall once, on the 3rd launch,
 * after the user has had time to see ads and trackers being blocked.
 * Also shows the Premium App lock whenever the app comes back from the background.
 */
object NexLaunchTracker : Application.ActivityLifecycleCallbacks {
    private const val PAYWALL_LAUNCH = 3
    private var countedThisProcess = false
    private var startedActivities = 0

    fun register(app: Application) {
        // Premium accent colour; falls back to Mint when Premium is not active.
        val prefs = NexPrefs(app)
        NexAccent.index = if (prefs.isPremium) prefs.accent else 0
        app.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity !is HomeActivity || countedThisProcess || savedInstanceState != null) return
        countedThisProcess = true
        val prefs = NexPrefs(activity)
        prefs.launchCount = prefs.launchCount + 1
        if (!prefs.onboardingDone) {
            activity.startActivity(Intent(activity, NexOnboardingActivity::class.java))
            return
        }
        if (prefs.launchCount >= PAYWALL_LAUNCH && !prefs.paywallShown && !prefs.isPremium) {
            prefs.paywallShown = true
            activity.startActivity(Intent(activity, PremiumActivity::class.java))
        }
    }

    override fun onActivityStarted(activity: Activity) {
        if (startedActivities == 0) NexWipe.onReturn(activity)
        startedActivities++
        if (activity is NexLockActivity || NexLock.unlocked) return
        if (NexPrefs(activity).appLockActive) {
            activity.startActivity(Intent(activity, NexLockActivity::class.java))
        }
    }

    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        if (startedActivities == 0 && !activity.isChangingConfigurations) {
            NexLock.unlocked = false
            NexWipe.onLeave(activity)
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
