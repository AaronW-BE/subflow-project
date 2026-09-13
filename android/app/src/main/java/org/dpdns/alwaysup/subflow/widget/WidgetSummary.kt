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
    /**
     * Every active subscription, soonest first.
     *
     * All of them, not a top few: the widget's list scrolls, and how many rows
     * fit is the launcher's business, not this function's. Cutting the list
     * here was what made a tall widget show one subscription and then empty
     * space.
     */
    val upcoming: List<UpcomingRenewal>
) {
    /** The soonest renewal, which is what the smallest widget names. */
    val next: UpcomingRenewal? get() = upcoming.firstOrNull()
}

/** One row of the widget's list. */
data class UpcomingRenewal(
    val id: String,
    val name: String,
    val daysLeft: Long,
    val isTrial: Boolean,
    /** Per billing cycle, in the subscription's own currency - as its dashboard row shows it. */
    val amount: Double,
    val currency: String
)

/**
 * The active subscriptions, as a monthly total and a list by renewal date.
 *
 * Deliberately the same shape as `DashboardScreen`: active only (paused and
 * deleted are out), every cycle normalised to a month, everything converted to
 * [primaryCurrency], and ordered by how soon each renews. A trial counts as a
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
    val upcoming = active
        .map { sub ->
            UpcomingRenewal(
                id = sub.id,
                name = sub.name,
                daysLeft = DateCalculators.calculateDaysUntil(sub.nextBillDate),
                isTrial = sub.isTrialPending,
                amount = sub.amount,
                currency = sub.currency
            )
        }
        // Name breaks ties so two renewals on the same day keep a stable
        // order between redraws instead of swapping places.
        .sortedWith(compareBy<UpcomingRenewal> { it.daysLeft }.thenBy { it.name.lowercase() })

    return WidgetSummary(
        monthlyTotal = total,
        currency = primaryCurrency,
        activeCount = active.size,
        upcoming = upcoming
    )
}
