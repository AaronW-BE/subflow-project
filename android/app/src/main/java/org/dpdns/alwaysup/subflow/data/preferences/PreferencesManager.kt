package org.dpdns.alwaysup.subflow.data.preferences

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val key: String, val labelRes: Int) {
    SYSTEM("system", org.dpdns.alwaysup.subflow.R.string.theme_system),
    LIGHT("light", org.dpdns.alwaysup.subflow.R.string.theme_light),
    DARK("dark", org.dpdns.alwaysup.subflow.R.string.theme_dark);

    companion object {
        fun fromKey(key: String): ThemeMode = entries.find { it.key.equals(key, ignoreCase = true) } ?: SYSTEM
    }
}

data class CurrencyOption(val code: String, val symbol: String, val name: String)

val SupportedCurrencies = listOf(
    CurrencyOption("USD", "$", "US Dollar"),
    CurrencyOption("EUR", "€", "Euro"),
    CurrencyOption("GBP", "£", "British Pound"),
    CurrencyOption("JPY", "¥", "Japanese Yen"),
    CurrencyOption("CAD", "CA$", "Canadian Dollar"),
    CurrencyOption("AUD", "AU$", "Australian Dollar"),
    CurrencyOption("CNY", "¥", "Chinese Yuan"),
    CurrencyOption("INR", "₹", "Indian Rupee"),
    CurrencyOption("BRL", "R$", "Brazilian Real"),
    CurrencyOption("KRW", "₩", "South Korean Won")
)

data class LanguageOption(val code: String, val displayName: String, val nativeName: String)

val SupportedLanguages = listOf(
    LanguageOption("en", "English", "English"),
    LanguageOption("de", "German", "Deutsch"),
    LanguageOption("fr", "French", "Français"),
    LanguageOption("es", "Spanish", "Español"),
    LanguageOption("ja", "Japanese", "日本語"),
    LanguageOption("zh", "Simplified Chinese", "简体中文")
)

/**
 * Reminder lead times, in days before the renewal date.
 * The free tier only gets the 1-day alert; Pro unlocks 3 and 7.
 */
enum class ReminderLead(val days: Int, val isPro: Boolean) {
    ONE_DAY(1, false),
    THREE_DAYS(3, true),
    SEVEN_DAYS(7, true);

    companion object {
        fun forDays(days: Int): ReminderLead = entries.find { it.days == days } ?: ONE_DAY
    }
}

class PreferencesManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _currency = MutableStateFlow(prefs.getString("primary_currency", resolveInitialCurrency()) ?: "USD")
    val currency: StateFlow<String> = _currency.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.fromKey(prefs.getString("theme_mode", "system") ?: "system"))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _language = MutableStateFlow(resolveInitialLanguage(context))
    val language: StateFlow<String> = _language.asStateFlow()

    private val _hapticsEnabled = MutableStateFlow(prefs.getBoolean("haptics_enabled", true))
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

    private val _onboardingComplete = MutableStateFlow(prefs.getBoolean("onboarding_complete", false))
    val onboardingComplete: StateFlow<Boolean> = _onboardingComplete.asStateFlow()

    /** Days before renewal at which the daily worker notifies. Free tier is pinned to 1. */
    private val _reminderLeads = MutableStateFlow(readLeads())
    val reminderLeads: StateFlow<Set<Int>> = _reminderLeads.asStateFlow()

    fun setCurrency(code: String) {
        _currency.value = code
        prefs.edit().putString("primary_currency", code).apply()
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode.key).apply()
    }

    fun setLanguage(langCode: String) {
        _language.value = langCode
        prefs.edit().putString("language", langCode).apply()
    }

    fun setHapticsEnabled(enabled: Boolean) {
        _hapticsEnabled.value = enabled
        prefs.edit().putBoolean("haptics_enabled", enabled).apply()
    }

    fun completeOnboarding() {
        _onboardingComplete.value = true
        prefs.edit().putBoolean("onboarding_complete", true).apply()
    }

    fun toggleReminderLead(days: Int, enabled: Boolean) {
        val next = _reminderLeads.value.toMutableSet()
        if (enabled) next.add(days) else next.remove(days)
        // At least one lead time must remain, otherwise the alert is silently off.
        val sanitised = if (next.isEmpty()) setOf(1) else next
        _reminderLeads.value = sanitised
        prefs.edit().putStringSet(KEY_LEADS, sanitised.map { it.toString() }.toSet()).apply()
    }

    /**
     * Lead times actually applied for the current entitlement. Free users always
     * get exactly one alert the day before, no matter what is stored.
     */
    fun effectiveLeads(isPro: Boolean): Set<Int> =
        if (isPro) _reminderLeads.value else setOf(ReminderLead.ONE_DAY.days)

    /** Incremented once per cold start; drives the "ask for a review" moment. */
    fun recordLaunch(): Int {
        val next = prefs.getInt("launch_count", 0) + 1
        prefs.edit().putInt("launch_count", next).apply()
        return next
    }

    private fun readLeads(): Set<Int> {
        val stored = prefs.getStringSet(KEY_LEADS, null)
            ?: return setOf(ReminderLead.ONE_DAY.days, ReminderLead.THREE_DAYS.days)
        return stored.mapNotNull { it.toIntOrNull() }.toSet().ifEmpty { setOf(1) }
    }

    private fun resolveInitialCurrency(): String {
        val fromLocale = runCatching {
            java.util.Currency.getInstance(java.util.Locale.getDefault()).currencyCode
        }.getOrNull()
        return SupportedCurrencies.firstOrNull { it.code == fromLocale }?.code ?: "USD"
    }

    companion object {
        const val PREFS_NAME = "subflow_user_preferences"
        private const val KEY_LEADS = "reminder_lead_days"

        fun resolveInitialLanguage(context: Context): String {
            val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString("language", null)
            if (saved != null) return saved
            val systemLang = java.util.Locale.getDefault().language
            return if (SupportedLanguages.any { it.code.equals(systemLang, ignoreCase = true) }) systemLang else "en"
        }

        /** Read directly from prefs for use outside Compose (e.g. the notification worker). */
        fun readLeadsStatic(context: Context, isPro: Boolean): Set<Int> {
            if (!isPro) return setOf(ReminderLead.ONE_DAY.days)
            val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getStringSet(KEY_LEADS, null) ?: return setOf(1, 3)
            return stored.mapNotNull { it.toIntOrNull() }.toSet().ifEmpty { setOf(1) }
        }
    }
}
