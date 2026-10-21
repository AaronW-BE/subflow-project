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

    /** What the running subscriptions cost a month, in [homeCurrency]. Paused and deleted ones cost nothing. */
    fun monthlyTotal(subscriptions: List<Subscription>, homeCurrency: String): Double =
        subscriptions
            .filter { it.isActive && !it.isDeleted }
            .sumOf { CurrencyConverter.convert(it.monthlyAmount, it.currency, homeCurrency) }

    /**
     * The budget's state after a change from [before] to [after] a month, when
     * that change is worth a word: the total went up and is now over the
     * budget. Null otherwise - a change that lowers the total, or keeps it
     * under, says nothing, and neither does a free user, who has no budget.
     *
     * "Went up and is over" rather than "crossed the line": someone already
     * over who adds another subscription is exactly who should hear by how
     * much they now are.
     */
    fun newlyOver(
        before: Double,
        after: Double,
        budget: MonthlyBudget?,
        homeCurrency: String,
        isPro: Boolean
    ): BudgetProgress? {
        if (!isPro || budget == null || after <= before + 0.005) return null
        return progress(after, budget, homeCurrency).takeIf { it.over }
    }

    /**
     * [totalMonthly] - already in [homeCurrency] - against [budget].
     *
     * In the annual view both sides are a year's worth, so the meter reads
     * the same in either view and only the figures scale.
     */
    fun progress(
        totalMonthly: Double,
        budget: MonthlyBudget,
        homeCurrency: String,
        annual: Boolean = false
    ): BudgetProgress {
        val limitMonthly = CurrencyConverter.convert(budget.amount, budget.currency, homeCurrency)
        val factor = if (annual) 12.0 else 1.0
        return BudgetProgress(spent = totalMonthly * factor, limit = limitMonthly * factor)
    }
}
