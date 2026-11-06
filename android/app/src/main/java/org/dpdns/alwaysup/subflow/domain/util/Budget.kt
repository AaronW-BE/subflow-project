package org.dpdns.alwaysup.subflow.domain.util

import org.dpdns.alwaysup.subflow.domain.model.Subscription

/**
 * A monthly spending limit, in the currency it was set in.
 *
 * The currency is kept rather than assumed to be the home currency: someone
 * who sets 100 EUR and later switches the app to dollars still meant 100 EUR,
 * and reading the same number as 100 USD would quietly move their limit.
 */
data class MonthlyBudget(val amount: Double, val currency: String)

/** How much of a [MonthlyBudget] the subscriptions take, in one period. */
data class BudgetProgress(val spent: Double, val limit: Double) {
    val over: Boolean get() = spent > limit
    val remaining: Double get() = (limit - spent).coerceAtLeast(0.0)
    val overBy: Double get() = (spent - limit).coerceAtLeast(0.0)

    /** For a meter: 0 to 1, full once the limit is reached. */
    val fraction: Float get() = if (limit <= 0.0) 1f else (spent / limit).toFloat().coerceIn(0f, 1f)
}

object Budget {

    fun monthlyTotal(subscriptions: List<Subscription>, snapshot: CalculationSnapshot): CostEstimate =
        aggregateContributions(currentContributions(subscriptions, snapshot))

    /** Both sides must be covered, even when the requested change lowers costs. */
    fun newlyOver(
        before: CostEstimate,
        after: CostEstimate,
        budget: MonthlyBudget?,
        snapshot: CalculationSnapshot,
        isPro: Boolean
    ): BudgetProgress? {
        if (!isPro || budget == null || !before.isComplete || !after.isComplete) return null
        val delta = after.knownAmount - before.knownAmount
        if (!delta.isFinite() || delta <= 0.005) return null
        return progress(before, after, budget, snapshot)?.takeIf { it.over }
    }

    fun progress(
        before: CostEstimate,
        after: CostEstimate,
        budget: MonthlyBudget,
        snapshot: CalculationSnapshot,
        annual: Boolean = false
    ): BudgetProgress? {
        if (!before.isComplete || !after.isComplete) return null
        val limit = (convertAmount(budget.amount, budget.currency, snapshot.homeCurrency, snapshot.rates)
            as? ConversionResult.Available)?.amount ?: return null
        val factor = if (annual) 12.0 else 1.0
        val spent = after.knownAmount * factor
        val scaledLimit = limit * factor
        if (!spent.isFinite() || !scaledLimit.isFinite() || !(spent - scaledLimit).isFinite()) return null
        return BudgetProgress(spent, scaledLimit)
    }

    /** Apply accepted records to a captured baseline, never to a post-write collector value. */
    fun replacing(before: List<Subscription>, accepted: List<Subscription>): List<Subscription> {
        val ids = accepted.map { it.id }.toSet()
        return before.filterNot { it.id in ids } + accepted
    }

}

/**
 * Two sentences as one message, whichever language they are in.
 *
 * The messages a budget note is added to do not agree on how they end - some
 * close with a full stop, some ("“Gym” saved") do not - and run together the
 * second sentence read as part of the first. Chinese and Japanese close with
 * a full-width 。 and take no space after it.
 */
fun joinSentences(first: String, second: String, locale: java.util.Locale): String {
    val cjk = locale.language in setOf("zh", "ja")
    val head = first.trimEnd()
    val closed = if (head.lastOrNull() in SENTENCE_ENDS) head else head + if (cjk) "。" else "."
    return if (cjk) closed + second else "$closed $second"
}

private val SENTENCE_ENDS = setOf('.', '!', '?', '。', '！', '？')
