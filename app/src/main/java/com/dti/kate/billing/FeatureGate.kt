package com.dti.kate.billing

/**
 * The four tiers as sold on PremiumScreen. Ordered low-to-high so
 * `SubscriptionTier.atLeast()` can do a simple ordinal comparison instead
 * of hardcoding "premium OR pro OR lifetime" checks at every gate site.
 */
enum class SubscriptionTier(val id: String) {
    FREE("free"),
    PREMIUM("premium"),
    PRO("pro"),
    LIFETIME("lifetime");

    fun atLeast(minimum: SubscriptionTier): Boolean = this.ordinal >= minimum.ordinal

    companion object {
        fun fromId(id: String): SubscriptionTier =
            entries.find { it.id.equals(id.trim(), ignoreCase = true) } ?: FREE
    }
}

/**
 * Every gate-able feature in one place, so "what's free vs paid" has a
 * single source of truth instead of being scattered across UI screens and
 * command-processing code as ad-hoc if-checks. Per product decision:
 *  - Chat (typed conversation) is free
 *  - Jokes, the tone slider, and custom/full wake-word support are
 *    Premium and above
 * Extend this enum + the `requiredTier` map below when adding new paid
 * features - never gate a feature by checking `tier == SubscriptionTier.PRO`
 * directly at the call site, since that hardcodes today's pricing decision
 * into unrelated UI/logic code.
 */
enum class GatedFeature {
    CHAT,
    JOKES,
    TONE_SLIDER,
    WAKE_WORD,
}

private val requiredTier: Map<GatedFeature, SubscriptionTier> = mapOf(
    GatedFeature.CHAT to SubscriptionTier.FREE,
    GatedFeature.JOKES to SubscriptionTier.PREMIUM,
    GatedFeature.TONE_SLIDER to SubscriptionTier.PREMIUM,
    GatedFeature.WAKE_WORD to SubscriptionTier.PREMIUM,
)

/**
 * Answers "can the current user use this feature" against whatever tier
 * EntitlementStore reports. Kept as pure functions over an injected tier
 * rather than a singleton so call sites (UI composables, KateCommandProcessor)
 * can be tested without a real billing connection.
 */
object FeatureGate {
    fun isUnlocked(feature: GatedFeature, currentTier: SubscriptionTier): Boolean =
        currentTier.atLeast(requiredTier.getValue(feature))

    fun requiredTierFor(feature: GatedFeature): SubscriptionTier = requiredTier.getValue(feature)
}
