package org.dpdns.alwaysup.subflow

import org.dpdns.alwaysup.subflow.data.billing.parseIso8601PeriodDays
import org.dpdns.alwaysup.subflow.domain.model.BillingCycle
import org.dpdns.alwaysup.subflow.domain.util.CurrencyConverter
import org.dpdns.alwaysup.subflow.domain.util.CurrencyFormatter
import org.dpdns.alwaysup.subflow.domain.util.DateCalculators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/**
 * These functions decide what number the user sees next to a currency symbol,
 * so a silent error here is a trust problem rather than a cosmetic one.
 */
class MoneyAndDatesTest {

    private val today = LocalDate.of(2026, 8, 28)

    // ------------------------------------------------------------ renewal dates

    @Test
    fun `next renewal rolls a long-past start date into the future`() {
        // The original implementation added a single cycle to the first bill
        // date, so a subscription started in 2023 reported a renewal in 2023.
        val next = DateCalculators.computeNextRenewalDate("2023-01-15", BillingCycle.MONTHLY, today)
        assertEquals("2026-09-15", next)
    }

    @Test
    fun `next renewal for a future start date is the start date itself`() {
        val next = DateCalculators.computeNextRenewalDate("2026-12-01", BillingCycle.MONTHLY, today)
        assertEquals("2026-12-01", next)
    }

    @Test
    fun `annual cycle advances by whole years`() {
        val next = DateCalculators.computeNextRenewalDate("2024-03-10", BillingCycle.ANNUALLY, today)
        assertEquals("2027-03-10", next)
    }

    @Test
    fun `weekly cycle lands within a week of today`() {
        val next = DateCalculators.computeNextRenewalDate("2026-01-01", BillingCycle.WEEKLY, today)
        val parsed = LocalDate.parse(next)
        assertTrue("expected $next to be after $today", parsed.isAfter(today))
        assertTrue("expected $next within 7 days", parsed.isBefore(today.plusDays(8)))
    }

    @Test
    fun `a malformed date falls back to one month out instead of throwing`() {
        val next = DateCalculators.computeNextRenewalDate("not-a-date", BillingCycle.MONTHLY, today)
        assertEquals(today.plusMonths(1).toString(), next)
    }

    @Test
    fun `cycles elapsed counts completed billing periods`() {
        // 2026-02-28 through 2026-08-28 inclusive of the start = 7 charges.
        assertEquals(7, DateCalculators.cyclesElapsed("2026-02-28", BillingCycle.MONTHLY, today))
        assertEquals(0, DateCalculators.cyclesElapsed("2027-01-01", BillingCycle.MONTHLY, today))
    }

    @Test
    fun `period length follows the real calendar, not a flat 30 days`() {
        // Aug 28 to Sep 28 is 31 days; a hard-coded 30 made the countdown ring
        // clamp to zero on a freshly added monthly subscription.
        assertEquals(31, DateCalculators.periodLengthDays("2026-09-28", BillingCycle.MONTHLY))
        // Feb 28 back to Jan 28 is 31 days; Mar 1 back to Feb 1 is 28.
        assertEquals(28, DateCalculators.periodLengthDays("2026-03-01", BillingCycle.MONTHLY))
        assertEquals(7, DateCalculators.periodLengthDays("2026-09-04", BillingCycle.WEEKLY))
        assertEquals(365, DateCalculators.periodLengthDays("2026-09-28", BillingCycle.ANNUALLY))
    }

    @Test
    fun `period length falls back sensibly for a malformed date`() {
        assertEquals(30, DateCalculators.periodLengthDays("nonsense", BillingCycle.MONTHLY))
    }

    // ------------------------------------------------------------ normalisation

    @Test
    fun `billing cycles normalise to a comparable monthly amount`() {
        assertEquals(10.0, BillingCycle.MONTHLY.toMonthly(10.0), 0.0001)
        assertEquals(10.0, BillingCycle.ANNUALLY.toMonthly(120.0), 0.0001)
        assertEquals(10.0, BillingCycle.QUARTERLY.toMonthly(30.0), 0.0001)
        assertEquals(43.333, BillingCycle.WEEKLY.toMonthly(10.0), 0.01)
    }

    // ---------------------------------------------------------------- currency

    @Test
    fun `zero-decimal currencies render without minor units`() {
        assertEquals("¥1,200", CurrencyFormatter.format(1200.0, "JPY", Locale.US))
        assertEquals("$1,200.00", CurrencyFormatter.format(1200.0, "USD", Locale.US))
    }

    @Test
    fun `grouping follows the locale but the symbol follows the currency`() {
        // German grouping, dollar symbol.
        assertEquals("$1.234,56", CurrencyFormatter.format(1234.56, "USD", Locale.GERMANY))
    }

    @Test
    fun `an unknown currency degrades to its code rather than a wrong symbol`() {
        assertTrue(CurrencyFormatter.format(10.0, "XYZ", Locale.US).startsWith("XYZ"))
    }

    @Test
    fun `conversion is symmetric through the rate table`() {
        val eur = CurrencyConverter.convert(100.0, "USD", "EUR")
        val backToUsd = CurrencyConverter.convert(eur, "EUR", "USD")
        assertEquals(100.0, backToUsd, 0.0001)
    }

    @Test
    fun `an unknown currency is passed through unconverted`() {
        assertEquals(42.0, CurrencyConverter.convert(42.0, "XYZ", "USD"), 0.0001)
    }

    @Test
    fun `server rates replace the built-in table`() {
        // /rates returns units-per-USD, so 1 USD = 2 TESTCOIN means 10 TESTCOIN
        // is worth 5 USD.
        CurrencyConverter.updateFromUsdBase(mapOf("EUR" to 2.0))
        assertEquals(5.0, CurrencyConverter.convert(10.0, "EUR", "USD"), 0.0001)
        // Restore something sane for any test that runs afterwards.
        CurrencyConverter.updateFromUsdBase(mapOf("EUR" to 0.92))
    }

    // ------------------------------------------------------------ Play periods

    @Test
    fun `play billing periods parse into days`() {
        assertEquals(3, parseIso8601PeriodDays("P3D"))
        assertEquals(7, parseIso8601PeriodDays("P1W"))
        assertEquals(30, parseIso8601PeriodDays("P1M"))
        assertEquals(365, parseIso8601PeriodDays("P1Y"))
        assertEquals(0, parseIso8601PeriodDays(""))
        assertEquals(0, parseIso8601PeriodDays("garbage"))
    }
}
