package org.dpdns.alwaysup.subflow

import org.dpdns.alwaysup.subflow.data.repository.SubscriptionRepository
import org.dpdns.alwaysup.subflow.domain.model.BillingCycle
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.model.TrialOutcome
import org.dpdns.alwaysup.subflow.domain.util.Trials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * What every write agrees on before it reaches the database.
 *
 * The rule that matters here is which subscriptions count as trials. A trial
 * that has been answered - converted or cancelled - is not one any more, and
 * treating it as one pins its renewal date to a day that has already passed.
 */
class WriteNormalisationTest {

    private val today = LocalDate.of(2026, 9, 18)

    private fun sub(
        isTrial: Boolean = false,
        outcome: TrialOutcome = TrialOutcome.PENDING,
        amount: Double = 12.99,
        firstBill: LocalDate = today.minusDays(40),
        trialEnd: LocalDate? = null
    ) = Subscription(
        id = "s1",
        name = "Figma Pro",
        category = "Productivity",
        amount = amount,
        currency = "USD",
        cycle = BillingCycle.MONTHLY,
        firstBillDate = firstBill.toString(),
        nextBillDate = firstBill.toString(),
        isTrial = isTrial,
        trialEndDate = trialEnd?.toString() ?: "",
        trialOutcome = outcome,
        postTrialAmount = 12.99
    )

    private fun normalise(s: Subscription) =
        SubscriptionRepository.normaliseForWrite(s, today)

    // ------------------------------------------------------- a running trial

    @Test
    fun `a pending trial is dated by its ending and costs nothing`() {
        val end = today.plusDays(6)
        val out = normalise(sub(isTrial = true, trialEnd = end))

        assertEquals(end.toString(), out.nextBillDate)
        assertEquals(0.0, out.amount, 0.0001)
    }

    @Test
    fun `a pending trial with no end date falls back to its start`() {
        val start = today.minusDays(3)
        val out = normalise(sub(isTrial = true, firstBill = start, trialEnd = null))

        assertEquals(start.toString(), out.nextBillDate)
    }

    // ----------------------------------------------------- an answered trial

    @Test
    fun `a cancelled trial is no longer dated by a trial end that has passed`() {
        // The bug: cancel left isTrial true, so every later write pinned the
        // renewal date back to trialEndDate. Resuming one then showed
        // "Overdue, $0.00" forever, because rollForwardDueRenewals skips
        // trials and would never advance it.
        val cancelled = Trials.cancel(
            sub(isTrial = true, trialEnd = today.minusDays(12))
        )
        val out = normalise(cancelled)

        assertTrue(
            "a resolved trial must be dated into the future, was ${out.nextBillDate}",
            LocalDate.parse(out.nextBillDate).isAfter(today)
        )
    }

    @Test
    fun `a converted trial is dated from the end of the trial it grew out of`() {
        val end = today.minusDays(12)
        val converted = Trials.convertToPaid(
            sub(isTrial = true, amount = 0.0, trialEnd = end)
        )
        val out = normalise(converted)

        assertEquals(12.99, out.amount, 0.0001)
        assertTrue(LocalDate.parse(out.nextBillDate).isAfter(today))
        // Billing runs from the trial's end, so the first charge is the one the
        // user was warned about: a month on from the 6th, not from today.
        assertEquals(end.plusMonths(1).toString(), out.nextBillDate)
    }

    // -------------------------------------------------- an ordinary one

    @Test
    fun `an ordinary subscription is rolled forward to its next occurrence`() {
        val out = normalise(sub(firstBill = today.minusDays(40)))

        assertTrue(LocalDate.parse(out.nextBillDate).isAfter(today))
        assertEquals(12.99, out.amount, 0.0001)
    }

    @Test
    fun `a subscription that has not started yet keeps its first bill date`() {
        val start = today.plusDays(10)
        val out = normalise(sub(firstBill = start))

        assertEquals(start.toString(), out.nextBillDate)
    }
}
