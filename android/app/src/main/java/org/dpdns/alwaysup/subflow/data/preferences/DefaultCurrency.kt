package org.dpdns.alwaysup.subflow.data.preferences

import java.util.Currency
import java.util.Locale

/**
 * The primary currency to use when the user has never picked one.
 *
 * Every converted total in the app is denominated in the primary currency, so
 * this has two jobs: start from the right answer, and never change it behind
 * the user's back.
 *
 * It used to do neither. The value was re-derived from `Locale.getDefault()` on
 * every launch and never stored. But `MainActivity.attachBaseContext` sets the
 * JVM default to the app's *interface language*, and every language but
 * Simplified Chinese is set without a region - `Locale.GERMAN` is just `de`. A
 * region-less locale has no currency, so the lookup threw and fell back to
 * dollars. Measured on a device, with the system set to zh-Hans-CN:
 *
 * - in-app language Deutsch, or per-app locale de-DE, en-GB, ja-JP: **USD**
 * - per-app locale zh-TW: **CNY** (the language maps to `zh_CN`)
 *
 * So a German, British or Japanese user started in dollars, a Taiwanese user in
 * yuan, and a Chinese user who switched the interface to German watched every
 * total silently change currency. Only mainland Chinese and American users were
 * right, and both by accident.
 *
 * The fix is in two parts, both in [resolve]: the region comes from the
 * *system* locale list rather than the interface language, and whatever is
 * resolved is written down once so a later language change cannot move it.
 */
object DefaultCurrency {

    const val FALLBACK = "USD"

    private val supported: Set<String> = SupportedCurrencies.map { it.code }.toSet()

    /**
     * @property save whether [code] still has to be written down. A value that
     *   was only ever derived is what let a language change move it.
     */
    data class Resolution(val code: String, val save: Boolean)

    /**
     * @param stored the saved choice, if any. Always wins.
     * @param firstLaunch nothing has run on this install before. Only then is
     *   there no earlier answer the user has already been looking at.
     * @param systemRegions the device's region codes in preference order - from
     *   the system locale list, never from the app's interface language.
     * @param legacy the locale earlier versions derived the currency from.
     *   An existing install is pinned to what that showed, so updating the app
     *   does not change the currency of every total either - which would be the
     *   very bug this replaces.
     */
    fun resolve(
        stored: String?,
        firstLaunch: Boolean,
        systemRegions: List<String>,
        legacy: Locale
    ): Resolution = when {
        stored != null -> Resolution(stored, save = false)
        firstLaunch -> Resolution(forRegions(systemRegions) ?: FALLBACK, save = true)
        else -> Resolution(forRegions(listOf(legacy.country)) ?: FALLBACK, save = true)
    }

    /**
     * The first region in the list whose currency the app supports.
     *
     * Blank regions are skipped rather than ending the search: a device can list
     * a bare language ahead of one with a region, and "en" alone says nothing
     * about money.
     */
    fun forRegions(regions: List<String>): String? =
        regions.asSequence()
            .filter { it.isNotBlank() }
            // getInstance throws for a code it does not know and returns null for
            // a territory with no currency of its own; both just mean "next".
            .mapNotNull { runCatching { Currency.getInstance(Locale("", it))?.currencyCode }.getOrNull() }
            .firstOrNull { it in supported }
}
