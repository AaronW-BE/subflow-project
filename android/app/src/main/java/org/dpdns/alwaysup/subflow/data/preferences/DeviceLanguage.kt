package org.dpdns.alwaysup.subflow.data.preferences

import java.util.Locale

/** The one shipped language that needs more than a language subtag to name it. */
const val TRADITIONAL_CHINESE = "zh-Hant"

/**
 * Which of the shipped languages a device locale should be shown in.
 *
 * For every language but Chinese the language subtag is enough: `de-AT` and
 * `de-DE` both read German. Chinese is two written languages under one subtag,
 * and matching on `zh` alone sent every Taiwanese and Hong Kong user to
 * Simplified Chinese, with "follow system" offering no way out (BIN-27).
 */
object DeviceLanguage {

    /** Regions that write Traditional Chinese when the locale names no script. */
    private val TRADITIONAL_REGIONS = setOf("TW", "HK", "MO")

    /**
     * The app's language code for [locale], whether or not the app ships it.
     *
     * An explicit script wins over the region, so `zh-Hans-HK` is Simplified
     * and `zh-Hant-CN` is Traditional. Without one - `zh-TW` from an older
     * device, or `Locale.TRADITIONAL_CHINESE` - the region decides.
     */
    fun codeFor(locale: Locale): String {
        val language = locale.language.lowercase()
        if (language != "zh") return language
        val traditional = when (locale.script) {
            "Hant" -> true
            "Hans" -> false
            else -> locale.country.uppercase() in TRADITIONAL_REGIONS
        }
        return if (traditional) TRADITIONAL_CHINESE else "zh"
    }

    /**
     * The first of the device's [locales], in its order of preference, that the
     * app ships; null if none are.
     */
    fun firstSupported(locales: List<Locale>, supported: Collection<String>): String? {
        for (locale in locales) {
            val code = codeFor(locale)
            supported.firstOrNull { it.equals(code, ignoreCase = true) }?.let { return it }
        }
        return null
    }
}
