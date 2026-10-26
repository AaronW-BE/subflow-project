package org.dpdns.alwaysup.subflow

import org.dpdns.alwaysup.subflow.data.billing.BillingManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A failed acknowledgement is retried on its own, with a growing wait (BIN-80). */
class AckRetryTest {

    @Test
    fun `the wait doubles from two seconds`() {
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 32_000L),
            (0..4).map { BillingManager.ackRetryDelayMs(it) })
    }

    @Test
    fun `and never passes a minute`() {
        for (attempt in 5..40) assertEquals(60_000L, BillingManager.ackRetryDelayMs(attempt))
    }

    @Test
    fun `all the tries fit well inside even a test purchase's window`() {
        // A license tester's unacknowledged purchase is refunded within minutes,
        // so the retries have to be done well before that.
        val total = (0 until BillingManager.MAX_ACK_ATTEMPTS - 1).sumOf { BillingManager.ackRetryDelayMs(it) }
        assertTrue("$total ms", total <= 3 * 60_000L)
    }
}
