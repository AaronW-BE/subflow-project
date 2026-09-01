package org.dpdns.alwaysup.subflow.domain.util

import org.dpdns.alwaysup.subflow.domain.model.BillingCycle
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

object CurrencyFormatter {

    private val symbols = mapOf(
        "USD" to "$",
        "EUR" to "€",
        "GBP" to "£",
        "JPY" to "¥",
        "CNY" to "¥",
        "CAD" to "CA$",
        "AUD" to "AU$",
        "INR" to "₹",
        "BRL" to "R$",
        "KRW" to "₩"
    )

    /** Currencies with no minor unit: showing "¥1,200.00" reads as wrong to a local. */
    private val zeroDecimalCurrencies = setOf("JPY", "KRW")

    fun symbolFor(currencyCode: String): String =
        symbols[currencyCode.uppercase()] ?: (currencyCode.uppercase() + " ")

    /**
     * Groups digits using the device locale (so a German user sees "1.234,56")
     * while keeping the currency's own symbol rather than the locale's.
     */
    fun format(amount: Double, currencyCode: String = "USD", locale: Locale = Locale.getDefault()): String {
        val code = currencyCode.uppercase()
        val symbol = symbolFor(code)
        val pattern = if (code in zeroDecimalCurrencies) "#,##0" else "#,##0.00"
        return try {
            val formatter = DecimalFormat(pattern, DecimalFormatSymbols.getInstance(locale))
            val magnitude = formatter.format(kotlin.math.abs(amount))
            val sign = if (amount < 0) "-" else ""
            "$sign$symbol$magnitude"
        } catch (e: Exception) {
            "$symbol$amount"
        }
    }

    /**
     * Formats a Google Play price given in micros. Uses the platform currency
     * formatter so the result matches what Play itself renders for that country.
     */
    fun formatPlayPrice(micros: Long, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        val amount = micros / 1_000_000.0
        return try {
            java.text.NumberFormat.getCurrencyInstance(locale).apply {
                currency = java.util.Currency.getInstance(currencyCode.uppercase())
                if (currencyCode.uppercase() in zeroDecimalCurrencies) {
                    maximumFractionDigits = 0
                }
            }.format(amount)
        } catch (e: Exception) {
            format(amount, currencyCode, locale)
        }
    }

    /** Compact form for dense chart labels: $1.2k, $18k. */
    fun formatCompact(amount: Double, currencyCode: String = "USD"): String {
        val symbol = symbolFor(currencyCode.uppercase())
        return when {
            kotlin.math.abs(amount) >= 1_000_000 -> "$symbol${DecimalFormat("#,##0.#").format(amount / 1_000_000)}M"
            kotlin.math.abs(amount) >= 1_000 -> "$symbol${DecimalFormat("#,##0.#").format(amount / 1_000)}k"
            else -> "$symbol${DecimalFormat("#,##0").format(amount)}"
        }
    }
}

object CurrencyConverter {
    // Fallback rates to 1.0 USD, used offline and until the server responds.
    private val fallbackRatesToUSD = mapOf(
        "USD" to 1.0,
        "EUR" to 1.08,
        "GBP" to 1.28,
        "JPY" to 0.0065,
        "CAD" to 0.74,
        "AUD" to 0.66,
        "CNY" to 0.138,
        "INR" to 0.012,
        "BRL" to 0.18,
        "KRW" to 0.00073
    )

    @Volatile
    private var ratesToUSD: Map<String, Double> = fallbackRatesToUSD

    @Volatile
    var lastUpdatedEpochMillis: Long = 0L
        private set

    /**
     * Replaces the built-in table with server rates. Values are expressed as
     * units-per-USD (the shape the /rates endpoint returns), so they are
     * inverted here into the USD-value-per-unit form used for conversion.
     */
    fun updateFromUsdBase(rates: Map<String, Double>) {
        if (rates.isEmpty()) return
        val converted = buildMap {
            put("USD", 1.0)
            rates.forEach { (code, perUsd) ->
                if (perUsd > 0.0) put(code.uppercase(), 1.0 / perUsd)
            }
        }
        ratesToUSD = fallbackRatesToUSD + converted
        lastUpdatedEpochMillis = System.currentTimeMillis()
    }

