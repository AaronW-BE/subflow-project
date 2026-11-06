package org.dpdns.alwaysup.subflow.domain.util

import kotlinx.coroutines.flow.asStateFlow

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
        "CHF" to "CHF",
        "CAD" to "CA$",
        "AUD" to "AU$",
        "NZD" to "NZ$",
        "CNY" to "¥",
        "HKD" to "HK$",
        "TWD" to "NT$",
        "SGD" to "S$",
        "KRW" to "₩",
        "INR" to "₹",
        "IDR" to "Rp",
        "THB" to "฿",
        "MYR" to "RM",
        "PHP" to "₱",
        "VND" to "₫",
        "BRL" to "R$",
        "MXN" to "MX$",
        "CLP" to "CLP$",
        "COP" to "COL$",
        "ZAR" to "R",
        "NGN" to "₦",
        "EGP" to "E£",
        "TRY" to "₺",
        "ILS" to "₪",
        "AED" to "AED",
        "SAR" to "SAR",
        "PLN" to "zł",
        "SEK" to "kr",
        "NOK" to "kr",
        "DKK" to "kr",
        "CZK" to "Kč",
        "HUF" to "Ft",
        "RON" to "lei",
        "UAH" to "₴",
        "PKR" to "₨",
        "BDT" to "৳"
    )

    /** Currencies with no minor unit: showing "¥1,200.00" reads as wrong to a local. */
    private val zeroDecimalCurrencies = setOf("JPY", "KRW", "VND", "CLP")

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
    // Licensed offline table, retained for currencies absent from a usable feed.
    //
    // Deliberately NOT a snapshot of the live provider. ExchangeRate-API's
    // terms permit caching for "customer end-use only" and state the licence
    // "does not permit re-distribution of our data"; baking their quote into an
    // APK handed to every user is distribution, not caching. Nothing from that
    // feed appears below.
    //
    // Provenance, as of 2026-09-01:
    //   29 from the ECB euro reference rates, which the ECB publishes for reuse
    //    2 official hard pegs (AED 3.6725, SAR 3.75)
    //    9 our own approximations - TWD, VND, CLP, COP, NGN, EGP, UAH, PKR,
    //      BDT - covered by neither. These are rough, and they are the reason
    //      this table is a floor rather than a source of truth.
    //
    // Every one of these is replaced by the live feed the moment /rates
    // answers. A currency missing from the table converts at 1:1 (see
    // convert()), so the table must stay complete even where it is imprecise.
    private val fallbackRatesToUSD = mapOf(
        "USD" to 1.0,
        "EUR" to 1.159,
        "GBP" to 1.35311,
        "JPY" to 0.00624376,
        "CHF" to 1.23376,
        "CAD" to 0.720046,
        "AUD" to 0.714133,
        "NZD" to 0.589102,
        "CNY" to 0.148759,
        "HKD" to 0.127535,
        "TWD" to 0.0309598,
        "SGD" to 0.785299,
        "KRW" to 0.000727479,
        "INR" to 0.0105319,
        "IDR" to 5.63539e-05,
        "THB" to 0.0300616,
        "MYR" to 0.247555,
        "PHP" to 0.0160182,
        "VND" to 3.93701e-05,
        "BRL" to 0.192348,
        "MXN" to 0.0588204,
        "CLP" to 0.00105263,
        "COP" to 0.000243902,
        "ZAR" to 0.0618973,
        "NGN" to 0.000645161,
        "EGP" to 0.0206186,
        "TRY" to 0.0207151,
        "ILS" to 0.33173,
        "AED" to 0.272294,
        "SAR" to 0.266667,
        "PLN" to 0.267587,
        "SEK" to 0.104279,
        "NOK" to 0.107132,
        "DKK" to 0.155053,
        "CZK" to 0.0479731,
        "HUF" to 0.00316056,
        "RON" to 0.220483,
        "UAH" to 0.0240964,
        "PKR" to 0.00359712,
        "BDT" to 0.00833333
    )

    private val publication = kotlinx.coroutines.flow.MutableStateFlow(
        RatesSnapshot(0L, fallbackRatesToUSD.mapValues { 1.0 / it.value })
    )
    val snapshots: kotlinx.coroutines.flow.StateFlow<RatesSnapshot> =
        publication.asStateFlow()

    fun captureSnapshot(): RatesSnapshot = publication.value
    val quotedAtEpochMillis: Long get() = captureSnapshot().quotedAtEpochMillis ?: 0L
    val providerName: String get() = captureSnapshot().providerName.orEmpty()
    val providerUrl: String get() = captureSnapshot().providerUrl.orEmpty()

    @Synchronized
    fun updateFromUsdBase(
        rates: Map<String, Double>,
        quotedAtEpochMillis: Long = 0L,
        provider: String = "",
        providerUrl: String = "",
        fromCache: Boolean = false,
        expectedRevision: Long? = null
    ) {
        val previous = publication.value
        if (fromCache && expectedRevision != null && previous.revision != expectedRevision) return
        if (fromCache && previous.revision > 0 &&
            quotedAtEpochMillis < (previous.quotedAtEpochMillis ?: 0L)) return
        val valid = rates.filterValues {
            it.isFinite() && it > 0.0 && (1.0 / it).isFinite() && 1.0 / it > 0.0
        }.mapKeys { currencyKey(it.key) }
        if (valid.isEmpty()) return
        val merged = fallbackRatesToUSD.mapValues { 1.0 / it.value } + valid + ("USD" to 1.0)
        if (previous.ratesPerUsd == merged && previous.quotedAtEpochMillis == quotedAtEpochMillis &&
            previous.providerName.orEmpty() == provider && previous.providerUrl.orEmpty() == providerUrl) return
        publication.value = RatesSnapshot(previous.revision + 1, merged,
            quotedAtEpochMillis, provider, providerUrl)
    }

    fun convert(amount: Double, fromCurrency: String, toCurrency: String): Double {
        if (fromCurrency.equals(toCurrency, ignoreCase = true)) return amount
        val table = captureSnapshot().ratesPerUsd.mapValues { (code, rate) ->
            if (rate == 1.0 / (fallbackRatesToUSD[code] ?: Double.NaN)) fallbackRatesToUSD.getValue(code)
            else 1.0 / rate
        }
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

    /**
     * The day a reminder [daysBefore] the bill on [nextBillDateStr] would
     * arrive, or null when that day is already today or gone - there is no
     * date worth naming then.
     */
    fun reminderDate(
        nextBillDateStr: String,
        daysBefore: Int,
        today: LocalDate = LocalDate.now()
    ): LocalDate? =
        parseOrNull(nextBillDateStr)
            ?.minusDays(daysBefore.toLong())
            ?.takeIf { it.isAfter(today) }

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
