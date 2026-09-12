package org.dpdns.alwaysup.subflow.domain.util

import org.dpdns.alwaysup.subflow.data.preferences.CurrencyOption
import org.dpdns.alwaysup.subflow.data.preferences.SupportedCurrencies
import java.util.Currency
import java.util.Locale

/**
 * A supported currency as it should read in one interface language.
 *
 * [name] comes from CLDR through [Currency.getDisplayName], so the picker says
 * 日元 in Simplified Chinese, 日圓 in Traditional Chinese and Japanischer Yen in
 * German, instead of showing forty English names to everyone.
 *
 * [englishName] is carried alongside because it stays worth searching by. Half
 * the world learns these currencies by their English names first, and someone
 * typing "won" with a Chinese interface should still find 韩元.
 */
data class LocalizedCurrency(
    val code: String,
    val symbol: String,
    val name: String,
    val englishName: String
) {
    /** Whether this currency should show up for [query] typed into the picker. */
    fun matches(query: String): Boolean {
        val q = query.trim()
        return q.isEmpty() ||
            code.contains(q, ignoreCase = true) ||
            name.contains(q, ignoreCase = true) ||
            englishName.contains(q, ignoreCase = true)
    }
}

/**
 * [SupportedCurrencies] with every name resolved for [locale].
 *
 * All forty were checked in the seven languages the app ships on 2026-09-13:
 * CLDR has a real name for every pair, so none of them needs to be overridden
 * here. Note that French and Spanish names are lower-case ("dollar canadien",
 * "dólar canadiense") — that is correct in those languages, so the case is left
 * exactly as CLDR gives it.
 */
fun localizedCurrencies(locale: Locale): List<LocalizedCurrency> =
    SupportedCurrencies.map { it.localizedFor(locale) }

/**
 * The one currency, named for [locale].
 *
 * [Currency.getDisplayName] answers with the code itself when it has no name
 * for the pair, and throws for a code ISO 4217 does not know. Either way the
 * hand-written English name is a better thing to show than "BDT", so it is the
 * fallback rather than the primary.
 */
fun CurrencyOption.localizedFor(locale: Locale): LocalizedCurrency {
    val fromCldr = runCatching { Currency.getInstance(code).getDisplayName(locale) }.getOrNull()
    val resolved = if (fromCldr.isNullOrBlank() || fromCldr == code) name else fromCldr
    return LocalizedCurrency(
        code = code,
        symbol = symbol,
        name = resolved,
        englishName = name
    )
}
