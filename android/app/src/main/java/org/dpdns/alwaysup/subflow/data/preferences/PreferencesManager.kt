package org.dpdns.alwaysup.subflow.data.preferences

import android.content.Context
import android.content.res.Resources
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
    CurrencyOption("CHF", "CHF", "Swiss Franc"),
    CurrencyOption("CAD", "CA$", "Canadian Dollar"),
    CurrencyOption("AUD", "AU$", "Australian Dollar"),
    CurrencyOption("NZD", "NZ$", "New Zealand Dollar"),
    CurrencyOption("CNY", "¥", "Chinese Yuan"),
    CurrencyOption("HKD", "HK$", "Hong Kong Dollar"),
    CurrencyOption("TWD", "NT$", "New Taiwan Dollar"),
    CurrencyOption("SGD", "S$", "Singapore Dollar"),
    CurrencyOption("KRW", "₩", "South Korean Won"),
    CurrencyOption("INR", "₹", "Indian Rupee"),
    CurrencyOption("IDR", "Rp", "Indonesian Rupiah"),
    CurrencyOption("THB", "฿", "Thai Baht"),
    CurrencyOption("MYR", "RM", "Malaysian Ringgit"),
    CurrencyOption("PHP", "₱", "Philippine Peso"),
    CurrencyOption("VND", "₫", "Vietnamese Dong"),
    CurrencyOption("BRL", "R$", "Brazilian Real"),
    CurrencyOption("MXN", "MX$", "Mexican Peso"),
    CurrencyOption("CLP", "CLP$", "Chilean Peso"),
    CurrencyOption("COP", "COL$", "Colombian Peso"),
    CurrencyOption("ZAR", "R", "South African Rand"),
    CurrencyOption("NGN", "₦", "Nigerian Naira"),
    CurrencyOption("EGP", "E£", "Egyptian Pound"),
    CurrencyOption("TRY", "₺", "Turkish Lira"),
    CurrencyOption("ILS", "₪", "Israeli New Shekel"),
    CurrencyOption("AED", "AED", "UAE Dirham"),
    CurrencyOption("SAR", "SAR", "Saudi Riyal"),
    CurrencyOption("PLN", "zł", "Polish Zloty"),
    CurrencyOption("SEK", "kr", "Swedish Krona"),
    CurrencyOption("NOK", "kr", "Norwegian Krone"),
    CurrencyOption("DKK", "kr", "Danish Krone"),
    CurrencyOption("CZK", "Kč", "Czech Koruna"),
    CurrencyOption("HUF", "Ft", "Hungarian Forint"),
    CurrencyOption("RON", "lei", "Romanian Leu"),
    CurrencyOption("UAH", "₴", "Ukrainian Hryvnia"),
    CurrencyOption("PKR", "₨", "Pakistani Rupee"),
    CurrencyOption("BDT", "৳", "Bangladeshi Taka")
)

/**
 * The language setting's "follow the device" value.
 *
 * A code rather than a null so it can be stored, compared and selected like any
 * other choice - the same shape [ThemeMode.SYSTEM] already uses.
 */
const val SYSTEM_LANGUAGE = "system"

/**
 * @param nativeName how the language names itself. Someone who has landed in a
 *   language they cannot read finds their way out by recognising this, so it is
 *   never translated.
 * @param labelRes set only where there is no native name to show - the
 *   follow-the-device entry, whose label has to be in the language the user is
 *   currently reading.
 */
data class LanguageOption(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val labelRes: Int? = null
)

val SupportedLanguages = listOf(
    LanguageOption(
        SYSTEM_LANGUAGE,
        "System default",
        "",
        org.dpdns.alwaysup.subflow.R.string.language_system
    ),
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

    /**
     * Whether the swipe-to-delete hint has been retired.
     *
     * Set when the user dismisses it *or* the first time they delete
     * something - a hint that keeps explaining a gesture you have already
     * used is just clutter.
     */
    private val _swipeHintSeen = MutableStateFlow(prefs.getBoolean(KEY_SWIPE_HINT, false))
    val swipeHintSeen: StateFlow<Boolean> = _swipeHintSeen.asStateFlow()

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

    /**
     * Puts the app back to its first-run state for teaching purposes only.
     *
     * The contextual hint goes back with the walkthrough: someone asking to see
     * the introduction again is asking for its hints too, and leaving the swipe
     * hint retired would half-restore it.
     */
    fun restartOnboarding() {
        _onboardingComplete.value = false
        _swipeHintSeen.value = false
        prefs.edit()
            .putBoolean("onboarding_complete", false)
            .putBoolean(KEY_SWIPE_HINT, false)
            .apply()
    }

    fun markSwipeHintSeen() {
        if (_swipeHintSeen.value) return
        _swipeHintSeen.value = true
        prefs.edit().putBoolean(KEY_SWIPE_HINT, true).apply()
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
        private const val KEY_SWIPE_HINT = "swipe_hint_seen"

        /**
         * The stored language choice, which may be [SYSTEM_LANGUAGE].
         *
         * Callers that need an actual language resolve it through
         * `localeForLanguageCode`; keeping the sentinel here is what lets the
         * app keep following the device after the setting was last read.
         */
        fun resolveInitialLanguage(context: Context): String =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString("language", null) ?: SYSTEM_LANGUAGE

        /**
         * The device's language, narrowed to one this app ships.
         *
         * Read from [Resources.getSystem], never `Locale.getDefault()`:
         * MainActivity calls `Locale.setDefault` with the app's own locale
         * while attaching, so from that point on the JVM default is the app
         * language and asking it what the device is set to answers with our
         * own last answer.
         */
        fun deviceLanguage(): String {
            val locales = Resources.getSystem().configuration.locales
            for (i in 0 until locales.size()) {
                val tag = locales[i].language
                SupportedLanguages.firstOrNull {
                    it.code != SYSTEM_LANGUAGE && it.code.equals(tag, ignoreCase = true)
                }?.let { return it.code }
            }
            return "en"
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
