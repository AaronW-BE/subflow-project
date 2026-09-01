package org.dpdns.alwaysup.subflow.domain.util

import android.content.Context
import android.content.res.Configuration
import org.dpdns.alwaysup.subflow.data.preferences.PreferencesManager
import java.util.Locale

fun localeForLanguageCode(code: String): Locale = when (code.lowercase()) {
    "zh" -> Locale.SIMPLIFIED_CHINESE
    "ja" -> Locale.JAPANESE
    "de" -> Locale.GERMAN
    "fr" -> Locale.FRENCH
    "es" -> Locale("es")
    else -> Locale.ENGLISH
}

/**
 * A context bound to the language the user picked in Settings.
 *
 * Background work (notifications) runs outside the Activity, so it would
 * otherwise resolve strings against the system locale and contradict the
 * in-app language choice.
 */
fun Context.withAppLocale(): Context {
    val locale = localeForLanguageCode(PreferencesManager.resolveInitialLanguage(this))
    val config = Configuration(resources.configuration).apply {
        setLocale(locale)
        setLayoutDirection(locale)
    }
    return createConfigurationContext(config)
}
