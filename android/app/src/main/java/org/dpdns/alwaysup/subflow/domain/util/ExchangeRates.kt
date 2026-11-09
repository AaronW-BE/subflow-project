package org.dpdns.alwaysup.subflow.domain.util

import java.util.Collections
import java.util.Locale

internal fun currencyKey(code: String): String = code.uppercase(Locale.ROOT)

/** Rates are currency units per one USD, not USD value per currency unit. */
class RatesSnapshot(
    val revision: Long,
    ratesPerUsd: Map<String, Double>,
    val quotedAtEpochMillis: Long? = null,
    val providerName: String? = null,
    val providerUrl: String? = null
) {
    val ratesPerUsd: Map<String, Double> = Collections.unmodifiableMap(
        ratesPerUsd.mapKeys { currencyKey(it.key) }.toMap()
    )
}

enum class ConversionFailure {
    INVALID_AMOUNT, MISSING_SOURCE_RATE, MISSING_TARGET_RATE,
    INVALID_SOURCE_RATE, INVALID_TARGET_RATE, OVERFLOW, UNDERFLOW
}

sealed interface ConversionResult {
    data class Available(val amount: Double) : ConversionResult
    data class Unavailable(val reason: ConversionFailure) : ConversionResult
}

fun convertAmount(
    amount: Double,
    fromCurrency: String,
    toCurrency: String,
    rates: RatesSnapshot
): ConversionResult {
    if (!amount.isFinite() || amount < 0.0) {
        return ConversionResult.Unavailable(ConversionFailure.INVALID_AMOUNT)
    }
    val source = currencyKey(fromCurrency)
    val target = currencyKey(toCurrency)
    if (amount == 0.0 || source == target) return ConversionResult.Available(amount)
    val sourceRate = rates.ratesPerUsd[source]
        ?: return ConversionResult.Unavailable(ConversionFailure.MISSING_SOURCE_RATE)
    if (!sourceRate.isFinite() || sourceRate <= 0.0) {
        return ConversionResult.Unavailable(ConversionFailure.INVALID_SOURCE_RATE)
    }
    val targetRate = rates.ratesPerUsd[target]
        ?: return ConversionResult.Unavailable(ConversionFailure.MISSING_TARGET_RATE)
    if (!targetRate.isFinite() || targetRate <= 0.0) {
        return ConversionResult.Unavailable(ConversionFailure.INVALID_TARGET_RATE)
    }
    val converted = amount / sourceRate * targetRate
    return when {
        !converted.isFinite() -> ConversionResult.Unavailable(ConversionFailure.OVERFLOW)
        converted == 0.0 -> ConversionResult.Unavailable(ConversionFailure.UNDERFLOW)
        else -> ConversionResult.Available(converted)
    }
}
