package org.dpdns.alwaysup.subflow.ui.screens.paywall

import java.time.LocalDate

/**
 * What the user was doing when they opened the paywall (BIN-74).
 *
 * The paywall is only ever opened by the user's own tap - a Pro lock, the
 * quota card, the header pill - never pushed at them. Each of those taps is a
 * question about one feature, so the paywall leads with that feature rather
 * than with the same list for everyone.
 */
enum class PaywallReason {
    /** The header pill, the Settings plan card, onboarding. */
    GENERAL,

    /** The free plan's five are all in use. */
    LIMIT,

    /** A 3- or 7-day reminder chip. */
    ALERTS,

    /** The monthly budget row. */
    BUDGET,

    /** A blurred chart, or CSV export. */
    ANALYTICS
}

/**
 * The features Pro actually adds, and nothing the free plan already has.
 *
 * Multi-currency totals used to be on this list, and the 1-day reminder was
 * implied by "alerts 1, 3 and 7 days ahead". Both are on every plan, so both
 * were claims of something the user would not be getting for their money.
 */
enum class ProFeatureKey { ALERTS, UNLIMITED, BUDGET, ANALYTICS, NO_ADS }

object PaywallContent {

    /**
     * Most concrete benefit first: a week's notice is money kept, which is
     * what someone tracking subscriptions is here for. The limit comes next
     * because it is the wall most people meet.
     */
    val DEFAULT_ORDER = listOf(
        ProFeatureKey.ALERTS,
        ProFeatureKey.UNLIMITED,
        ProFeatureKey.BUDGET,
        ProFeatureKey.ANALYTICS,
        ProFeatureKey.NO_ADS
    )

    /** The feature the user asked about, if any. */
    fun leadFeature(reason: PaywallReason): ProFeatureKey? = when (reason) {
        PaywallReason.GENERAL -> null
        PaywallReason.LIMIT -> ProFeatureKey.UNLIMITED
        PaywallReason.ALERTS -> ProFeatureKey.ALERTS
        PaywallReason.BUDGET -> ProFeatureKey.BUDGET
        PaywallReason.ANALYTICS -> ProFeatureKey.ANALYTICS
    }

    /** [DEFAULT_ORDER], with the feature asked about moved to the top. */
    fun featureOrder(reason: PaywallReason): List<ProFeatureKey> {
        val lead = leadFeature(reason) ?: return DEFAULT_ORDER
        return listOf(lead) + DEFAULT_ORDER.filter { it != lead }
    }

    /**
     * The day a free trial bought today would turn into a charge. Said as a
     * date because "7 days" leaves the user to count, and the date is the
     * thing they need to put in a calendar.
     */
    fun trialEnds(today: LocalDate, trialDays: Int): LocalDate =
        today.plusDays(trialDays.toLong())
}
