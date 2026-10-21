package org.dpdns.alwaysup.subflow

import org.dpdns.alwaysup.subflow.data.review.ReviewPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** When the app asks for a Play rating (BIN-72). */
class ReviewPolicyTest {

    private val day = TimeUnit.DAYS.toMillis(1)
    private val now = 1_000 * day

    private fun ask(
        installedDaysAgo: Long = 30,
        launches: Int = 10,
        running: Int = 4,
        lastAskedDaysAgo: Long? = null
    ) = ReviewPolicy.shouldAsk(
        nowMillis = now,
        firstInstallMillis = now - installedDaysAgo * day,
        launchCount = launches,
        runningSubscriptions = running,
        lastAskedMillis = lastAskedDaysAgo?.let { now - it * day }
    )

    @Test
    fun `someone settled in, never asked, is asked`() = assertTrue(ask())

    @Test
    fun `not in the first week`() {
        assertFalse(ask(installedDaysAgo = 6))
        assertTrue(ask(installedDaysAgo = 7))
    }

    @Test
    fun `not before the fifth launch`() {
        assertFalse(ask(launches = 4))
        assertTrue(ask(launches = 5))
    }

    @Test
    fun `not with fewer than three running subscriptions`() {
        assertFalse(ask(running = 2))
        assertTrue(ask(running = 3))
    }

    @Test
    fun `not again within ninety days`() {
        assertFalse(ask(lastAskedDaysAgo = 89))
        assertTrue(ask(lastAskedDaysAgo = 90))
    }
}
