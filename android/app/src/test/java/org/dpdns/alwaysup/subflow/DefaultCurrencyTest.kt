package org.dpdns.alwaysup.subflow

import org.dpdns.alwaysup.subflow.data.preferences.DefaultCurrency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The primary currency a user starts with, and what keeps it from moving.
 *
 * The locales below are the ones MainActivity actually installs as the JVM
 * default for each interface language - `Locale.GERMAN` has no region, and
 * that is the whole bug.
 */
class DefaultCurrencyTest {

    private val chinaPhone = listOf("CN", "US") // zh-Hans-CN, en-US

    @Test
    fun `a stored choice is never second-guessed`() {
        val r = DefaultCurrency.resolve(
            stored = "EUR", firstLaunch = true, systemRegions = chinaPhone, legacy = Locale.GERMAN
        )
        assertEquals("EUR", r.code)
        assertFalse("nothing new to write", r.save)
    }

    @Test
    fun `a fresh install takes the device region, not the interface language`() {
        // A Chinese phone with the app set to German. The old rule read the
        // region-less `de` and landed on dollars.
        val r = DefaultCurrency.resolve(
            stored = null, firstLaunch = true, systemRegions = chinaPhone, legacy = Locale.GERMAN
        )
        assertEquals("CNY", r.code)
        assertTrue(r.save)
    }

    @Test
    fun `a German phone starts in euros`() {
        val r = DefaultCurrency.resolve(
            stored = null, firstLaunch = true, systemRegions = listOf("DE"), legacy = Locale.GERMAN
        )
        assertEquals("EUR", r.code)
    }

    @Test
    fun `a Taiwanese phone starts in its own currency, not the mainland's`() {
        // The interface language for zh-TW maps to SIMPLIFIED_CHINESE, whose
        // region is CN; the old rule answered CNY.
        val r = DefaultCurrency.resolve(
            stored = null, firstLaunch = true, systemRegions = listOf("TW"),
            legacy = Locale.SIMPLIFIED_CHINESE
        )
        assertEquals("TWD", r.code)
    }

    @Test
    fun `an existing install keeps showing what it showed before the update`() {
        // Earlier versions never stored the value, so these users have been
        // looking at the derived one on every launch. Swapping it for the
        // "correct" one on update would change every total's currency at once -
        // the same surprise this fix exists to remove.
        val german = DefaultCurrency.resolve(
            stored = null, firstLaunch = false, systemRegions = listOf("DE"), legacy = Locale.GERMAN
        )
        assertEquals("what `de` used to fall back to", "USD", german.code)
        assertTrue("and it is now written down", german.save)

        val chinese = DefaultCurrency.resolve(
            stored = null, firstLaunch = false, systemRegions = chinaPhone,
            legacy = Locale.SIMPLIFIED_CHINESE
        )
        assertEquals("CNY", chinese.code)
    }

    @Test
    fun `once resolved, changing the interface language cannot move it`() {
        // Launch 1: Chinese phone, app following the system.
        val first = DefaultCurrency.resolve(
            stored = null, firstLaunch = true, systemRegions = chinaPhone,
            legacy = Locale.SIMPLIFIED_CHINESE
        )
        assertEquals("CNY", first.code)
        assertTrue(first.save)

        // Launch 2: the user switched the app to German. What was saved stands.
        val second = DefaultCurrency.resolve(
            stored = first.code, firstLaunch = false, systemRegions = chinaPhone,
            legacy = Locale.GERMAN
        )
        assertEquals("CNY", second.code)
        assertFalse(second.save)
    }

    @Test
    fun `regions are tried in order, skipping blanks and unsupported currencies`() {
        // A bare language, then Argentina (pesos, not in the list), then Japan.
        assertEquals("JPY", DefaultCurrency.forRegions(listOf("", "AR", "JP")))
    }

    @Test
    fun `nothing usable falls back to dollars`() {
        assertNull(DefaultCurrency.forRegions(emptyList()))
        // Antarctica has no currency of its own; "ZZ" is not a region at all.
        assertNull(DefaultCurrency.forRegions(listOf("", "AQ", "ZZ")))

        val r = DefaultCurrency.resolve(
            stored = null, firstLaunch = true, systemRegions = listOf(""), legacy = Locale.ENGLISH
        )
        assertEquals(DefaultCurrency.FALLBACK, r.code)
    }
}
