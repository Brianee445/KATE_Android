package com.dti.kate.billing

import android.content.Context

/**
 * Fast, synchronous-read local mirror of the user's entitlement, so UI and
 * KateCommandProcessor can check "is this feature unlocked" without an
 * async round-trip on every single command.
 *
 * Two things keep this in sync, either of which may run depending on which
 * flow the user is in:
 *  - SettingsViewModel.loadProfile() -> repository.getCurrentUser(), which
 *    already fetches the account's tier from the existing backend (the
 *    same one auth/login use - NOT the dead chat endpoint) and calls
 *    setTier() here.
 *  - BillingManager.handlePurchase(), for a purchase made through Play
 *    Billing directly on-device (see that class's doc comment on why Play
 *    Billing, not Stripe/Flutterwave, has to be the payment path here).
 *
 * Deliberately NOT the source of truth for entitlement - a value here with
 * no corresponding valid backend record or Play purchase is just a stale
 * cache, not a real unlock.
 */
class EntitlementStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("kate_entitlements", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TIER = "current_tier"
    }

    fun getTier(): SubscriptionTier = SubscriptionTier.fromId(prefs.getString(KEY_TIER, null) ?: "free")

    fun setTier(tier: SubscriptionTier) = prefs.edit().putString(KEY_TIER, tier.id).apply()

    fun isUnlocked(feature: GatedFeature): Boolean = FeatureGate.isUnlocked(feature, getTier())
}
