/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.nex

import android.content.Context
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.SearchAction
import mozilla.components.browser.state.action.TrackingProtectionAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store

/**
 * Counts every tracker the engine blocks, across all tabs, into [NexPrefs] (on-device only).
 * Powers the "X trackers blocked" stats and the paywall's proof-of-value line.
 */
class BlockedCounterMiddleware(context: Context) : Middleware<BrowserState, BrowserAction> {
    private val prefs = NexPrefs(context)

    override fun invoke(
        store: Store<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction,
    ) {
        if (action is TrackingProtectionAction.TrackerBlockedAction) {
            prefs.addBlocked()
        }
        next(action)
        if (action is SearchAction && !prefs.searchDefaultApplied) {
            applyPrivateSearchDefault(store)
        }
    }

    /**
     * First run only: make DuckDuckGo (no search tracking) the default engine once the engine list
     * has loaded. If the user later picks another engine we never touch it again.
     */
    private fun applyPrivateSearchDefault(store: Store<BrowserState, BrowserAction>) {
        val search = store.state.search
        if (!search.complete) return
        prefs.searchDefaultApplied = true
        val engine = search.regionSearchEngines.firstOrNull { it.name.equals(PREFERRED_ENGINE, ignoreCase = true) }
            ?: return
        store.dispatch(SearchAction.SelectSearchEngineAction(engine.id, engine.name))
    }

    private companion object {
        const val PREFERRED_ENGINE = "DuckDuckGo"
    }
}
