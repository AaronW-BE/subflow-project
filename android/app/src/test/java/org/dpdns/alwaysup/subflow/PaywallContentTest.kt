package org.dpdns.alwaysup.subflow

import org.dpdns.alwaysup.subflow.domain.util.DateCalculators
import org.dpdns.alwaysup.subflow.ui.screens.paywall.PaywallContent
import org.dpdns.alwaysup.subflow.ui.screens.paywall.PaywallReason
import org.dpdns.alwaysup.subflow.ui.screens.paywall.ProFeatureKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** What the paywall leads with, and the dates it names (BIN-74). */
class PaywallContentTest {

    @Test
    fun `opened from nowhere in particular, earlier alerts come first`() {
        assertEquals(ProFeatureKey.ALERTS, PaywallContent.featureOrder(PaywallReason.GENERAL).first())
    }

    @Test
    fun `each entry point leads with the feature it was about`() {
        mapOf(
            PaywallReason.LIMIT to ProFeatureKey.UNLIMITED,
            PaywallReason.ALERTS to ProFeatureKey.ALERTS,
            PaywallReason.BUDGET to ProFeatureKey.BUDGET,
            PaywallReason.ANALYTICS to ProFeatureKey.ANALYTICS
        ).forEach { (reason, lead) ->
            assertEquals(reason.name, lead, PaywallContent.featureOrder(reason).first())
        }
    }

    @Test
    fun `whatever leads, every feature is listed once`() {
        PaywallReason.entries.forEach { reason ->
            val order = PaywallContent.featureOrder(reason)
            assertEquals(reason.name, ProFeatureKey.entries.toSet(), order.toSet())
            assertEquals(reason.name, ProFeatureKey.entries.size, order.size)
        }
    }

    @Test
    fun `a 7-day trial bought today is charged a week today`() {
        assertEquals(
            LocalDate.of(2026, 10, 28),
            PaywallContent.trialEnds(LocalDate.of(2026, 10, 21), 7)
        )
    }

    // ------------------------------------------------------- reminder date

    private val today = LocalDate.of(2026, 10, 21)

    @Test
    fun `a reminder lands the given number of days before the charge`() {
        assertEquals(LocalDate.of(2026, 11, 18), DateCalculators.reminderDate("2026-11-21", 3, today))
        assertEquals(LocalDate.of(2026, 11, 14), DateCalculators.reminderDate("2026-11-21", 7, today))
    }

    @Test
    fun `no date when that day is today or already gone`() {
        assertNull(DateCalculators.reminderDate("2026-10-24", 3, today))
        assertNull(DateCalculators.reminderDate("2026-10-23", 3, today))
    }

    @Test
    fun `no date from a date that cannot be read`() {
        assertNull(DateCalculators.reminderDate("", 3, today))
    }
}
