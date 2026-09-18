package org.dpdns.alwaysup.subflow

import org.dpdns.alwaysup.subflow.ui.screens.analytics.wholePercentages
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The category breakdown's percentages.
 *
 * Truncated one by one they came to 98% on four categories. Whatever else a
 * breakdown gets wrong, it must add up to the whole it is breaking down.
 */
class WholePercentagesTest {

    @Test
    fun `the case that shipped adds up to 100 now`() {
        // Health, Streaming, Productivity, Cloud on an $85.47 month: truncated
        // they were 57, 29, 9, 3 = 98.
        val out = wholePercentages(listOf(49.0, 25.48, 8.0, 2.99))

        assertEquals(100, out.sum())
        assertEquals(listOf(57, 30, 9, 4), out)
    }

    @Test
    fun `three equal thirds give 34, 33, 33 rather than 33 three times`() {
        val out = wholePercentages(listOf(1.0, 1.0, 1.0))

        assertEquals(100, out.sum())
        assertEquals(listOf(34, 33, 33), out)
    }

    @Test
    fun `an exact split is left exactly as it is`() {
        assertEquals(listOf(50, 25, 25), wholePercentages(listOf(2.0, 1.0, 1.0)))
    }

    @Test
    fun `a single category is the whole`() {
        assertEquals(listOf(100), wholePercentages(listOf(12.34)))
    }

    @Test
    fun `a tiny share can round to zero without breaking the total`() {
        val out = wholePercentages(listOf(1000.0, 1.0))

        assertEquals(100, out.sum())
        assertEquals(listOf(100, 0), out)
    }

    @Test
    fun `nothing to divide gives zeros, not a crash`() {
        assertEquals(emptyList<Int>(), wholePercentages(emptyList()))
        assertEquals(listOf(0, 0), wholePercentages(listOf(0.0, 0.0)))
    }
}
