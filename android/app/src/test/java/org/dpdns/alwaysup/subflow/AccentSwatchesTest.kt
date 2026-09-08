package org.dpdns.alwaysup.subflow

import org.dpdns.alwaysup.subflow.ui.screens.add.accentSwatches
import org.dpdns.alwaysup.subflow.ui.theme.ApplePalette
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The bug this replaces was invisible in the picker itself: the row rendered
 * perfectly, it simply had nothing selected, and the reason was that a preset's
 * brand colour is almost never one of the ten swatches on offer - 31 of the 34
 * bundled presets, when this was written.
 */
class AccentSwatchesTest {

    @Test
    fun `a colour already in the palette is not repeated`() {
        val inPalette = ApplePalette.first()
        assertEquals(ApplePalette, accentSwatches(inPalette))
        assertEquals(ApplePalette, accentSwatches(inPalette.lowercase()))
    }

    @Test
    fun `a brand colour outside the palette is offered first`() {
        val swatches = accentSwatches("#E50914")
        assertEquals("#E50914", swatches.first())
        assertEquals(ApplePalette.size + 1, swatches.size)
        assertEquals(ApplePalette, swatches.drop(1))
    }

    @Test
    fun `no brand colour leaves the palette alone`() {
        assertEquals(ApplePalette, accentSwatches(null))
        assertEquals(ApplePalette, accentSwatches(""))
        assertEquals(ApplePalette, accentSwatches("   "))
    }
}
