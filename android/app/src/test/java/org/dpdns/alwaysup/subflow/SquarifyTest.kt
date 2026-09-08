package org.dpdns.alwaysup.subflow

import org.dpdns.alwaysup.subflow.ui.screens.analytics.squarify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A treemap is only worth drawing if area really is proportional to value, and
 * that is exactly the property nobody notices is broken - the picture looks
 * fine either way. These check the arithmetic the eye cannot.
 */
class SquarifyTest {

    private val width = 400f
    private val height = 200f

    private fun rectsFor(values: List<Double>) = squarify(values, width, height)

    @Test
    fun `every value gets a rectangle, in the order it was given`() {
        val rects = rectsFor(listOf(6.0, 3.0, 2.0, 1.0))
        assertEquals(4, rects.size)
        // Values descend, so areas must descend too.
        val areas = rects.map { it.width * it.height }
        assertTrue(areas.zipWithNext().all { (a, b) -> a >= b - 0.01f })
    }

    @Test
    fun `area is proportional to value`() {
        val values = listOf(50.0, 25.0, 15.0, 10.0)
        val rects = rectsFor(values)
        val total = values.sum()
        rects.forEachIndexed { index, rect ->
            val expected = (values[index] / total) * width * height
            assertEquals(expected, (rect.width * rect.height).toDouble(), expected * 0.02)
        }
    }

    @Test
    fun `the rectangles fill the box and do not overlap`() {
        val values = listOf(40.0, 22.0, 18.0, 12.0, 5.0, 3.0)
        val rects = rectsFor(values)

        val covered = rects.sumOf { (it.width * it.height).toDouble() }
        assertEquals((width * height).toDouble(), covered, (width * height) * 0.01)

        rects.forEach { r ->
            assertTrue("left edge", r.x >= -0.01f)
            assertTrue("top edge", r.y >= -0.01f)
            assertTrue("right edge", r.x + r.width <= width + 0.01f)
            assertTrue("bottom edge", r.y + r.height <= height + 0.01f)
        }

        for (i in rects.indices) {
            for (j in i + 1 until rects.size) {
                val a = rects[i]
                val b = rects[j]
                val overlapX = min(a.x + a.width, b.x + b.width) - max(a.x, b.x)
                val overlapY = min(a.y + a.height, b.y + b.height) - max(a.y, b.y)
                assertTrue(
                    "rect $i overlaps rect $j",
                    overlapX <= 0.01f || overlapY <= 0.01f
                )
            }
        }
    }

    @Test
    fun `tiles stay closer to square than a naive slicing would`() {
        // Six equal values sliced into strips would each be 66x200, an aspect
        // of 3. Squarified they should be far better than that.
        val rects = rectsFor(List(6) { 1.0 })
        val worst = rects.maxOf { max(it.width / it.height, it.height / it.width) }
        assertTrue("worst aspect was $worst", worst < 2.0f)
    }

    @Test
    fun `a single value takes the whole box`() {
        val rect = rectsFor(listOf(9.0)).single()
        assertEquals(0f, rect.x, 0.01f)
        assertEquals(0f, rect.y, 0.01f)
        assertEquals(width, rect.width, 0.01f)
        assertEquals(height, rect.height, 0.01f)
    }

    @Test
    fun `degenerate input produces nothing rather than infinities`() {
        assertTrue(squarify(emptyList(), width, height).isEmpty())
        assertTrue(squarify(listOf(1.0), 0f, height).isEmpty())
        assertTrue(squarify(listOf(0.0, 0.0), width, height).isEmpty())
        assertTrue(rectsFor(listOf(5.0, 1.0)).all { abs(it.width * it.height) > 0f })
    }
}
