package org.dpdns.alwaysup.subflow.domain.util

import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.model.TrialOutcome
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * What today's scan owes the user about one trial.
 *
 * @param announce the lead time to raise a notification for, or null when
 *   nothing should be sent.
 * @param retire every lead settled by this decision, announced or not. Once a
 *   lead is retired it never fires again for this trial end date, which is what
 *   keeps a reboot or a time-zone change from delivering it twice.
 */
data class TrialReminderDecision(
    val announce: Int?,
    val retire: Set<Int>
) {
    companion object {
        val Nothing = TrialReminderDecision(announce = null, retire = emptySet())
    }
}

/**
 * Trial timing and the three things that can become of one.
 *
 * Deliberately free of Android types: the reminder rules are the part of this
 * feature that is hard to get right and easy to get wrong silently, so they are
 * decided here and merely delivered by the worker.
 */
object Trials {

    /** Lead times in days before the trial ends. */
    val DefaultLeads = setOf(7, 3, 1)

    /** Days from [today] until the trial ends; null when the date is unusable. */
    fun daysRemaining(subscription: Subscription, today: LocalDate = LocalDate.now()): Long? {
        val end = DateCalculators.parseOrNull(subscription.trialEndDate) ?: return null
        return ChronoUnit.DAYS.between(today, end)
    }

    /**
     * The key this trial's reminder state is stored under.
     *
     * Includes the end date, so moving the date is itself the rescheduling: the
     * old marks are addressed to a date that no longer exists and the leads for
     * the new one start clean. Nothing has to be cancelled.
     */
    fun stateKey(subscription: Subscription): String =
        "trial_${subscription.id}_${subscription.trialEndDate}"

    /**
     * @param retired leads already settled for this trial end date.
     * @param firstSight whether this is the first scan ever to see this trial
     *   at this end date.
     */
    fun decide(
        subscription: Subscription,
        leads: Set<Int> = DefaultLeads,
        retired: Set<Int> = emptySet(),
        firstSight: Boolean = false,
        today: LocalDate = LocalDate.now()
    ): TrialReminderDecision {
        if (!subscription.isTrialPending) return TrialReminderDecision.Nothing
        if (subscription.reminderDaysBefore <= 0) return TrialReminderDecision.Nothing

        val remaining = daysRemaining(subscription, today) ?: return TrialReminderDecision.Nothing
        if (remaining < 0) return TrialReminderDecision.Nothing

        // Due once the trial is *within* the lead rather than exactly on it. A
        // phone that was off from day 7 to day 2 still owes the warning it slept
        // through, and an equality test drops that one silently - which for this
        // feature means the charge it existed to prevent.
        val due = leads.filter { it > 0 && remaining <= it && it !in retired }.toSet()
        if (due.isEmpty()) return TrialReminderDecision.Nothing

        // The first scan to see a trial settles whatever is already due without
        // a word. The user entered this end date moments ago, so announcing it
        // back to them is noise - and this is what "schedule only the times
        // still in the future" means for a trial added close to its end.
        if (firstSight) return TrialReminderDecision(announce = null, retire = due)

        // Catching up says the most urgent thing once, rather than stacking
        // three notifications into one morning.
        return TrialReminderDecision(announce = due.min(), retire = due)
    }

    /**
     * The trial became a paying subscription.
     *
     * The post-trial price and cycle move into the fields every total already
     * reads, and billing starts at the trial's end date, so the first charge is
     * the one the user was warned about rather than one dated today.
     */
    fun convertToPaid(subscription: Subscription): Subscription = subscription.copy(
        isTrial = false,
        trialOutcome = TrialOutcome.CONVERTED,
        amount = subscription.postTrialAmount,
        cycle = subscription.postTrialCycle,
        firstBillDate = subscription.trialEndDate.ifBlank { subscription.firstBillDate },
        isActive = true
    )

    /**
     * The trial ended without becoming a paid plan.
     *
     * The row is kept rather than deleted: it is a true part of the user's
     * history, and being inactive already keeps it out of every total.
     */
    fun cancel(subscription: Subscription): Subscription = subscription.copy(
        trialOutcome = TrialOutcome.CANCELLED,
        isActive = false
    )

    /**
     * The trial was extended.
     *
     * Only the end date moves - see [TrialOutcome] for why this is not an
     * outcome of its own. The reminders re-arm by themselves because they are
     * keyed by that date; there is no queue here to rebuild.
     */
    fun extendTo(subscription: Subscription, newEndDate: String): Subscription =
        subscription.copy(trialEndDate = newEndDate, nextBillDate = newEndDate)
}
