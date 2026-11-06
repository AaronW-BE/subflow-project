package org.dpdns.alwaysup.subflow.domain.util

import org.dpdns.alwaysup.subflow.domain.model.Subscription
import java.time.ZonedDateTime

object FinalDayTrialChecks {
    fun key(sub: Subscription): String = "trial-final:${sub.id}:${sub.trialEndDate}"

    fun target(sub: Subscription, enabled: Boolean, minuteOfDay: Int, now: ZonedDateTime): ZonedDateTime? {
        if (!enabled || !sub.isActive || sub.isDeleted || !sub.isTrialPending ||
            !sub.trialConverts || sub.isTrialKept || sub.reminderDaysBefore <= 0) return null
        val date = runCatching { java.time.LocalDate.parse(sub.trialEndDate) }.getOrNull() ?: return null
        if (date < now.toLocalDate()) return null
        val minute = minuteOfDay.coerceIn(0, 1439)
        return date.atTime(minute / 60, minute % 60).atZone(now.zone)
    }

    fun isDue(sub: Subscription, enabled: Boolean, minuteOfDay: Int, now: ZonedDateTime): Boolean =
        target(sub, enabled, minuteOfDay, now)?.let { !it.isAfter(now) } == true
}
