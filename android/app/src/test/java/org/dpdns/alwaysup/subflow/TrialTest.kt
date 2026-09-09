package org.dpdns.alwaysup.subflow

import org.dpdns.alwaysup.subflow.domain.model.BillingCycle
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.model.TrialOutcome
import org.dpdns.alwaysup.subflow.domain.util.TrialReminderDecision
import org.dpdns.alwaysup.subflow.domain.util.Trials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * A missed trial reminder is not a cosmetic failure: it is the surprise charge
 * the feature exists to prevent, and it fails silently. These tests are mostly
 * about the ways a reminder can go missing or arrive twice.
 */
class TrialTest {

    private val today = LocalDate.of(2026, 9, 9)

    private fun trial(
        endDate: String,
        outcome: TrialOutcome = TrialOutcome.PENDING,
        reminderDaysBefore: Int = 1,
        converts: Boolean = true
    ) = Subscription(
        id = "sub_1",
        name = "Netflix",
        amount = 0.0,
        firstBillDate = "2026-09-01",
        nextBillDate = endDate,
        reminderDaysBefore = reminderDaysBefore,
        isTrial = true,
        trialEndDate = endDate,
        trialConverts = converts,
        postTrialAmount = 15.49,
        postTrialCycle = BillingCycle.MONTHLY,
        trialOutcome = outcome
    )

    // ------------------------------------------------------------ nothing owed

    @Test
    fun `a paid subscription is never a trial reminder`() {
        val paid = trial("2026-09-12").copy(isTrial = false)
        assertEquals(TrialReminderDecision.Nothing, Trials.decide(paid, today = today))
    }

    @Test
    fun `turning reminders off for the row silences the trial too`() {
        val decision = Trials.decide(trial("2026-09-12", reminderDaysBefore = 0), today = today)
        assertNull(decision.announce)
        assertTrue(decision.retire.isEmpty())
    }

    @Test
    fun `a resolved trial says nothing further`() {
        for (outcome in listOf(TrialOutcome.CONVERTED, TrialOutcome.CANCELLED)) {
            val decision = Trials.decide(trial("2026-09-12", outcome = outcome), today = today)
            assertNull("outcome $outcome", decision.announce)
        }
    }

    @Test
    fun `a trial past its end date is no longer reminded about`() {
        assertNull(Trials.decide(trial("2026-09-08"), today = today).announce)
    }

    @Test
    fun `an unparseable end date is ignored rather than crashing the scan`() {
        assertNull(Trials.decide(trial("not-a-date"), today = today).announce)
    }

    @Test
    fun `disabling every lead disables trial reminders`() {
        val decision = Trials.decide(trial("2026-09-10"), leads = emptySet(), today = today)
        assertNull(decision.announce)
    }

    @Test
    fun `a lead that is still in the future is not announced early`() {
        // 10 days out: nothing is due yet, not even the 7-day lead.
        val decision = Trials.decide(trial("2026-09-19"), today = today)
        assertNull(decision.announce)
        assertTrue(decision.retire.isEmpty())
    }

    // ------------------------------------------------------------ the happy path

    @Test
    fun `the seven day lead fires on the seventh day`() {
        val decision = Trials.decide(trial("2026-09-16"), today = today)
        assertEquals(7, decision.announce)
        assertEquals(setOf(7), decision.retire)
    }

    @Test
    fun `a trial ending today still gets its warning`() {
        val decision = Trials.decide(trial("2026-09-09"), retired = setOf(7, 3), today = today)
        assertEquals(1, decision.announce)
    }

    // ------------------------------------------------------ at most once, ever

    @Test
    fun `a retired lead never fires again`() {
        // The mark is permanent rather than per-day, so a reboot or a time-zone
        // change that re-runs the scan on the same trial delivers nothing more.
        val decision = Trials.decide(trial("2026-09-16"), retired = setOf(7), today = today)
        assertNull(decision.announce)
        assertTrue(decision.retire.isEmpty())
    }

    // -------------------------------------------------- the gap that swallowed one

    @Test
    fun `a scan that missed days announces the most urgent lead it slept through`() {
        // Phone off from day 7 to day 2. An equality test against the lead would
        // have matched nothing on either day and dropped both warnings without a
        // trace; the user's next contact with the trial would be the charge.
        val decision = Trials.decide(trial("2026-09-11"), today = today)
        assertEquals("announces once, not three times", 3, decision.announce)
        assertEquals("and settles what it slept through", setOf(7, 3), decision.retire)
    }

    @Test
    fun `first sight of a trial added close to its end stays quiet`() {
        // The user typed this date a moment ago. Retiring the passed leads
        // without a word is what stops an immediate notification, while the
        // 1-day warning is still ahead of them.
        val decision = Trials.decide(trial("2026-09-11"), firstSight = true, today = today)
        assertNull(decision.announce)
        assertEquals(setOf(7, 3), decision.retire)
        // ...and the day before it ends, the remaining lead does fire.
        val next = Trials.decide(
            trial("2026-09-11"),
            retired = decision.retire,
            today = LocalDate.of(2026, 9, 10)
        )
        assertEquals(1, next.announce)
    }

    @Test
    fun `moving the end date starts the reminders over`() {
        val short = trial("2026-09-11")
        val extended = Trials.extendTo(short, "2026-10-09")
        // The state is stored under a key that names the date, so the marks made
        // against the old one do not reach the new one.
        assertTrue(Trials.stateKey(short) != Trials.stateKey(extended))
        assertEquals("2026-10-09", extended.trialEndDate)
        assertEquals("2026-10-09", extended.nextBillDate)
        assertTrue("still a running trial", extended.isTrialPending)
    }

    // ------------------------------------------------------------- the outcomes

    @Test
    fun `converting moves the post-trial price into the fields totals read`() {
        val converted = Trials.convertToPaid(trial("2026-09-16"))
        assertEquals(15.49, converted.amount, 0.001)
        assertEquals(BillingCycle.MONTHLY, converted.cycle)
        assertEquals("billing starts when the trial ended", "2026-09-16", converted.firstBillDate)
        assertTrue(!converted.isTrial)
        assertEquals(TrialOutcome.CONVERTED, converted.trialOutcome)
        assertEquals(15.49, converted.monthlyAmount, 0.001)
    }

    @Test
    fun `cancelling keeps the row but takes it out of every total`() {
        val cancelled = Trials.cancel(trial("2026-09-16"))
        assertTrue(!cancelled.isActive)
        assertEquals(TrialOutcome.CANCELLED, cancelled.trialOutcome)
        assertTrue("no longer awaiting an answer", !cancelled.isTrialPending)
    }

    @Test
    fun `a trial that ends without a charge advertises no upcoming amount`() {
        assertNull(trial("2026-09-16", converts = false).postTrialCharge)
        assertEquals(15.49, trial("2026-09-16").postTrialCharge!!, 0.001)
    }
}
