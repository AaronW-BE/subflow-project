package org.dpdns.alwaysup.subflow.domain.util

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.After
import org.junit.Test

class ObservableRatesTest {
    private val original = CurrencyConverter.captureSnapshot()
    @After fun restore() {
        val field = CurrencyConverter::class.java.getDeclaredField("publication")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(null) as MutableStateFlow<RatesSnapshot>).value = original
    }
    @Test fun validConversionsMatchLegacy() {
        val snapshot = CurrencyConverter.captureSnapshot()
        for (from in snapshot.ratesPerUsd.keys) for (to in snapshot.ratesPerUsd.keys) {
            val legacy = 125.0 / snapshot.ratesPerUsd.getValue(from) * snapshot.ratesPerUsd.getValue(to)
            val value = convertAmount(125.0, from, to, snapshot) as ConversionResult.Available
            assertEquals(legacy, value.amount, legacy * 1e-12)
        }
    }
    @Test fun atomicImmutableAndIdempotentPublication() {
        val incoming = mutableMapOf("EUR" to 0.9)
        CurrencyConverter.updateFromUsdBase(incoming, 100, "feed", "url")
        val snapshot = CurrencyConverter.captureSnapshot()
        incoming["EUR"] = 8.0
        assertEquals(0.9, snapshot.ratesPerUsd.getValue("EUR"), 0.0)
        assertEquals(100L, snapshot.quotedAtEpochMillis)
        assertEquals("feed", snapshot.providerName)
        assertEquals("url", snapshot.providerUrl)
        CurrencyConverter.updateFromUsdBase(mapOf("EUR" to 0.9), 100, "feed", "url", true)
        assertSame(snapshot, CurrencyConverter.snapshots.value)
        CurrencyConverter.updateFromUsdBase(mapOf("EUR" to 0.9), 100, "changed", "url")
        assertEquals(snapshot.revision + 1, CurrencyConverter.captureSnapshot().revision)
        assertEquals("feed", snapshot.providerName)
        try {
            (snapshot.ratesPerUsd as MutableMap)["EUR"] = 5.0
            fail("mutable rates")
        } catch (_: UnsupportedOperationException) { }
    }
    @Test fun rejectsNonFiniteAndDelayedCache() {
        val revision = CurrencyConverter.captureSnapshot().revision
        CurrencyConverter.updateFromUsdBase(mapOf("EUR" to 0.95), 200, "live")
        val live = CurrencyConverter.captureSnapshot()
        CurrencyConverter.updateFromUsdBase(mapOf("EUR" to 0.8), 100, "cache", fromCache = true,
            expectedRevision = revision)
        assertSame(live, CurrencyConverter.captureSnapshot())
        CurrencyConverter.updateFromUsdBase(mapOf("EUR" to Double.NaN, "JPY" to Double.POSITIVE_INFINITY,
            "GBP" to 0.0, "CAD" to -1.0, "CHF" to Double.MIN_VALUE))
        assertSame(live, CurrencyConverter.captureSnapshot())
        assertNotEquals(CalculationSnapshot("USD", java.time.LocalDate.of(2026,1,1), original),
            CalculationSnapshot("USD", java.time.LocalDate.of(2026,1,1), live))
    }
}
