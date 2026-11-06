package org.dpdns.alwaysup.subflow.widget

import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.util.*
import java.time.temporal.ChronoUnit

data class WidgetSummary(
    val estimate: CostEstimate,
    val currency: String,
    val counts: InventoryCounts,
    val upcoming: List<UpcomingRenewal>
) {
    val activeCount: Int get() = counts.active
    val monthlyTotal: Double? get() = estimate.displayAmount
    val next: UpcomingRenewal? get() = upcoming.firstOrNull()
}

data class UpcomingRenewal(
    val id: String, val name: String, val daysLeft: Long, val isTrial: Boolean,
    val amount: Double, val currency: String, val date: String
)

/** Pure, global inventory and current commitment, using the homepage contract. */
fun summariseForWidget(subscriptions: List<Subscription>, snapshot: CalculationSnapshot): WidgetSummary {
    val overview = calculateOverview(subscriptions, snapshot)
    return WidgetSummary(overview.currentMonthlyEstimate, snapshot.homeCurrency, overview.counts,
        overview.upcomingEvents.map {
            UpcomingRenewal(it.subscriptionId, it.name, ChronoUnit.DAYS.between(snapshot.today, it.date),
                it.isPendingTrial, it.amount, it.currency, it.date.toString())
        })
}
