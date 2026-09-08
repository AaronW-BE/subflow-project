package org.dpdns.alwaysup.subflow

import androidx.compose.ui.graphics.Color
import org.dpdns.alwaysup.subflow.domain.model.BillingCycle
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.ui.screens.analytics.buildTreemapItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * squarify's packing is only as good as the descending order it is handed, and
 * nothing about the picture says when that order was broken - the tiles still
 * tile, just worse. So the invariant is pinned here rather than left to the eye.
 */
class TreemapItemsTest {

    private val other = "Everything else"
    private val otherColor = Color.Gray

    private fun sub(id: String, amount: Double, colour: String = "#112233") = Subscription(
        id = id,
        name = id,
        amount = amount,
        currency = "USD",
        cycle = BillingCycle.MONTHLY,
        firstBillDate = "2025-01-01",
        nextBillDate = "2026-10-01",
        colorHex = colour
    )

    @Test
    fun `a short list is one tile per subscription, biggest first`() {
        val items = buildTreemapItems(
            listOf(sub("a", 3.0), sub("b", 9.0), sub("c", 6.0)),
            "USD", other, otherColor
        )
        assertEquals(listOf("b", "c", "a"), items.map { it.label })
        assertTrue(items.none { it.label == other })
    }

    @Test
    fun `a long tail is pooled into one tile`() {
        val subs = (1..20).map { sub("s$it", it.toDouble()) }
        val items = buildTreemapItems(subs, "USD", other, otherColor)
        assertEquals(8, items.size)
        val pooled = items.single { it.label == other }
        // Everything below the top seven: 1 through 13.
        assertEquals((1..13).sum().toDouble(), pooled.value, 0.0001)
        assertEquals(otherColor, pooled.color)
    }

    @Test
    fun `the pooled tile sits in size order, not at the end`() {
        // Seven at 100 and a tail worth 200. Appended blind, the list handed to
        // squarify would not descend and the layout would be measurably worse.
        val subs = (1..7).map { sub("big$it", 100.0) } + (1..20).map { sub("small$it", 10.0) }
        val items = buildTreemapItems(subs, "USD", other, otherColor)

        assertEquals(200.0, items.single { it.label == other }.value, 0.0001)
        assertEquals(0, items.indexOfFirst { it.label == other })
        assertTrue(
            "values must descend, got ${items.map { it.value }}",
            items.map { it.value }.zipWithNext().all { (a, b) -> a >= b }
        )
    }

    @Test
    fun `subscriptions that cost nothing get no tile`() {
        val items = buildTreemapItems(
            listOf(sub("free", 0.0), sub("paid", 5.0)), "USD", other, otherColor
        )
        assertEquals(listOf("paid"), items.map { it.label })
    }

    @Test
    fun `an unparseable brand colour falls back rather than throwing`() {
        val items = buildTreemapItems(
            listOf(sub("broken", 5.0, colour = "not-a-colour")), "USD", other, otherColor
        )
        assertEquals(1, items.size)
    }
}
