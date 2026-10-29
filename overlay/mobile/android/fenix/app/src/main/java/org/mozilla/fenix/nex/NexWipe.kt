/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.nex

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.RecentlyClosedAction
import mozilla.components.concept.engine.Engine
import org.mozilla.fenix.ext.components

/**
 * Premium auto-wipe: closes every tab and clears history, cookies, site data and cache
 * as soon as the app is left, or when it is opened again after the chosen number of minutes away.
 */
object NexWipe {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Choices offered in Privacy Shield: off, as soon as you leave, after 5 or 30 minutes. */
    val CHOICES = listOf(0, NexPrefs.WIPE_NOW, 5, 30)

    fun onLeave(context: Context) {
        val prefs = NexPrefs(context)
        prefs.leftAt = System.currentTimeMillis()
        if (prefs.isPremium && prefs.wipeAfter == NexPrefs.WIPE_NOW) wipe(context)
    }

    fun onReturn(context: Context) {
        val prefs = NexPrefs(context)
        val minutes = prefs.wipeAfter
        if (!prefs.isPremium || minutes <= NexPrefs.WIPE_NOW || prefs.leftAt == 0L) return
        if (System.currentTimeMillis() - prefs.leftAt >= minutes * MINUTE) wipe(context)
    }

    fun wipe(context: Context) {
        val components = context.applicationContext.components
        components.useCases.tabsUseCases.removeAllTabs(false)
        components.core.store.dispatch(RecentlyClosedAction.RemoveAllClosedTabAction)
        components.core.engine.clearData(Engine.BrowsingData.all())
        scope.launch(Dispatchers.IO) {
            components.core.historyStorage.deleteEverything()
        }
    }

    private const val MINUTE = 60_000L
}
