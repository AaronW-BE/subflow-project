package org.dpdns.alwaysup.subflow

import org.dpdns.alwaysup.subflow.domain.model.BillingCycle
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.widget.summariseForWidget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * What the home screen widget says.
 *
 * The widget must not contradict the dashboard, so these mirror the rules the
 * dashboard applies: active only, every cycle normalised to a month, and the
 * next renewal is simply the soonest date.
 *
 * Amounts are all in one currency on purpose - conversion is
 * `CurrencyConverter`'s job and has its own tests, and pinning a rate here
 * would only test the fallback table.
 */
class WidgetSummaryTest {

    private fun sub(
        name: String,
        amount: Double = 10.0,
        cycle: BillingCycle = BillingCycle.MONTHLY,
        daysAway: Long = 10,
        active: Boolean = true,
        deleted: Boolean = false,
        trial: Boolean = false
    ) = Subscription(
        id = name.lowercase(),
        name = name,
        amount = if (trial) 0.0 else amount,
        currency = "USD",
        cycle = cycle,
        firstBillDate = LocalDate.now().minusMonths(2).toString(),
        nextBillDate = LocalDate.now().plusDays(daysAway).toString(),
        isActive = active,
        isDeleted = deleted,
        isTrial = trial,
        trialEndDate = if (trial) LocalDate.now().plusDays(daysAway).toString() else "",
    )

    @Test
    fun `every cycle is normalised to a month`() {
        val s = summariseForWidget(
            listOf(
                sub("Monthly", amount = 10.0, cycle = BillingCycle.MONTHLY),
                sub("Annual", amount = 120.0, cycle = BillingCycle.ANNUALLY)
            ),
            "USD"
        )
        assertEquals(20.0, s.monthlyTotal, 0.01)
        assertEquals(2, s.activeCount)
    }

    @Test
    fun `paused and deleted subscriptions are not in the total`() {
        val s = summariseForWidget(
            listOf(
                sub("Active", amount = 10.0),
                sub("Paused", amount = 99.0, active = false),
                sub("Gone", amount = 99.0, deleted = true)
            ),
            "USD"
        )
        assertEquals(10.0, s.monthlyTotal, 0.01)
        assertEquals(1, s.activeCount)
        assertEquals("Active", s.next?.name)
    }

    @Test
    fun `the next renewal is the soonest one`() {
        val s = summariseForWidget(
            listOf(
                sub("Later", daysAway = 20),
                sub("Sooner", daysAway = 2),
                sub("Latest", daysAway = 40)
            ),
            "USD"
        )
        assertEquals("Sooner", s.next?.name)
        assertEquals(2L, s.next?.daysLeft)
    }

    @Test
    fun `a trial counts as the next event and costs nothing`() {
        // Its end is its next dated event, which is exactly what the widget is
        // pointing at; the repository writes a trial's amount as zero.
        val s = summariseForWidget(
            listOf(sub("Paid", amount = 10.0, daysAway = 20), sub("Trying", trial = true, daysAway = 3)),
            "USD"
        )
        assertEquals(10.0, s.monthlyTotal, 0.01)
        assertEquals("Trying", s.next?.name)
        assertTrue("a trial is labelled as one", s.next?.isTrial == true)
    }

    @Test
    fun `an overdue renewal still sorts first`() {
        val s = summariseForWidget(
            listOf(sub("Upcoming", daysAway = 5), sub("Overdue", daysAway = -3)),
            "USD"
        )
        assertEquals("Overdue", s.next?.name)
        assertTrue("and reads as overdue", (s.next?.daysLeft ?: 0) < 0)
    }

    @Test
    fun `nothing to show is not an error`() {
        val empty = summariseForWidget(emptyList(), "USD")
        assertEquals(0.0, empty.monthlyTotal, 0.0)
        assertEquals(0, empty.activeCount)
        assertNull(empty.next)

        val allPaused = summariseForWidget(listOf(sub("Paused", active = false)), "USD")
        assertEquals(0, allPaused.activeCount)
        assertNull(allPaused.next)
    }

    @Test
    fun `the currency it reports is the one it was asked for`() {
        assertEquals("EUR", summariseForWidget(emptyList(), "EUR").currency)
    }
}
