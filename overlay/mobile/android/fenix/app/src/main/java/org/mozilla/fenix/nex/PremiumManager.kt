/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.nex

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Google Play Billing for Premium. Products to create in Play Console:
 *  - Subscription  [SUB_ID] with base plans [PLAN_MONTHLY] and [PLAN_YEARLY], each with a 3-day free-trial offer.
 *  - One-time product [LIFETIME_ID].
 * Entitlement is cached locally in [NexPrefs]; Play is the source of truth and is re-checked on every start.
 */
class PremiumManager(context: Context) : PurchasesUpdatedListener {
    private val prefs = NexPrefs(context)
    private val client: BillingClient =
        BillingClient.newBuilder(context.applicationContext)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()

    var subscription: ProductDetails? = null
        private set
    var lifetime: ProductDetails? = null
        private set

    /** Called when product details or entitlement change, so the UI can refresh. */
    var onChanged: () -> Unit = {}

    val isPremium: Boolean get() = prefs.isPremium

    fun connect() {
        if (client.isReady) {
            refresh()
            return
        }
        client.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) refresh()
                }

                override fun onBillingServiceDisconnected() = Unit
            },
        )
    }

    fun end() = client.endConnection()

    private fun refresh() {
        loadProducts()
        restore()
    }

    private fun loadProducts() {
        val sub = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(SUB_ID).setProductType(BillingClient.ProductType.SUBS).build()
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(sub)).build(),
        ) { _, result ->
            subscription = result.productDetailsList.firstOrNull()
            onChanged()
        }
        val life = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(LIFETIME_ID).setProductType(BillingClient.ProductType.INAPP).build()
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(life)).build(),
        ) { _, result ->
            lifetime = result.productDetailsList.firstOrNull()
            onChanged()
        }
    }

    /** Re-reads owned purchases from Play (also used by the "Restore purchase" button). */
    fun restore() {
        var subOwned = false
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        ) { _, subs ->
            subOwned = subs.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            subs.forEach(::acknowledge)
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
            ) { _, inapp ->
                val lifeOwned = inapp.any {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED && LIFETIME_ID in it.products
                }
                inapp.forEach(::acknowledge)
                prefs.isPremium = subOwned || lifeOwned
                onChanged()
            }
        }
    }

    /** Price text for a subscription base plan, e.g. "Rs 1,500/year", or null until loaded. */
    fun planPrice(planId: String): String? =
        offerFor(planId)?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice

    /** True when the user is still eligible for the free-trial offer on this plan. */
    fun hasTrial(planId: String): Boolean =
        offerFor(planId)?.pricingPhases?.pricingPhaseList?.any { it.priceAmountMicros == 0L } == true

    fun lifetimePrice(): String? = lifetime?.oneTimePurchaseOfferDetails?.formattedPrice

    private fun offerFor(planId: String): ProductDetails.SubscriptionOfferDetails? {
        val offers = subscription?.subscriptionOfferDetails?.filter { it.basePlanId == planId } ?: return null
        // Play only returns offers the user is eligible for; prefer the trial offer when present.
        return offers.firstOrNull { it.offerId != null } ?: offers.firstOrNull()
    }

    fun buySubscription(activity: Activity, planId: String) {
        val details = subscription ?: return
        val offer = offerFor(planId) ?: return
        launch(
            activity,
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details).setOfferToken(offer.offerToken).build(),
        )
    }

    fun buyLifetime(activity: Activity) {
        val details = lifetime ?: return
        launch(activity, BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build())
    }

    private fun launch(activity: Activity, params: BillingFlowParams.ProductDetailsParams) {
        client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params)).build(),
        )
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            purchases?.forEach(::acknowledge)
            restore()
        }
    }

    private fun acknowledge(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
            ) { }
        }
    }

    companion object {
        const val SUB_ID = "premium"
        const val PLAN_MONTHLY = "monthly"
        const val PLAN_YEARLY = "yearly"
        const val LIFETIME_ID = "premium_lifetime"
    }
}
