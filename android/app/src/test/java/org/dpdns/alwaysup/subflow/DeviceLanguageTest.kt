package org.dpdns.alwaysup.subflow

import org.dpdns.alwaysup.subflow.data.preferences.DeviceLanguage
import org.dpdns.alwaysup.subflow.data.preferences.SYSTEM_LANGUAGE
import org.dpdns.alwaysup.subflow.data.preferences.SupportedLanguages
import org.dpdns.alwaysup.subflow.data.preferences.TRADITIONAL_CHINESE
import org.dpdns.alwaysup.subflow.domain.util.localeForLanguageCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * Which language "follow system" shows.
 *
 * Matching on the language subtag alone sent every Taiwanese and Hong Kong
 * user to Simplified Chinese, and nothing in Settings could change that.
 */
class DeviceLanguageTest {

    private val shipped = SupportedLanguages.map { it.code }.filter { it != SYSTEM_LANGUAGE }

    private fun device(vararg tags: String) = tags.map(Locale::forLanguageTag)

    @Test
    fun `Taiwan, Hong Kong and Macau read Traditional Chinese`() {
        for (tag in listOf("zh-TW", "zh-HK", "zh-MO", "zh-Hant-TW", "zh-Hant-HK")) {
            assertEquals(tag, TRADITIONAL_CHINESE, DeviceLanguage.firstSupported(device(tag), shipped))
        }
    }

    @Test
    fun `the mainland, Singapore and a bare zh read Simplified Chinese`() {
        for (tag in listOf("zh-CN", "zh-SG", "zh-Hans-CN", "zh")) {
            assertEquals(tag, "zh", DeviceLanguage.firstSupported(device(tag), shipped))
        }
    }

    @Test
    fun `an explicit script outranks the region`() {
        // Someone in Hong Kong who set their phone to Simplified meant it, and
        // the other way round.
        assertEquals("zh", DeviceLanguage.codeFor(Locale.forLanguageTag("zh-Hans-HK")))
        assertEquals(TRADITIONAL_CHINESE, DeviceLanguage.codeFor(Locale.forLanguageTag("zh-Hant-CN")))
    }

    @Test
    fun `the old region-only constant is Traditional`() {
        // Locale.TRADITIONAL_CHINESE is zh_TW with no script at all.
        assertEquals(TRADITIONAL_CHINESE, DeviceLanguage.codeFor(Locale.TRADITIONAL_CHINESE))
        assertEquals("zh", DeviceLanguage.codeFor(Locale.SIMPLIFIED_CHINESE))
    }

    @Test
    fun `other languages still match on the language alone`() {
        assertEquals("de", DeviceLanguage.firstSupported(device("de-AT"), shipped))
        assertEquals("fr", DeviceLanguage.firstSupported(device("fr-CA"), shipped))
        assertEquals("ja", DeviceLanguage.firstSupported(device("ja-JP"), shipped))
    }

    @Test
    fun `the device's order of preference is kept, skipping what the app does not ship`() {
        // Portuguese first, which the app does not ship, then Taiwanese Chinese.
        assertEquals(TRADITIONAL_CHINESE, DeviceLanguage.firstSupported(device("pt-BR", "zh-TW", "en-US"), shipped))
        assertEquals("en", DeviceLanguage.firstSupported(device("en-GB", "zh-TW"), shipped))
    }

    @Test
    fun `nothing shipped means no answer, and the caller falls back`() {
        assertNull(DeviceLanguage.firstSupported(device("pt-BR", "ko-KR"), shipped))
    }

    @Test
    fun `a stored Traditional choice becomes a Hant locale`() {
        // The script is what makes Android pick values-b+zh+Hant over values-zh.
        val locale = localeForLanguageCode(TRADITIONAL_CHINESE)
        assertEquals("zh", locale.language)
        assertEquals("Hant", locale.script)
        assertEquals(Locale.SIMPLIFIED_CHINESE, localeForLanguageCode("zh"))
    }

    @Test
    fun `every shipped language has its own locale rather than falling to English`() {
        // localeForLanguageCode ends in `else -> Locale.ENGLISH`, so a code
        // added to the picker without a branch there silently shows English.
        for (code in shipped.filter { it != "en" }) {
            assertEquals(code, false, localeForLanguageCode(code).language == "en")
        }
    }
}
