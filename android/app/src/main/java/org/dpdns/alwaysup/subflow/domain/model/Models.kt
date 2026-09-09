package org.dpdns.alwaysup.subflow.domain.model

enum class BillingCycle(val key: String) {
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    QUARTERLY("quarterly"),
    ANNUALLY("annually");

    fun toMonthly(amount: Double): Double = when (this) {
        WEEKLY -> amount * 4.3333
        MONTHLY -> amount
        QUARTERLY -> amount / 3.0
        ANNUALLY -> amount / 12.0
    }

    fun toYearly(amount: Double): Double = toMonthly(amount) * 12.0

    companion object {
        fun fromKey(key: String): BillingCycle = entries.find { it.key.equals(key, ignoreCase = true) } ?: MONTHLY
    }
}

enum class ProTier(val key: String) {
    FREE("free"),
    MONTHLY("monthly"),
    ANNUAL("annual"),
    LIFETIME("lifetime");

    companion object {
        fun fromKey(key: String): ProTier = entries.find { it.key.equals(key, ignoreCase = true) } ?: FREE
    }
}

/**
 * What became of a trial once its end date passed.
 *
 * There is deliberately no "extended" member. Extending a trial only moves
 * [Subscription.trialEndDate] and leaves the trial running, so recording it as
 * an outcome would mean the end-of-trial prompt never returns the second time
 * around - the user would lose the warning exactly once it matters again.
 */
enum class TrialOutcome(val key: String) {
    PENDING(""),
    CONVERTED("converted"),
    CANCELLED("cancelled");

    companion object {
        fun fromKey(key: String): TrialOutcome =
            entries.find { it.key.equals(key, ignoreCase = true) } ?: PENDING
    }
}

data class Subscription(
    val id: String,
    val name: String,
    val category: String = "Entertainment",
    val amount: Double,
    val currency: String = "USD",
    val cycle: BillingCycle = BillingCycle.MONTHLY,
    val firstBillDate: String,
    val nextBillDate: String,
    val reminderDaysBefore: Int = 3,
    val isActive: Boolean = true,
    val colorHex: String = "#5856D6",
    val iconUrl: String = "",
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    /**
     * Whether this is a free trial rather than something being paid for.
     *
     * A trial carries [amount] `0.0` - it costs nothing today, and that is what
     * every total in the app should say. The price that starts once it ends
     * lives in [postTrialAmount] instead, so no aggregate has to remember to
     * exclude trials and none of them can silently forget to.
     */
    val isTrial: Boolean = false,
    /** ISO date the trial ends. Also mirrored into [nextBillDate] on save. */
    val trialEndDate: String = "",
    /** Whether the trial rolls into a paid plan or simply stops. */
    val trialConverts: Boolean = true,
    val postTrialAmount: Double = 0.0,
    val postTrialCycle: BillingCycle = BillingCycle.MONTHLY,
    val trialOutcome: TrialOutcome = TrialOutcome.PENDING
) {
    val monthlyAmount: Double get() = cycle.toMonthly(amount)
    val yearlyAmount: Double get() = cycle.toYearly(amount)

    /** A trial the user has not yet told us the ending of. */
    val isTrialPending: Boolean get() = isTrial && trialOutcome == TrialOutcome.PENDING

    /** What lands when the trial ends, or null when it ends without a charge. */
    val postTrialCharge: Double? get() = if (trialConverts) postTrialAmount else null
}

data class PresetService(
    val id: String,
    val name: String,
    val category: String,
    val brandColor: String,
    val iconUrl: String,
    val defaultCycle: BillingCycle = BillingCycle.MONTHLY,
    val defaultAmountUSD: Double,
    val websiteUrl: String = "",
    val isPopular: Boolean = false
)

data class UserProfile(
    val id: String,
    val email: String,
    val name: String,
    val picture: String,
    val authProvider: String,
    val isPro: Boolean,
    val proTier: ProTier = ProTier.FREE
)

data class SubFlowBackupContainer(
    val version: Int = 1,
    val exportTimestamp: Long,
    val subscriptions: List<Subscription>
)
