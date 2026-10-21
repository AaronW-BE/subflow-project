package org.dpdns.alwaysup.subflow

import org.dpdns.alwaysup.subflow.domain.util.Budget
import org.dpdns.alwaysup.subflow.domain.util.BudgetProgress
import org.dpdns.alwaysup.subflow.domain.util.CurrencyConverter
import org.dpdns.alwaysup.subflow.domain.util.MonthlyBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The monthly budget against what the subscriptions cost (BIN-68). */
class BudgetTest {

    @Test
    fun `under the limit, what is left`() {
        val p = Budget.progress(69.98, MonthlyBudget(100.0, "USD"), "USD")
        assertFalse(p.over)
        assertEquals(30.02, p.remaining, 1e-9)
        assertEquals(0.0, p.overBy, 0.0)
        assertEquals(0.6998f, p.fraction, 1e-4f)
    }

    @Test
    fun `over the limit, by how much, with the meter full`() {
        val p = Budget.progress(120.0, MonthlyBudget(100.0, "USD"), "USD")
        assertTrue(p.over)
        assertEquals(20.0, p.overBy, 1e-9)
        assertEquals(0.0, p.remaining, 0.0)
        assertEquals(1f, p.fraction, 0f)
    }

    @Test
    fun `exactly at the limit is not over`() {
        assertFalse(Budget.progress(100.0, MonthlyBudget(100.0, "USD"), "USD").over)
    }

    @Test
    fun `the annual view is a year of each side, so the meter does not move`() {
        val monthly = Budget.progress(50.0, MonthlyBudget(100.0, "USD"), "USD")
        val annual = Budget.progress(50.0, MonthlyBudget(100.0, "USD"), "USD", annual = true)
        assertEquals(600.0, annual.spent, 1e-9)
        assertEquals(1200.0, annual.limit, 1e-9)
        assertEquals(monthly.fraction, annual.fraction, 0f)
    }

    @Test
    fun `a budget set in another currency is converted, not reread`() {
        // 100 EUR set earlier, home currency now USD: the limit is 100 EUR in
        // dollars, not 100 dollars.
        val p = Budget.progress(50.0, MonthlyBudget(100.0, "EUR"), "USD")
        assertEquals(CurrencyConverter.convert(100.0, "EUR", "USD"), p.limit, 1e-9)
        assertTrue("EUR and USD differ in the rate table", p.limit != 100.0)
    }

    @Test
    fun `a zero limit reads as full rather than dividing by zero`() {
        assertEquals(1f, BudgetProgress(spent = 0.0, limit = 0.0).fraction, 0f)
    }
}
