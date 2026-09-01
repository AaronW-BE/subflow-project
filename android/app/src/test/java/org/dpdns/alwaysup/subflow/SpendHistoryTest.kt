package org.dpdns.alwaysup.subflow

import org.dpdns.alwaysup.subflow.domain.model.BillingCycle
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.ui.screens.analytics.buildSpendHistory
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/**
 * The analytics curve used to be a fixed decorative shape. It now has to
 * reflect the user's own history, so these tests pin that behaviour.
 */
class SpendHistoryTest {

    private val today = LocalDate.of(2026, 8, 28)

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
    fun `history has one point per month for the last six months`() {
        val points = buildSpendHistory(emptyList(), "USD", Locale.US, today)
        assertEquals(6, points.size)
        assertEquals("Aug", points.last().label)
        assertEquals("Mar", points.first().label)
    }

    @Test
    fun `a subscription only counts from the month it started`() {
        val subs = listOf(sub("old", 10.0, "2020-01-01"), sub("new", 5.0, "2026-07-04"))
        val points = buildSpendHistory(subs, "USD", Locale.US, today)

        // March through June: only the old one.
        assertEquals(10.0, points[0].amount, 0.0001)
        assertEquals(10.0, points[3].amount, 0.0001)
        // July onwards: both.
        assertEquals(15.0, points[4].amount, 0.0001)
        assertEquals(15.0, points[5].amount, 0.0001)
    }

    @Test
    fun `a subscription starting later this month still counts for this month`() {
        // Started on the 30th, which is after "today" but inside August: the
        // month bucket is keyed on the end of the month, not on today.
        val points = buildSpendHistory(listOf(sub("s", 9.0, "2026-08-30")), "USD", Locale.US, today)
        assertEquals(0.0, points[4].amount, 0.0001)
        assertEquals(9.0, points[5].amount, 0.0001)
    }

    @Test
    fun `non-monthly cycles are normalised into the monthly total`() {
        val subs = listOf(sub("annual", 120.0, "2020-01-01", BillingCycle.ANNUALLY))
        val points = buildSpendHistory(subs, "USD", Locale.US, today)
        assertEquals(10.0, points.last().amount, 0.0001)
    }
}