    fun convert(amount: Double, fromCurrency: String, toCurrency: String): Double {
        if (fromCurrency.equals(toCurrency, ignoreCase = true)) return amount
        val table = ratesToUSD
        val fromRate = table[fromCurrency.uppercase()] ?: return amount
        val toRate = table[toCurrency.uppercase()] ?: return amount
        if (toRate == 0.0) return amount
        return amount * fromRate / toRate
    }
}

object DateCalculators {
    private val isoFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun calculateDaysUntil(targetDateStr: String): Long = try {
        ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(targetDateStr, isoFormatter))
    } catch (e: Exception) {
        0
    }

    fun parseOrNull(dateStr: String): LocalDate? =
        runCatching { LocalDate.parse(dateStr, isoFormatter) }.getOrNull()

    fun formatMedium(dateStr: String, locale: Locale = Locale.getDefault()): String =
        parseOrNull(dateStr)
            ?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
            ?: dateStr

    fun advance(date: LocalDate, cycle: BillingCycle): LocalDate = when (cycle) {
        BillingCycle.WEEKLY -> date.plusWeeks(1)
        BillingCycle.MONTHLY -> date.plusMonths(1)
        BillingCycle.QUARTERLY -> date.plusMonths(3)
        BillingCycle.ANNUALLY -> date.plusYears(1)
    }

    /**
     * Rolls a start date forward until it lands in the future.
     *
     * The previous implementation added a single cycle to the *first* bill date,
     * so a subscription started a year ago reported a renewal date already in
     * the past and never fired a reminder.
     */
    fun computeNextRenewalDate(
        firstBillDateStr: String,
        cycle: BillingCycle,
        today: LocalDate = LocalDate.now()
    ): String {
        val start = parseOrNull(firstBillDateStr) ?: return today.plusMonths(1).format(isoFormatter)
        if (start.isAfter(today)) return start.format(isoFormatter)

        var next = start
        // Bounded so a corrupt date can never spin: 400 weekly cycles is ~7.5 years.
        var guard = 0
        while (!next.isAfter(today) && guard < 1000) {
            next = advance(next, cycle)
            guard++
        }
        return next.format(isoFormatter)
    }

    /**
     * Actual length of the billing period that ends on [nextBillDateStr], found
     * by stepping back one cycle.
     *
     * A fixed 30 days for "monthly" is wrong for most months: Aug 28 to Sep 28
     * is 31 days, so the countdown ring could never reach the end of its track.
     */
    fun periodLengthDays(nextBillDateStr: String, cycle: BillingCycle): Int {
        val next = parseOrNull(nextBillDateStr) ?: return when (cycle) {
            BillingCycle.WEEKLY -> 7
            BillingCycle.MONTHLY -> 30
            BillingCycle.QUARTERLY -> 91
            BillingCycle.ANNUALLY -> 365
        }
        val previous = when (cycle) {
            BillingCycle.WEEKLY -> next.minusWeeks(1)
            BillingCycle.MONTHLY -> next.minusMonths(1)
            BillingCycle.QUARTERLY -> next.minusMonths(3)
            BillingCycle.ANNUALLY -> next.minusYears(1)
        }
        return ChronoUnit.DAYS.between(previous, next).toInt().coerceAtLeast(1)
    }

    /** How many billing periods have elapsed since the first payment. */
    fun cyclesElapsed(
        firstBillDateStr: String,
        cycle: BillingCycle,
        today: LocalDate = LocalDate.now()
    ): Int {
        val start = parseOrNull(firstBillDateStr) ?: return 0
        if (start.isAfter(today)) return 0
        var cursor = start
        var count = 0
        while (!cursor.isAfter(today) && count < 1000) {
            cursor = advance(cursor, cycle)
            count++
        }
        return count
    }
}
