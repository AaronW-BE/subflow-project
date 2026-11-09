package org.dpdns.alwaysup.subflow

import androidx.compose.ui.graphics.Color
import org.dpdns.alwaysup.subflow.domain.model.*
import org.dpdns.alwaysup.subflow.domain.util.*
import org.dpdns.alwaysup.subflow.ui.screens.analytics.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class SubscriptionAnalyticsTest {
    private val snapshot = CalculationSnapshot("USD", LocalDate.of(2026, 9, 18),
        RatesSnapshot(1, mapOf("USD" to 1.0, "EUR" to 2.0, "JPY" to 100.0)))
    private fun sub(id: String, amount: Double = 10.0, currency: String = "USD") = Subscription(
        id = id, name = id, amount = amount, currency = currency, category = id,
        firstBillDate = "2026-09-01", nextBillDate = "2026-09-01")
    private fun history(subs: List<Subscription>, snap: CalculationSnapshot = snapshot) = buildSpendHistory(subs, snap, Locale.US)
    private fun forecast(subs: List<Subscription>, snap: CalculationSnapshot = snapshot) = buildBillingForecast(subs, snap, Locale.US)
    private fun map(subs: List<Subscription>) = buildTreemapItems(subs, snapshot, "Other", Color.Gray)

    @Test fun completeCostsNormalizeAndRankConvertedAmounts() {
        val costs = calculateAnalyticsCosts(listOf(sub("yen", 100.0, "JPY"), sub("dollar", 5.0), sub("annual", 120.0).copy(cycle = BillingCycle.ANNUALLY)), snapshot)
        assertEquals(16.0, costs.monthly.displayAmount!!, 0.0)
        assertEquals(listOf("annual", "dollar", "yen"), costs.ranked!!.map { it.first.id })
        assertEquals(62, costs.topSharePercent)
        assertEquals(16.0 / 3, costs.average.displayAmount!!, 1e-10)
        assertEquals(16.0 * 12 / 365, costs.daily.displayAmount!!, 1e-10)
    }
    @Test fun missingSuspendsEveryComparisonButPreservesIndependentCategory() {
        val subs = listOf(sub("known"), sub("missing", currency = "XXX"))
        val costs = calculateAnalyticsCosts(subs, snapshot)
        assertEquals(10.0, costs.monthly.displayAmount!!, 0.0)
        assertFalse(costs.monthly.isComplete)
        assertNull(costs.ranked)
        assertNull(costs.topSharePercent)
        assertNull(map(subs))
        assertNull(history(subs))
        assertNull(forecast(subs))
        assertTrue(costs.categories.getValue("known").isComplete)
        assertEquals(10.0, costs.categories.getValue("known").displayAmount!!, 0.0)
        assertFalse(costs.average.isComplete)
    }
    @Test fun noKnownPositiveDoesNotInventZero() {
        val costs = calculateAnalyticsCosts(listOf(sub("unknown", currency = "XXX"), sub("free", 0.0)), snapshot)
        assertNull(costs.monthly.displayAmount)
        assertNull(costs.daily.displayAmount)
        assertNull(costs.average.displayAmount)
    }
    @Test fun emptyFreeAndPendingAreCompleteZeroEvenWhenTrialExpiredAndKept() {
        val trial = sub("trial", 999.0, "XXX").copy(isTrial = true, trialEndDate = "2020-01-01", trialKeepEndDate = "2020-01-01")
        for (subs in listOf(emptyList(), listOf(sub("free", 0.0, "XXX")), listOf(trial))) {
            val costs = calculateAnalyticsCosts(subs, snapshot)
            assertTrue(costs.monthly.isComplete)
            assertEquals(0.0, costs.monthly.displayAmount!!, 0.0)
            assertNotNull(costs.ranked)
            assertNotNull(map(subs))
        }
    }
    @Test fun pausedAndDeletedNeverAffectCoverageOrAverageDenominator() {
        val subs = listOf(sub("paid"), sub("trial").copy(isTrial = true),
            sub("paused", currency = "XXX").copy(isActive = false), sub("deleted", currency = "XXX").copy(isDeleted = true))
        val costs = calculateAnalyticsCosts(subs, snapshot)
        assertTrue(costs.monthly.isComplete)
        assertEquals(5.0, costs.average.displayAmount!!, 0.0)
        assertNotNull(history(subs))
    }
    @Test fun trialFutureMissingDoesNotInvalidateCurrentOrHistory() {
        val trial = sub("trial", currency = "XXX").copy(isTrial = true, trialEndDate = "2026-10-05", postTrialAmount = 25.0, trialConverts = true)
        assertTrue(calculateAnalyticsCosts(listOf(trial), snapshot).monthly.isComplete)
        assertNotNull(history(listOf(trial)))
        assertNull(forecast(listOf(trial)))
        assertNotNull(forecast(listOf(trial.copy(trialConverts = false))))
    }
    @Test fun currentMissingCanHaveCompleteForecastAndHistoryWhenOutsideTheirContributions() {
        val future = sub("future", currency = "XXX").copy(firstBillDate = "2027-09-01")
        assertFalse(calculateAnalyticsCosts(listOf(future), snapshot).monthly.isComplete)
        assertTrue(history(listOf(future))!!.all { it.amount == 0.0 })
        assertTrue(forecast(listOf(future))!!.all { it.amount == 0.0 })
    }
    @Test fun historyUsesIncludedRecordsOnlyAndPreservesInvalidStartSemantics() {
        val unknown = sub("unknown", currency = "XXX").copy(firstBillDate = "2026-10-01")
        assertNotNull(history(listOf(unknown)))
        assertNull(history(listOf(unknown.copy(firstBillDate = "invalid"))))
        assertNull(history(listOf(unknown), snapshot.copy(today = LocalDate.of(2026, 10, 1))))
    }
    @Test fun forecastIncludesElapsedDatesAndFullSixMonthsAndTrialTransition() {
        val trial = sub("trial").copy(isTrial = true, trialEndDate = "2026-10-02", postTrialAmount = 30.0, postTrialCycle = BillingCycle.QUARTERLY, trialConverts = true)
        val result = forecast(listOf(sub("monthly", 2.0), trial))!!
        assertEquals(listOf("Sep", "Oct", "Nov", "Dec", "Jan", "Feb"), result.map { it.label })
        assertEquals(listOf(2.0, 32.0, 2.0, 2.0, 32.0, 2.0), result.map { it.amount })
    }
    @Test fun dateAndRateOnlySnapshotsRecalculateWithoutGlobalRates() {
        val subs = listOf(sub("euro", 20.0, "EUR"))
        assertEquals(10.0, forecast(subs)!!.first().amount, 0.0)
        val changed = snapshot.copy(rates = RatesSnapshot(2, mapOf("USD" to 1.0, "EUR" to 4.0)))
        assertEquals(5.0, forecast(subs, changed)!!.first().amount, 0.0)
        assertEquals("Oct", forecast(subs, changed.copy(today = LocalDate.of(2026, 10, 1)))!!.first().label)
    }
    @Test fun overflowSuspendsAffectedResultsWithoutInfinityOrBlankingCurrent() {
        val huge = listOf(sub("huge", Double.MAX_VALUE / 2))
        val costs = calculateAnalyticsCosts(huge, snapshot, 12.0)
        assertTrue(costs.monthly.isComplete)
        assertNull(costs.period.displayAmount)
        assertTrue(costs.daily.displayAmount!!.isFinite())
        assertNull(costs.ranked)
        assertNotNull(costs.topSharePercent)
        assertEquals(listOf(50, 50), wholePercentages(listOf(Double.MAX_VALUE, Double.MAX_VALUE)))
        assertNull(buildTreemapItems(huge, snapshot, "Other", Color.Gray, factor = 12.0))
        assertNull(buildSpendHistory(huge, snapshot, Locale.US, factor = 12.0))
        assertNull(forecast(listOf(sub("weekly", Double.MAX_VALUE).copy(cycle = BillingCycle.WEEKLY))))
    }
    @Test fun invalidForecastDatesNeverBecomeTodayAndFiniteGuardRejectsCorruptAmounts() {
        assertNull(forecast(listOf(sub("bad").copy(firstBillDate = "bad", nextBillDate = "bad"))))
        assertNull(forecast(listOf(sub("bad", Double.POSITIVE_INFINITY))))
        assertNull(calculateAnalyticsCosts(listOf(sub("bad", Double.NaN)), snapshot).monthly.displayAmount)
    }
}
