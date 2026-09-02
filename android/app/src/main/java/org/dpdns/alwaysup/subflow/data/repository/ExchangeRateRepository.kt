package org.dpdns.alwaysup.subflow.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dpdns.alwaysup.subflow.data.preferences.SupportedCurrencies
import org.dpdns.alwaysup.subflow.BuildConfig
import org.dpdns.alwaysup.subflow.data.remote.ExchangeRateApi
import org.dpdns.alwaysup.subflow.data.remote.SubFlowApiService
import org.dpdns.alwaysup.subflow.domain.util.CurrencyConverter

/**
 * Keeps [CurrencyConverter] on live rates, in every build type.
 *
 * The cache is what makes this acceptable to the provider and to the user's
 * battery: rates are published once a day, the response says when the next one
 * lands, and nothing is requested before then. A cold launch with a valid cache
 * makes no network call at all.
 *
 * Every failure is silent and non-destructive. Whatever was loaded stays
 * loaded, because a wrong total is worse than a slightly old one.
 */
class ExchangeRateRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val api = ExchangeRateApi.create()
    private val backendApi = SubFlowApiService.create()
    private val gson = Gson()

    /** Loads the cache into the converter. Call before the first conversion. */
    fun primeFromCache() {
        val json = prefs.getString(KEY_RATES, null) ?: return
        val rates: Map<String, Double> = runCatching {
            gson.fromJson<Map<String, Double>>(
                json, object : TypeToken<Map<String, Double>>() {}.type
            )
        }.getOrNull() ?: return

        if (rates.isNotEmpty()) {
            CurrencyConverter.updateFromUsdBase(
                rates = rates,
                quotedAtEpochMillis = prefs.getLong(KEY_QUOTED_AT, 0L),
                provider = ExchangeRateApi.PROVIDER_NAME,
                providerUrl = ExchangeRateApi.PROVIDER_URL
            )
        }
    }

    /**
     * Fetches if the cached quote has expired.
     *
     * @param force ignore the schedule, for an explicit pull-to-refresh.
     * @return true when live rates are in use, cached or freshly fetched.
     */
    suspend fun refreshIfStale(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        primeFromCache()

        val nextUpdate = prefs.getLong(KEY_NEXT_UPDATE, 0L)
        val haveCache = prefs.contains(KEY_RATES)
        // Trust the provider's own schedule, and never let a bad clock or a
        // missing hint turn this into a request on every launch.
        val due = force || !haveCache ||
            nextUpdate <= 0L || System.currentTimeMillis() >= nextUpdate

        if (!due) return@withContext true

        // Prefer our own backend, but only when it is actually adding
        // something. A server with no EXCHANGE_RATE_API_KEY is relaying the
        // very endpoint below, so going through it buys nothing and adds a hop
        // that can fail.
        if (fetchFromBackend()) return@withContext true

        try {
            val res = api.latestUsd()
            val body = res.body()
            val rates = body?.rates

            if (!res.isSuccessful || body?.result != "success" || rates.isNullOrEmpty()) {
                Log.d(TAG, "Rate refresh declined: http=${res.code()} result=${body?.result}")
                return@withContext haveCache
            }
            if (!body.baseCode.equals("USD", ignoreCase = true) || rates["USD"] != 1.0) {
                // A non-USD base would invert every conversion in the app.
                Log.w(TAG, "Rate refresh rejected: base=${body.baseCode} usd=${rates["USD"]}")
                return@withContext haveCache
            }
            val missing = SupportedCurrencies.count { rates[it.code] == null }
            if (missing > 0) {
                // A partial table is worse than a stale one: CurrencyConverter
                // returns the amount unchanged for an unknown currency, so the
                // missing ones would silently convert at 1:1.
                Log.w(TAG, "Rate refresh rejected: missing $missing selectable currencies")
                return@withContext haveCache
            }
            if (body.timeEolUnix > 0L) {
                Log.w(TAG, "Rate endpoint is scheduled for shutdown at ${body.timeEolUnix}")
            }

            val quotedAt = body.timeLastUpdateUnix * 1000L
            prefs.edit()
                .putString(KEY_RATES, gson.toJson(rates))
                .putLong(KEY_QUOTED_AT, quotedAt)
                .putLong(KEY_NEXT_UPDATE, body.timeNextUpdateUnix * 1000L)
                .apply()

            CurrencyConverter.updateFromUsdBase(
                rates = rates,
                quotedAtEpochMillis = quotedAt,
                provider = ExchangeRateApi.PROVIDER_NAME,
                providerUrl = ExchangeRateApi.PROVIDER_URL
            )
            true
        } catch (e: Exception) {
            // Offline, DNS failure, captive portal. Keep what we have.
            Log.d(TAG, "Rate refresh skipped: ${e.message}")
            haveCache
        }
    }

    /**
     * Tries the SubFlow backend's /rates.
     *
     * Returns false for every reason not to use it - not configured,
     * unreachable, or unkeyed - and the caller then goes direct. Nothing is
     * cached on a rejection, so a half-answer from a misconfigured server
     * cannot displace good rates already in hand.
     */
    private suspend fun fetchFromBackend(): Boolean {
        if (!BuildConfig.BACKEND_ENABLED) return false
        return try {
            val res = backendApi.getRates()
            val body = res.body()
            val rates = body?.rates

            when {
                !res.isSuccessful || rates.isNullOrEmpty() -> false
                // Unkeyed: the server is just proxying the public endpoint.
                !body.keyed -> false
                !body.baseCurrency.equals("USD", ignoreCase = true) -> false
                rates["USD"] != 1.0 -> false
                SupportedCurrencies.any { rates[it.code] == null } -> false
                else -> {
                    val quotedAt = body.updatedAt?.let(::parseIsoInstant) ?: 0L
                    prefs.edit()
                        .putString(KEY_RATES, gson.toJson(rates))
                        .putLong(KEY_QUOTED_AT, quotedAt)
                        // The backend refreshes on the provider's schedule, so
                        // check back daily rather than trying to mirror it.
                        .putLong(KEY_NEXT_UPDATE, System.currentTimeMillis() + BACKEND_RECHECK_MS)
                        .apply()

                    CurrencyConverter.updateFromUsdBase(
                        rates = rates,
                        quotedAtEpochMillis = quotedAt,
                        provider = body.provider.orEmpty().ifBlank { ExchangeRateApi.PROVIDER_NAME },
                        providerUrl = body.providerUrl.orEmpty().ifBlank { ExchangeRateApi.PROVIDER_URL }
                    )
                    true
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Backend rates unavailable, falling back: ${e.message}")
            false
        }
    }

    /** ISO-8601 to epoch millis, 0 when unparseable. */
    private fun parseIsoInstant(value: String): Long = runCatching {
        java.time.Instant.parse(value).toEpochMilli()
    }.getOrDefault(0L)

    private companion object {
        const val TAG = "SubFlowRates"
        const val PREFS_NAME = "subflow_exchange_rates"
        const val KEY_RATES = "rates_json"
        const val KEY_QUOTED_AT = "quoted_at_millis"
        const val KEY_NEXT_UPDATE = "next_update_millis"
        const val BACKEND_RECHECK_MS = 24L * 60 * 60 * 1000
    }
}
