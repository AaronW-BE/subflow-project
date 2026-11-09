package org.dpdns.alwaysup.subflow

import org.dpdns.alwaysup.subflow.domain.model.*
import org.dpdns.alwaysup.subflow.domain.util.*
import org.dpdns.alwaysup.subflow.widget.summariseForWidget
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CsvWidgetSnapshotTest {
    private val today = LocalDate.of(2026, 2, 1)
    private val rates = RatesSnapshot(41, mapOf("USD" to 1.0, "EUR" to 2.0))
    private val snapshot = CalculationSnapshot("USD", today, rates)
    private fun sub(id: String = "one", amount: Double = 10.0, currency: String = "USD") = Subscription(
        id = id, name = id, amount = amount, currency = currency,
        cycle = BillingCycle.MONTHLY, firstBillDate = "2026-01-01", nextBillDate = "2026-02-01"
    )
    private fun row(s: Subscription) = ExportUtils.buildCsv(listOf(s), snapshot).lines()[1].split(',')

    @Test fun csvAppendsStableStatusWithoutChangingExistingColumns() {
        val csv = ExportUtils.buildCsv(listOf(sub()), snapshot)
        assertEquals(ExportUtils.UTF8_BOM + "Name,Category,Amount,Currency,Cycle,MonthlyNormalized,MonthlyInUSD,FirstBillDate,NextRenewalDate,ReminderDaysBefore,Notes,MonthlyConversionStatus", csv.lines()[0])
        assertEquals("AVAILABLE", row(sub()).last())
        assertTrue(csv.endsWith("\n"))
    }
    @Test fun unknownConversionLeavesOnlyHomeCellBlankAndRetainsPausedOriginal() {
        val cells = row(sub(amount = 42.0, currency = "XYZ").copy(isActive = false))
        assertEquals("42.00", cells[2])
        assertEquals("XYZ", cells[3])
        assertEquals("42.00", cells[5])
        assertEquals("", cells[6])
        assertEquals("MISSING_SOURCE_RATE", cells.last())
    }
    @Test fun zeroTrialAndEmptyAreCompleteEvenWithoutRates() {
        for (s in listOf(sub(amount = 0.0, currency = "XYZ"), sub(currency = "XYZ").copy(isTrial = true, trialEndDate = "2026-02-02"))) {
            assertEquals("0.00", row(s)[6])
            assertEquals("AVAILABLE", row(s).last())
            val widget = summariseForWidget(listOf(s), snapshot)
            assertTrue(widget.estimate.isComplete)
            assertEquals(0.0, widget.monthlyTotal!!, 0.0)
        }
        assertEquals(2, ExportUtils.buildCsv(emptyList(), snapshot).lines().size)
        assertTrue(summariseForWidget(emptyList(), snapshot).estimate.isComplete)
    }
    @Test fun malformedOriginalIsRetainedButNeverComputedAsZero() {
        val cells = row(sub(amount = -8.0))
        assertEquals("-8.00", cells[2])
        assertEquals("", cells[5])
        assertEquals("", cells[6])
        assertEquals("INVALID_AMOUNT", cells.last())
    }
    @Test fun normalizationOverflowIsBlankAndExplicit() {
        val cells = row(sub(amount = Double.MAX_VALUE).copy(cycle = BillingCycle.WEEKLY))
        assertEquals("", cells[5])
        assertEquals("", cells[6])
        assertEquals("OVERFLOW", cells.last())
        assertFalse(cells.joinToString().contains("Infinity"))
    }
    @Test fun everyCycleRetainsExistingNormalization() {
        for (cycle in BillingCycle.entries) {
            val cells = row(sub(amount = 120.0).copy(cycle = cycle))
            assertEquals(String.format(java.util.Locale.US, "%.2f", cycle.toMonthly(120.0)), cells[6])
        }
    }
    @Test fun capturedRatesAndHomeDoNotFollowLaterPublications() {
        val mutable = mutableMapOf("USD" to 1.0, "EUR" to 2.0)
        val captured = snapshot.copy(rates = RatesSnapshot(55, mutable))
        val records = List(100) { sub(it.toString(), 10.0, "EUR") }
        val expected = ExportUtils.buildCsv(records, captured)
        mutable["EUR"] = 100.0
        assertEquals(expected, ExportUtils.buildCsv(records, captured))
        assertTrue(expected.lines().drop(1).filter { it.isNotEmpty() }.all { it.split(',')[6] == "5.00" })
        assertEquals(500.0, summariseForWidget(records, captured).monthlyTotal!!, 0.0)
    }
    @Test fun widgetGlobalCountsAndPartialUnavailableAreIndependentOfEvents() {
        val known = sub("known")
        val unknown = sub("unknown", currency = "XYZ").copy(nextBillDate = "invalid")
        val trial = sub("trial", currency = "XYZ").copy(isTrial = true, trialEndDate = "2026-01-01")
        val records = listOf(known, unknown, trial, sub("paused").copy(isActive = false), sub("deleted").copy(isDeleted = true))
        val widget = summariseForWidget(records, snapshot)
        assertEquals(InventoryCounts(4, 3, 1, 1), widget.counts)
        assertEquals(10.0, widget.monthlyTotal!!, 0.0)
        assertFalse(widget.estimate.isComplete)
        assertEquals(listOf("known"), widget.upcoming.map { it.id })
        assertNull(summariseForWidget(listOf(unknown), snapshot).monthlyTotal)
    }
    @Test fun allPausedIsSavedInventoryNotEmptyOnboarding() {
        val widget = summariseForWidget(listOf(sub(currency = "XYZ").copy(isActive = false)), snapshot)
        assertEquals(InventoryCounts(1, 0, 1, 0), widget.counts)
        assertEquals(0.0, widget.monthlyTotal!!, 0.0)
        assertTrue(widget.estimate.isComplete)
        assertTrue(widget.upcoming.isEmpty())
    }
    @Test fun eventsUseValidFutureTrialEndsAndStableDateNameIdOrdering() {
        val a = sub("a").copy(name = "same")
        val b = sub("b").copy(name = "same")
        val trial = sub("trial").copy(isTrial = true, nextBillDate = "invalid", trialEndDate = "2026-02-02")
        val huge = sub("huge").copy(nextBillDate = "+999999999-12-31")
        val widget = summariseForWidget(listOf(b, huge, trial, a, sub("old").copy(nextBillDate = "2026-01-31"), sub("bad").copy(nextBillDate = "bad")), snapshot)
        assertEquals(listOf("a", "b", "trial", "huge"), widget.upcoming.map { it.id })
        assertTrue(widget.upcoming.last().daysLeft > Int.MAX_VALUE)
        assertTrue(widget.upcoming[2].isTrial)
        assertEquals(listOf("trial", "huge"), summariseForWidget(listOf(a, b, trial, huge), snapshot.copy(today = today.plusDays(1))).upcoming.map { it.id })
    }
    @Test fun loadedWidgetDoesNotChangeWhenANewRateSnapshotIsUsed() {
        val records = listOf(sub(currency = "EUR"))
        val loaded = summariseForWidget(records, snapshot)
        val reloaded = summariseForWidget(records, snapshot.copy(rates = RatesSnapshot(42, mapOf("USD" to 1.0, "EUR" to 4.0))))
        assertEquals(5.0, loaded.monthlyTotal!!, 0.0)
        assertEquals(2.5, reloaded.monthlyTotal!!, 0.0)
        assertEquals(5.0, loaded.monthlyTotal!!, 0.0)
    }
    @Test fun finiteMonthlyCsvIsNotInvalidatedByOverflowingHypotheticalAnnualScale() {
        val record = sub(amount = Double.MAX_VALUE / 2)
        assertEquals("AVAILABLE", row(record).last())
        assertFalse(row(record)[6].isBlank())
        val widget = summariseForWidget(listOf(record), snapshot)
        assertTrue(widget.estimate.isComplete)
        val yearly = scaleEstimate(widget.estimate, 12.0)
        assertFalse(yearly.isComplete)
        assertNull(yearly.displayAmount)
    }
    @Test fun csvDeletedExcludedAndJsonModelHasNoExportStatus() {
        assertEquals(ExportUtils.buildCsv(emptyList(), snapshot), ExportUtils.buildCsv(listOf(sub().copy(isDeleted = true)), snapshot))
        assertFalse(Subscription::class.java.declaredFields.any { it.name.contains("ConversionStatus") })
    }
}
