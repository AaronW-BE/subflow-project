package org.dpdns.alwaysup.subflow

import org.dpdns.alwaysup.subflow.domain.model.BillingCycle
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.ui.screens.analytics.buildBillingForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/**
 * The forecast's whole reason to exist is that it does *not* smooth: an annual
 * plan has to land whole, in one month. These pin that, because the tempting
 * simplification - reuse monthlyAmount like every other number on the screen -
 * would produce six identical bars and still look plausible.
 */
class BillingForecastTest {

    private val today = LocalDate.of(2026, 9, 8)

    private fun sub(
        id: String,
        amount: Double,
        firstBillDate: String,
        cycle: BillingCycle = BillingCycle.MONTHLY,
        currency: String = "USD"
    ) = Subscription(
        id = id,
        name = id,
        amount = amount,
        currency = currency,
        cycle = cycle,
        firstBillDate = firstBillDate,
        nextBillDate = firstBillDate
    )

    @Test
    fun `the window is six months starting with the current one`() {
        val months = buildBillingForecast(emptyList(), "USD", Locale.US, today)
        assertEquals(6, months.size)
        assertEquals("Sep", months.first().label)
        assertEquals("Feb", months.last().label)
        assertTrue(months.first().isCurrentMonth)
        assertTrue(months.drop(1).none { it.isCurrentMonth })
    }

    @Test
    fun `a monthly plan charges once in every month`() {
        val months = buildBillingForecast(
            listOf(sub("m", 9.99, "2025-03-12")), "USD", Locale.US, today
        )
        months.forEach {
            assertEquals(9.99, it.amount, 0.0001)
            assertEquals(1, it.charges)
        }
    }

    @Test
    fun `an annual plan lands whole in one month and is absent from the rest`() {
        // First billed 12 January, so the renewal inside this window is January.
        val months = buildBillingForecast(
            listOf(sub("a", 120.0, "2024-01-12", BillingCycle.ANNUALLY)),
            "USD", Locale.US, today
        )
        val january = months.single { it.label == "Jan" }
        assertEquals(120.0, january.amount, 0.0001)
        assertEquals(1, january.charges)
        assertEquals(0.0, months.filter { it.label != "Jan" }.sumOf { it.amount }, 0.0001)
    }

    @Test
    fun `the peak month is the one the smoothed average hides`() {
        // 10 a month plus a 120 annual: the average is 20, but January is 130.
        val subs = listOf(
            sub("monthly", 10.0, "2025-01-05"),
            sub("annual", 120.0, "2024-01-12", BillingCycle.ANNUALLY)
        )
        val months = buildBillingForecast(subs, "USD", Locale.US, today)
        assertEquals(130.0, months.single { it.label == "Jan" }.amount, 0.0001)
        assertEquals(10.0, months.single { it.label == "Sep" }.amount, 0.0001)
    }

    @Test
    fun `a subscription that has not started yet only counts from its first bill`() {
        val months = buildBillingForecast(
            listOf(sub("future", 5.0, "2026-11-20")), "USD", Locale.US, today
        )
        assertEquals(0.0, months.single { it.label == "Sep" }.amount, 0.0001)
        assertEquals(0.0, months.single { it.label == "Oct" }.amount, 0.0001)
        assertEquals(5.0, months.single { it.label == "Nov" }.amount, 0.0001)
        assertEquals(5.0, months.single { it.label == "Dec" }.amount, 0.0001)
    }

    @Test
    fun `a quarterly plan charges twice in six months`() {
        val months = buildBillingForecast(
            listOf(sub("q", 30.0, "2025-10-02", BillingCycle.QUARTERLY)),
            "USD", Locale.US, today
        )
        assertEquals(2, months.count { it.charges > 0 })
        assertEquals(60.0, months.sumOf { it.amount }, 0.0001)
    }

    @Test
    fun `a charge earlier this month still counts toward this month`() {
        // Billed on the 1st, today is the 8th: the money is gone, but the
        // month's total is what the bar is claiming to show.
        val months = buildBillingForecast(
            listOf(sub("early", 7.0, "2025-04-01")), "USD", Locale.US, today
        )
        assertEquals(7.0, months.first().amount, 0.0001)
    }

    @Test
    fun `an unparseable first bill date falls back to the next bill date`() {
        val broken = sub("broken", 4.0, "not-a-date").copy(nextBillDate = "2026-10-06")
        val months = buildBillingForecast(listOf(broken), "USD", Locale.US, today)
        assertEquals(4.0, months.single { it.label == "Oct" }.amount, 0.0001)
    }
}
