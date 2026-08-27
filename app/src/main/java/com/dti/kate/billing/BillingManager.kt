package com.dti.kate.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Google Play Billing integration. This is the ONLY payment path that
 * belongs in this app for the subscription tiers sold on PremiumScreen -
 * Play policy requires Play Billing (not Stripe, not Flutterwave, not any
 * other processor) for digital content/subscriptions in an app distributed
 * through Google Play. PremiumScreen's existing Flutterwave/Stripe
 * provider picker was built for a different distribution model and needs
 * to be replaced with this before submitting to Play - see
 * docs/ROADMAP.md's Monetization batch.
 *
 * ============================================================
 * CREDENTIALS / CONFIG YOU NEED TO FILL IN - see PLACEHOLDER markers below
 * ============================================================
 * Unlike Stripe/Flutterwave, Play Billing does NOT use an API key embedded
 * in the app - authentication is via your app's own Play Console signing
 * config, so there's no secret to paste here. What you DO need to supply:
 *
 *  1. Product IDs (PLACEHOLDER below) - created in Play Console under
 *     Monetize > Products > Subscriptions, matching the four tiers on
 *     PremiumScreen (premium, pro, lifetime - "free" has no product).
 *     These are just string identifiers you choose, not secrets.
 *
 *  2. (Optional but strongly recommended) A backend endpoint to verify
 *     purchase tokens server-side via the Play Developer API, so a rooted
 *     device or a network-intercepted response can't fake an unlock.
 *     That backend call needs a Google Cloud service-account JSON key -
 *     that key belongs on your SERVER, never in this app's APK. Nothing
 *     in this file should ever hold that credential.
 */
class BillingManager(private val context: Context) {

    companion object {
        // PLACEHOLDER: replace with the actual product/subscription IDs
        // you configure in Play Console. These three map directly to
        // PremiumScreen's PremiumTier.id values - keep them in sync.
        const val PRODUCT_ID_PREMIUM = "kate_premium_monthly" // PLACEHOLDER
        const val PRODUCT_ID_PRO = "kate_pro_monthly"         // PLACEHOLDER
        const val PRODUCT_ID_LIFETIME = "kate_lifetime"       // PLACEHOLDER

        // PLACEHOLDER: your backend's receipt-verification endpoint, if/when
        // one exists. Left blank (rather than a guessed URL) so this fails
        // loudly/obviously instead of silently pointing at nothing.
        const val BACKEND_VERIFY_PURCHASE_URL = "" // PLACEHOLDER
    }

    private val entitlementStore = EntitlementStore(context)

    private var billingClient: BillingClient? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { handlePurchase(it) }
        }
        // User cancelled, already owns it, network hiccup, etc. - no
        // entitlement change; PremiumScreen's own loading/error state
        // handles telling the user what happened.
    }

    /** Call once (e.g. from Application.onCreate or lazily on first
     * PremiumScreen visit) before launching any purchase flow. */
    suspend fun connect(): Boolean = suspendCancellableCoroutine { cont ->
        val client = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()
        billingClient = client

        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
            }
            override fun onBillingServiceDisconnected() {
                // BillingClient reconnects automatically on the next call
                // that needs it - nothing to do here beyond not crashing.
            }
        })
    }

    /** Launches the Play purchase sheet for [productId]. Call from an
     * Activity context (PremiumScreen), since Play's UI needs one. */
    fun launchPurchaseFlow(activity: Activity, productId: String, isSubscription: Boolean) {
        val client = billingClient ?: return
        val productType = if (isSubscription) BillingClient.ProductType.SUBS else BillingClient.ProductType.INAPP

        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(productId)
            .setProductType(productType)
            .build()

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        client.queryProductDetailsAsync(params) { result, productDetailsResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val details = productDetailsResult.productDetailsList.firstOrNull() ?: return@queryProductDetailsAsync

            val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)

            if (isSubscription) {
                val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
                if (offerToken != null) productDetailsParamsBuilder.setOfferToken(offerToken)
            }

            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))
                .build()

            client.launchBillingFlow(activity, flowParams)
        }
    }

    /** Reconciles local EntitlementStore against what Play actually has on
     * record - call on app start so a reinstall, a different device, or a
     * subscription that lapsed/renewed outside this session all resolve to
     * the correct tier rather than trusting a stale local flag. */
    suspend fun queryExistingPurchases() {
        val client = billingClient ?: return
        for (type in listOf(BillingClient.ProductType.SUBS, BillingClient.ProductType.INAPP)) {
            val params = QueryPurchasesParams.newBuilder().setProductType(type).build()
            val result = suspendCancellableCoroutine<PurchasesResult> { cont ->
                client.queryPurchasesAsync(params) { billingResult, purchases ->
                    cont.resume(PurchasesResult(billingResult, purchases))
                }
            }
            if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                result.purchasesList.forEach { handlePurchase(it) }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        // TODO: if BACKEND_VERIFY_PURCHASE_URL is set, POST
        // purchase.purchaseToken there for server-side verification via the
        // Play Developer API before granting entitlement - client-side
        // trust alone (what this scaffold does today) is fine to ship with
        // but is spoofable on a rooted/patched device. See class doc.

        if (!purchase.isAcknowledged) {
            val client = billingClient
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            client?.acknowledgePurchase(ackParams) { /* ignore result - next
                queryExistingPurchases() call will retry if this failed */ }
        }

        val tier = purchase.products.firstOrNull()?.let { productId ->
            when (productId) {
                PRODUCT_ID_PREMIUM -> SubscriptionTier.PREMIUM
                PRODUCT_ID_PRO -> SubscriptionTier.PRO
                PRODUCT_ID_LIFETIME -> SubscriptionTier.LIFETIME
                else -> null
            }
        }
        if (tier != null) entitlementStore.setTier(tier)
    }

    fun disconnect() {
        billingClient?.endConnection()
        billingClient = null
    }
}
