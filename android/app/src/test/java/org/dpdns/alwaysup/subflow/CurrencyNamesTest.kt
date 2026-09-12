package org.dpdns.alwaysup.subflow

import org.dpdns.alwaysup.subflow.data.preferences.CurrencyOption
import org.dpdns.alwaysup.subflow.data.preferences.SupportedCurrencies
import org.dpdns.alwaysup.subflow.domain.util.localizedCurrencies
import org.dpdns.alwaysup.subflow.domain.util.localizedFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * How the currency picker reads in each language the app ships.
 *
 * The locales below are the seven the app has strings for, spelled the way
 * `localeForLanguageCode` produces them.
 */
class CurrencyNamesTest {

    private val shipped = listOf(
        Locale.ENGLISH,
        Locale.SIMPLIFIED_CHINESE,
        Locale.forLanguageTag("zh-Hant"),
        Locale.JAPANESE,
        Locale.GERMAN,
        Locale.FRENCH,
        Locale("es")
    )

    @Test
    fun `every supported currency has a name in every shipped language`() {
        for (locale in shipped) {
            for (curr in localizedCurrencies(locale)) {
                assertTrue(
                    "${curr.code} has a blank name in $locale",
                    curr.name.isNotBlank()
                )
                // A bare code means CLDR had nothing and the fallback did not
                // fire either - the user would be reading "BDT" as a name.
                assertNotEquals(
                    "${curr.code} falls back to its own code in $locale",
                    curr.code,
                    curr.name
                )
            }
        }
    }

    @Test
    fun `the name follows the interface language`() {
        fun jpyIn(locale: Locale) = localizedCurrencies(locale).first { it.code == "JPY" }.name

        assertEquals("日元", jpyIn(Locale.SIMPLIFIED_CHINESE))
        // Not a character conversion of the Simplified name: Taiwan writes 日圓.
        assertEquals("日圓", jpyIn(Locale.forLanguageTag("zh-Hant")))
        assertEquals("日本円", jpyIn(Locale.JAPANESE))
    }

    @Test
    fun `the English name is kept for searching`() {
        val cny = localizedCurrencies(Locale.SIMPLIFIED_CHINESE).first { it.code == "CNY" }
        assertEquals("人民币", cny.name)
        assertEquals("Chinese Yuan", cny.englishName)
        assertEquals("¥", cny.symbol)
    }

    @Test
    fun `a code ISO 4217 does not know keeps our own name`() {
        val invented = CurrencyOption("ZZZ", "Z$", "Imaginary Dollar").localizedFor(Locale.GERMAN)
        assertEquals("Imaginary Dollar", invented.name)
        assertEquals("Imaginary Dollar", invented.englishName)
    }

    @Test
    fun `search matches the code, the localized name and the English one`() {
        val krw = localizedCurrencies(Locale.SIMPLIFIED_CHINESE).first { it.code == "KRW" }

        assertTrue("by code", krw.matches("krw"))
        assertTrue("by localized name", krw.matches("韩元"))
        // Someone who learned it as "won" should find it with a Chinese phone.
        assertTrue("by English name", krw.matches("Won"))
        assertTrue("an empty query matches everything", krw.matches("  "))
        assertFalse("and something unrelated matches nothing", krw.matches("peso"))
    }

    @Test
    fun `the list stays in the order the picker was written for`() {
        assertEquals(
            SupportedCurrencies.map { it.code },
            localizedCurrencies(Locale.FRENCH).map { it.code }
        )
    }
}
