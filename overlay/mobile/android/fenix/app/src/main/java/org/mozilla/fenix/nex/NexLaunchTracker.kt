/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.nex

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import org.mozilla.fenix.HomeActivity

/**
 * Counts app launches and shows the Premium paywall once, on the 3rd launch,
 * after the user has had time to see ads and trackers being blocked.
 */
object NexLaunchTracker : Application.ActivityLifecycleCallbacks {
    private const val PAYWALL_LAUNCH = 3
    private var countedThisProcess = false

    fun register(app: Application) = app.registerActivityLifecycleCallbacks(this)

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

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
