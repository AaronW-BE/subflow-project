package org.dpdns.alwaysup.subflow.domain.util

import org.dpdns.alwaysup.subflow.domain.model.BillingCycle
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import java.time.LocalDate

data class CalculationSnapshot(
    val homeCurrency: String,
    val today: LocalDate,
    val rates: RatesSnapshot
)

data class InventoryCounts(val total: Int, val active: Int, val paused: Int, val pendingTrial: Int)

data class CostContribution(
    val subscriptionId: String,
    val currency: String,
    val result: ConversionResult
)

data class MissingContribution(
    val subscriptionId: String,
    val currency: String,
    val reason: ConversionFailure
)

/** Incomplete known amounts must not be presented as complete costs. */
class CostEstimate internal constructor(
    val knownAmount: Double,
    val convertiblePositiveCount: Int,
    missing: List<MissingContribution>
) {
    val missing: List<MissingContribution> = java.util.Collections.unmodifiableList(missing.toList())
    val missingSubscriptionIds: List<String> get() = missing.map { it.subscriptionId }
    val missingCurrencies: Set<String> get() = missing.map { it.currency }.toSet()
    val isComplete: Boolean get() = missing.isEmpty()
    val displayAmount: Double? get() = knownAmount.takeIf { isComplete || convertiblePositiveCount > 0 }
}

/** Null cycle means a one-off amount; a cycle requests existing monthly normalization. */
fun normalizeContribution(
    subscriptionId: String,
    amount: Double,
    currency: String,
    snapshot: CalculationSnapshot,
    cycle: BillingCycle? = null
): CostContribution {
    val result = if (!amount.isFinite() || amount < 0.0) {
        ConversionResult.Unavailable(ConversionFailure.INVALID_AMOUNT)
    } else {
        val normalized = cycle?.toMonthly(amount) ?: amount
        when {
            !normalized.isFinite() -> ConversionResult.Unavailable(ConversionFailure.OVERFLOW)
            amount > 0.0 && normalized == 0.0 -> ConversionResult.Unavailable(ConversionFailure.UNDERFLOW)
            else -> convertAmount(normalized, currency, snapshot.homeCurrency, snapshot.rates)
        }
    }
    return CostContribution(subscriptionId, currencyKey(currency), result)
}

fun aggregateContributions(contributions: Iterable<CostContribution>): CostEstimate {
    var known = 0.0
    var positiveCount = 0
    val missing = mutableListOf<MissingContribution>()
    for (contribution in contributions) {
        val result = contribution.result
        val failure = when (result) {
            is ConversionResult.Unavailable -> result.reason
            is ConversionResult.Available -> when {
                !result.amount.isFinite() || result.amount < 0.0 -> ConversionFailure.INVALID_AMOUNT
                !(known + result.amount).isFinite() -> ConversionFailure.OVERFLOW
                else -> null
            }
        }
        if (failure != null) {
            missing += MissingContribution(contribution.subscriptionId, contribution.currency, failure)
        } else if (result is ConversionResult.Available) {
            known += result.amount
            if (result.amount > 0.0) positiveCount++
        }
    }
    return CostEstimate(known, positiveCount, missing)
}

data class NormalizedSubscriptionPrice(
    val subscriptionId: String,
    val cyclePrice: ConversionResult,
    val monthlyPrice: ConversionResult
)

data class UpcomingEvent(
    val subscriptionId: String,
    val name: String,
    val date: LocalDate,
    val isPendingTrial: Boolean,
    val amount: Double,
    val currency: String,
    val cycle: BillingCycle
)

data class SubscriptionOverview(
    val counts: InventoryCounts,
    val currentMonthlyEstimate: CostEstimate,
    val normalizedPrices: List<NormalizedSubscriptionPrice>,
    val upcomingEvents: List<UpcomingEvent>
)

fun calculateOverview(subscriptions: List<Subscription>, snapshot: CalculationSnapshot): SubscriptionOverview {
    val saved = subscriptions.filterNot { it.isDeleted }
    val active = saved.filter { it.isActive }
    fun monthly(sub: Subscription) = normalizeContribution(
        sub.id, if (sub.isTrialPending) 0.0 else sub.amount, sub.currency, snapshot, sub.cycle
    )
    val prices = saved.map { sub ->
        NormalizedSubscriptionPrice(
            sub.id,
            normalizeContribution(sub.id, if (sub.isTrialPending) 0.0 else sub.amount, sub.currency, snapshot).result,
            monthly(sub).result
        )
    }
    val events = active.mapNotNull { sub ->
        val date = runCatching {
            LocalDate.parse(if (sub.isTrialPending) sub.trialEndDate else sub.nextBillDate)
        }.getOrNull()?.takeUnless { it.isBefore(snapshot.today) } ?: return@mapNotNull null
        UpcomingEvent(sub.id, sub.name, date, sub.isTrialPending, sub.amount, sub.currency, sub.cycle)
    }.sortedWith(compareBy<UpcomingEvent> { it.date }.thenBy { it.name }.thenBy { it.subscriptionId })
    return SubscriptionOverview(
        InventoryCounts(saved.size, active.size, saved.size - active.size, active.count { it.isTrialPending }),
        aggregateContributions(active.map { monthly(it) }), prices, events
    )
}
