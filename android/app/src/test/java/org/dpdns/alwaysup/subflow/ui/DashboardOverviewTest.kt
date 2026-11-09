package org.dpdns.alwaysup.subflow.ui

import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.model.BillingCycle
import org.dpdns.alwaysup.subflow.domain.util.*
import org.dpdns.alwaysup.subflow.ui.screens.dashboard.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DashboardOverviewTest {
    private val today = LocalDate.of(2026, 2, 1)
    private val snapshot = CalculationSnapshot("USD", today, RatesSnapshot(1, mapOf("USD" to 1.0, "EUR" to 2.0)))
    private fun sub(id: String, amount: Double = 10.0, currency: String = "USD") = Subscription(
        id = id, name = id, amount = amount, currency = currency,
        firstBillDate = "2026-01-01", nextBillDate = "2026-02-01", updatedAt = 0
    )

    @Test fun unavailablePricesAreLastInBothDirectionsWithStableTiesAndMonthlyBasis() {
        val subs = listOf(sub("unknown1", 1.0, "ZZZ"), sub("annual", 120.0).copy(cycle = BillingCycle.ANNUALLY),
            sub("cheap", 4.0, "EUR"), sub("tie", 10.0), sub("unknown2", 1000.0, "ZZZ"), sub("deleted").copy(isDeleted = true))
        fun sorted(order: DashboardSortOrder) = subs.filterAndSort("All", "", order, "USD", snapshot).map { it.id }
        assertEquals(listOf("cheap", "annual", "tie", "unknown1", "unknown2"), sorted(DashboardSortOrder.PRICE_LOW))
        assertEquals(listOf("annual", "tie", "cheap", "unknown1", "unknown2"), sorted(DashboardSortOrder.PRICE_HIGH))
    }

    @Test fun countsIncludeExpiredKeptTrialsButNotPausedTrialsOrDeletedRecords() {
        val kept = sub("trial", 999.0, "ZZZ").copy(isTrial = true, trialEndDate = "2026-01-01", trialKeepEndDate = "2026-01-01")
        val overview = calculateOverview(listOf(kept, kept.copy(id = "paused", isActive = false), sub("gone").copy(isDeleted = true)), snapshot)
        assertEquals(InventoryCounts(2, 1, 1, 1), overview.counts)
        assertTrue(overview.currentMonthlyEstimate.isComplete)
        assertEquals(0.0, overview.currentMonthlyEstimate.displayAmount!!, 0.0)
        assertTrue(overview.upcomingEvents.isEmpty())
    }

    @Test fun validEventsUseStableDateNameIdAndRefreshWithSnapshotDate() {
        val records = listOf(sub("z"), sub("a"), sub("past").copy(nextBillDate = "2026-01-31"), sub("bad").copy(nextBillDate = "invalid"), sub("future").copy(nextBillDate = "2026-02-02"))
        assertEquals(listOf("a", "z", "future"), calculateOverview(records, snapshot).upcomingEvents.map { it.subscriptionId })
        assertEquals(listOf("future"), calculateOverview(records, snapshot.copy(today = today.plusDays(1))).upcomingEvents.map { it.subscriptionId })
    }
}
