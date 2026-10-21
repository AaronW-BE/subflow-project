package org.dpdns.alwaysup.subflow.domain.util

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
