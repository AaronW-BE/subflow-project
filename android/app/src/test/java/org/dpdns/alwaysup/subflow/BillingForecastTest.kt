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

    private fun trial(
        id: String,
        postTrialAmount: Double,
        trialEndDate: String,
        converts: Boolean = true,
        postTrialCycle: BillingCycle = BillingCycle.MONTHLY
    ) = Subscription(
        id = id,
        name = id,
        // As stored: a running trial costs nothing today.
        amount = 0.0,
        currency = "USD",
        cycle = BillingCycle.MONTHLY,
        firstBillDate = "2026-09-01",
        nextBillDate = trialEndDate,
        isTrial = true,
        trialEndDate = trialEndDate,
        trialConverts = converts,
        postTrialAmount = postTrialAmount,
        postTrialCycle = postTrialCycle
    )

    // ------------------------------------------------------------------ trials

    @Test
    fun `a trial bills nothing until it ends, then bills its real price`() {
        // The charge after a free month is exactly what someone consults a
        // forecast to find, and it is the one the smoothed numbers cannot show:
        // this subscription reads as 0 everywhere else in the app.
        val months = buildBillingForecast(
            listOf(trial("t", 20.0, "2026-11-15")), "USD", Locale.US, today
        )
        assertEquals("Sep", months[0].label)
        assertEquals(0.0, months[0].amount, 0.0001)
        assertEquals(0.0, months[1].amount, 0.0001)
        assertEquals("first charge lands the month the trial ends", 20.0, months[2].amount, 0.0001)
        assertEquals(1, months[2].charges)
        assertEquals("and recurs after that", 20.0, months[3].amount, 0.0001)
    }

    @Test
    fun `a trial that simply stops never appears in the forecast`() {
        val months = buildBillingForecast(
            listOf(trial("t", 20.0, "2026-11-15", converts = false)), "USD", Locale.US, today
        )
        assertTrue(months.all { it.amount == 0.0 && it.charges == 0 })
    }

    @Test
    fun `a trial is walked on the cycle it converts to, not the one it carries`() {
        // cycle is MONTHLY on the stored row; the plan it becomes is annual, so
        // it must land once rather than in every month of the window.
        val months = buildBillingForecast(
            listOf(trial("t", 120.0, "2026-10-05", postTrialCycle = BillingCycle.ANNUALLY)),
            "USD", Locale.US, today
        )
        assertEquals(1, months.count { it.charges > 0 })
        assertEquals("Oct", months.first { it.charges > 0 }.label)
        assertEquals(120.0, months.first { it.charges > 0 }.amount, 0.0001)
    }

    @Test
    fun `a converted trial is forecast like any other paid subscription`() {
        val converted = org.dpdns.alwaysup.subflow.domain.util.Trials
            .convertToPaid(trial("t", 20.0, "2026-10-05"))
        val months = buildBillingForecast(listOf(converted), "USD", Locale.US, today)
        assertEquals(0.0, months[0].amount, 0.0001)
        assertEquals(20.0, months[1].amount, 0.0001)
        assertEquals(20.0, months[2].amount, 0.0001)
    }

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
