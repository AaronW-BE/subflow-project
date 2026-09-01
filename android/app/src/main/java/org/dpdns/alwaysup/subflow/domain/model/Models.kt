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
    val isDeleted: Boolean = false
) {
    val monthlyAmount: Double get() = cycle.toMonthly(amount)
    val yearlyAmount: Double get() = cycle.toYearly(amount)
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
