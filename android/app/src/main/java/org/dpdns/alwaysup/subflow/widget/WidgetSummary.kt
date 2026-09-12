package org.dpdns.alwaysup.subflow.widget

import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.util.CurrencyConverter
import org.dpdns.alwaysup.subflow.domain.util.DateCalculators

/**
 * Everything the home screen widget shows, worked out in one place.
 *
 * Kept apart from the Glance code so the arithmetic can be tested without a
 * launcher, and so it can be read next to the dashboard's: both must answer
 * the same question the same way, or the widget quietly contradicts the app.
 */
data class WidgetSummary(
    val monthlyTotal: Double,
    val currency: String,
    val activeCount: Int,
    val next: NextRenewal?
)

/** The renewal the widget names, which is simply the soonest one. */
data class NextRenewal(
    val id: String,
    val name: String,
    val daysLeft: Long,
    val isTrial: Boolean
)

/**
 * The active subscriptions, as a monthly total and the next thing due.
 *
 * Deliberately the same shape as `DashboardScreen`: active only (paused and
 * deleted are out), every cycle normalised to a month, everything converted to
 * [primaryCurrency], and the soonest renewal by date. A trial counts as a
 * renewal because its end *is* its next dated event, and it contributes zero
 * to the total because the repository writes its amount as zero.
 */
fun summariseForWidget(
    subscriptions: List<Subscription>,
    primaryCurrency: String
): WidgetSummary {
    val active = subscriptions.filter { it.isActive && !it.isDeleted }
    val total = active.sumOf {
        CurrencyConverter.convert(it.monthlyAmount, it.currency, primaryCurrency)
    }
    val soonest = active.minByOrNull { DateCalculators.calculateDaysUntil(it.nextBillDate) }
    return WidgetSummary(
        monthlyTotal = total,
        currency = primaryCurrency,
        activeCount = active.size,
        next = soonest?.let {
            NextRenewal(
                id = it.id,
                name = it.name,
                daysLeft = DateCalculators.calculateDaysUntil(it.nextBillDate),
                isTrial = it.isTrialPending
            )
        }
    )
}
