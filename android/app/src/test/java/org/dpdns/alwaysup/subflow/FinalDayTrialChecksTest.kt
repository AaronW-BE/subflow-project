package org.dpdns.alwaysup.subflow

import org.junit.Assert.*
import org.junit.Test
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.util.FinalDayTrialChecks as Checks
import java.time.ZonedDateTime

class FinalDayTrialChecksTest {
    private val now = ZonedDateTime.parse("2026-09-12T09:00:00+02:00[Europe/Amsterdam]")
    private val sub = Subscription(id = "trial", name = "Trial", amount = 0.0,
        firstBillDate = "2026-09-01", nextBillDate = "2026-09-12",
        isTrial = true, trialEndDate = "2026-09-12")

    @Test fun dueIncludesFirstEnableCatchupButNeverPastDates() {
        assertTrue(Checks.isDue(sub, true, 540, now))
        assertTrue(Checks.isDue(sub, true, 540, now.plusHours(10)))
        assertFalse(Checks.isDue(sub, true, 541, now))
        assertNull(Checks.target(sub, true, 540, now.plusDays(1)))
        assertEquals(now.plusDays(1), Checks.target(sub.copy(trialEndDate = "2026-09-13"), true, 540, now))
    }
    @Test fun qualificationAndKeepReset() {
        for (s in listOf(sub.copy(isActive = false), sub.copy(isDeleted = true),
            sub.copy(isTrial = false), sub.copy(trialConverts = false),
            sub.copy(reminderDaysBefore = 0), sub.copy(trialKeepEndDate = sub.trialEndDate))) {
            assertNull(Checks.target(s, true, 540, now))
        }
        assertNull(Checks.target(sub, false, 540, now))
        assertTrue(Checks.isDue(sub.copy(trialKeepEndDate = "2026-09-11"), true, 540, now))
        assertEquals(Checks.key(sub), Checks.key(sub.copy(trialKeepEndDate = sub.trialEndDate)))
        assertNotEquals(Checks.key(sub), Checks.key(sub.copy(trialEndDate = "2026-09-13")))
    }
    @Test fun dstUsesCurrentDeviceZoneGapAndEarlierOverlap() {
        val gap = now.withZoneSameInstant(java.time.ZoneId.of("America/New_York"))
            .withYear(2026).withMonth(3).withDayOfMonth(8).withHour(0)
        val target = Checks.target(sub.copy(trialEndDate = "2026-03-08"), true, 150, gap)!!
        assertEquals(3, target.hour)
        assertEquals(30, target.minute)
        assertEquals(gap.zone, target.zone)
    }
}
