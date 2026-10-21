package org.dpdns.alwaysup.subflow

import org.dpdns.alwaysup.subflow.ui.screens.analytics.lockedCoverAlpha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A locked chart stays a hint on phones that cannot blur it (BIN-79). */
class LockedCoverTest {

    @Test
    fun `Android 12 and later keep the light cover over the blur`() {
        assertEquals(0.72f, lockedCoverAlpha(31), 0f)
        assertEquals(0.72f, lockedCoverAlpha(36), 0f)
    }

    @Test
    fun `below 12, where nothing is blurred, the cover is nearly solid`() {
        for (sdk in 26..30) {
            assertTrue("API $sdk", lockedCoverAlpha(sdk) >= 0.9f)
        }
    }
}
