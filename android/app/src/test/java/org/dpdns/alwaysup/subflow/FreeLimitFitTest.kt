package org.dpdns.alwaysup.subflow

import org.dpdns.alwaysup.subflow.data.repository.SubscriptionRepository
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which incoming subscriptions stay active under the free plan's limit.
 *
 * Restoring a backup used to insert everything in the file, so a free user
 * could get past the limit with a hand-written one. What does not fit now
 * comes in paused rather than being dropped.
 */
class FreeLimitFitTest {

    private fun sub(id: String, active: Boolean = true) =
        Subscription(
            id = id,
            name = id,
            amount = 9.99,
            firstBillDate = "2026-01-01",
            nextBillDate = "2026-11-01",
            isActive = active
        )

    private fun fit(
        incoming: List<Subscription>,
        onDevice: Set<String> = emptySet(),
        isPro: Boolean = false
    ) = SubscriptionRepository.fitToFreeLimit(incoming, onDevice, isPro)

    private fun activeIds(fit: org.dpdns.alwaysup.subflow.data.repository.LimitFit) =
        fit.subscriptions.filter { it.isActive }.map { it.id }

    @Test
    fun `a free plan keeps the first five of a larger backup active`() {
        val result = fit((1..8).map { sub("s$it") })
        assertEquals(listOf("s1", "s2", "s3", "s4", "s5"), activeIds(result))
        assertEquals(3, result.pausedByLimit)
        // Nothing is lost: all eight are still written.
        assertEquals(8, result.subscriptions.size)
    }

    @Test
    fun `subscriptions already active on the device take their slots first`() {
        val result = fit((1..4).map { sub("new$it") }, onDevice = setOf("a", "b", "c"))
        assertEquals(listOf("new1", "new2"), activeIds(result))
        assertEquals(2, result.pausedByLimit)
    }

    @Test
    fun `a restore onto the same phone does not count its own rows twice`() {
        // The device already has s1..s5 active; the backup holds the same five.
        val onDevice = (1..5).map { "s$it" }.toSet()
        val result = fit((1..5).map { sub("s$it") }, onDevice)
        assertEquals(0, result.pausedByLimit)
        assertEquals(5, activeIds(result).size)
    }

    @Test
    fun `rows that were paused in the file stay paused and take no slot`() {
        val incoming = listOf(sub("p1", active = false)) + (1..5).map { sub("s$it") }
        val result = fit(incoming)
        assertEquals((1..5).map { "s$it" }, activeIds(result))
        assertEquals(0, result.pausedByLimit)
    }

    @Test
    fun `a full device pauses everything new`() {
        val onDevice = (1..5).map { "d$it" }.toSet()
        val result = fit(listOf(sub("x"), sub("y")), onDevice)
        assertEquals(emptyList<String>(), activeIds(result))
        assertEquals(2, result.pausedByLimit)
    }

    @Test
    fun `Pro keeps everything active`() {
        val result = fit((1..20).map { sub("s$it") }, onDevice = setOf("a", "b"), isPro = true)
        assertEquals(20, activeIds(result).size)
        assertEquals(0, result.pausedByLimit)
    }
}
