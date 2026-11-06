package org.dpdns.alwaysup.subflow.data.time

import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.time.*

@RunWith(RobolectricTestRunner::class)
class CurrentDateObserverTest {
    private class MovingClock(var now: Instant) : Clock() {
        override fun instant() = now
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: ZoneId) = this
    }
    @Test fun midnightUsesLocalDstBoundary() {
        val zone = ZoneId.of("America/New_York")
        for ((date, hours) in listOf("2026-03-08" to 23, "2026-11-01" to 25)) {
            val clock = Clock.fixed(LocalDate.parse(date).atStartOfDay(zone).toInstant(), ZoneOffset.UTC)
            assertEquals(hours * 3600000L, millisUntilMidnight(clock, zone))
        }
    }
    @Test fun resumeBroadcastTimezoneAndCancellation() {
        val app = RuntimeEnvironment.getApplication()
        val clock = MovingClock(Instant.parse("2026-03-08T23:30:00Z"))
        var zone: ZoneId = ZoneOffset.UTC
        val job = SupervisorJob()
        val observer = CurrentDateObserver(app, clock, { zone }, CoroutineScope(job + Dispatchers.Unconfined))
        val owner = object : LifecycleOwner {
            override val lifecycle: Lifecycle = LifecycleRegistry(this)
        }
        observer.onResume(owner)
        assertEquals(LocalDate.parse("2026-03-08"), observer.currentLocalDate.value)
        zone = ZoneId.of("Asia/Tokyo")
        app.sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(LocalDate.parse("2026-03-09"), observer.currentLocalDate.value)
        observer.onPause(owner)
        assertFalse(job.children.any())
        clock.now = Instant.parse("2026-03-12T00:00:00Z")
        app.sendBroadcast(Intent(Intent.ACTION_TIME_CHANGED))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(LocalDate.parse("2026-03-09"), observer.currentLocalDate.value)
        observer.onResume(owner)
        assertEquals(LocalDate.parse("2026-03-12"), observer.currentLocalDate.value)
        observer.close()
        assertTrue(job.isCancelled)
    }
}
