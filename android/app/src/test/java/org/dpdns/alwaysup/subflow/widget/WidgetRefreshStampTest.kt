package org.dpdns.alwaysup.subflow.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetRefreshStampTest {
    @Test fun sameInstantAndBackwardsClockRemainDistinct() {
        val stamp = WidgetRefreshStamp()
        assertEquals(100L, stamp.next(100))
        assertEquals(101L, stamp.next(100))
        assertEquals(102L, stamp.next(50))
    }
}
