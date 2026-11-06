package org.dpdns.alwaysup.subflow.domain.util

import org.dpdns.alwaysup.subflow.domain.model.Subscription

fun scaleEstimate(estimate: CostEstimate, factor: Double): CostEstimate = aggregateContributions(
    listOf(CostContribution("scaled", "", when {
        !factor.isFinite() || factor < 0.0 -> ConversionResult.Unavailable(ConversionFailure.INVALID_AMOUNT)
        !(estimate.knownAmount * factor).isFinite() -> ConversionResult.Unavailable(ConversionFailure.OVERFLOW)
        estimate.knownAmount > 0.0 && factor > 0.0 && estimate.knownAmount * factor == 0.0 ->
            ConversionResult.Unavailable(ConversionFailure.UNDERFLOW)
        else -> ConversionResult.Available(estimate.knownAmount * factor)
    })) + estimate.missing.map {
        CostContribution(it.subscriptionId, it.currency, ConversionResult.Unavailable(it.reason))
    }
)

fun currentContributions(subscriptions: List<Subscription>, snapshot: CalculationSnapshot): List<CostContribution> =
    subscriptions.filter { it.isActive && !it.isDeleted }.map {
        normalizeContribution(it.id, if (it.isTrialPending) 0.0 else it.amount, it.currency, snapshot, it.cycle)
    }

data class AnalyticsCosts(
    val monthly: CostEstimate,
    val period: CostEstimate,
    val daily: CostEstimate,
    val average: CostEstimate,
    val categories: Map<String, CostEstimate>,
    val ranked: List<Pair<Subscription, Double>>?,
    val topSharePercent: Int?
)

fun calculateAnalyticsCosts(
    subscriptions: List<Subscription>, snapshot: CalculationSnapshot, factor: Double = 1.0
): AnalyticsCosts {
    val active = subscriptions.filter { it.isActive && !it.isDeleted }
    val contributions = currentContributions(active, snapshot)
    val monthly = aggregateContributions(contributions)
    val period = scaleEstimate(monthly, factor)
    val prices = contributions.associate { it.subscriptionId to (it.result as? ConversionResult.Available)?.amount }
    val ranked = if (period.isComplete) active.map { it to prices.getValue(it.id)!! * factor }
        .sortedByDescending { it.second } else null
    val share = if (!monthly.isComplete) null else if (monthly.knownAmount == 0.0) 0 else {
        ((prices.values.filterNotNull().maxOrNull() ?: 0.0) / monthly.knownAmount * 100.0).toInt()
    }
    return AnalyticsCosts(monthly, period, scaleEstimate(monthly, 12.0 / 365.0),
        scaleEstimate(period, if (active.isEmpty()) 1.0 else 1.0 / active.size),
        active.groupBy { it.category }.mapValues { (_, list) ->
            scaleEstimate(aggregateContributions(currentContributions(list, snapshot)), factor)
        }, ranked, share)
}
