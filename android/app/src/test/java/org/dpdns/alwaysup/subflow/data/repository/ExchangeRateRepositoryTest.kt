package org.dpdns.alwaysup.subflow.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.dpdns.alwaysup.subflow.data.preferences.SupportedCurrencies
import org.dpdns.alwaysup.subflow.data.remote.*
import org.dpdns.alwaysup.subflow.domain.util.CurrencyConverter
import org.dpdns.alwaysup.subflow.domain.util.RatesSnapshot
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import retrofit2.Response
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
class ExchangeRateRepositoryTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val prefs = context.getSharedPreferences("subflow_exchange_rates", Context.MODE_PRIVATE)
    private val original = CurrencyConverter.captureSnapshot()
    private val rates = SupportedCurrencies.associate { it.code to if (it.code == "USD") 1.0 else 0.9 }
    private val quotedAt = 1900000000000L

    private fun restorePublication() {
        val field = CurrencyConverter::class.java.getDeclaredField("publication").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (field.get(null) as MutableStateFlow<RatesSnapshot>).value = original
    }

    @After fun restore() {
        restorePublication()
        prefs.edit().clear().commit()
    }

    private fun repository(backend: Boolean, onRefresh: () -> Unit): ExchangeRateRepository {
        val api = object : ExchangeRateApi {
            override suspend fun latestUsd() = Response.success(ExchangeRateResponse(
                "success", "USD", rates, quotedAt / 1000, quotedAt / 1000 + 86400))
        }
        val backendApi = Proxy.newProxyInstance(SubFlowApiService::class.java.classLoader,
            arrayOf(SubFlowApiService::class.java)) { _, method, _ ->
            check(method.name == "getRates")
            Response.success(CurrencyRatesResponse("USD", rates,
                java.time.Instant.ofEpochMilli(quotedAt).toString(), "Custom Feed", "https://custom.example", true))
        } as SubFlowApiService
        return ExchangeRateRepository(context, api, backendApi, backend) { onRefresh() }
    }

    @Test fun backendPublicationThenWidgetCachePrimeIsIdempotent() = runBlocking {
        prefs.edit().clear().commit()
        var live: RatesSnapshot? = null
        lateinit var repository: ExchangeRateRepository
        repository = repository(true) {
            live = CurrencyConverter.captureSnapshot()
            repository.primeFromCache()
        }
        assertTrue(repository.refreshIfStale(true))
        val published = requireNotNull(live)
        val primed = CurrencyConverter.captureSnapshot()
        assertEquals("Custom Feed", primed.providerName)
        assertEquals("https://custom.example", primed.providerUrl)
        assertEquals(quotedAt, primed.quotedAtEpochMillis)
        assertEquals(published.ratesPerUsd, primed.ratesPerUsd)
        assertEquals(published.revision, primed.revision)
        assertSame(published, primed)
        assertColdCacheRestores(repository, published)
    }

    @Test fun directPublicationRestoresItsOriginWithoutRevisionChange() = runBlocking {
        prefs.edit().clear().commit()
        val repository = repository(false) {}
        assertTrue(repository.refreshIfStale(true))
        val live = CurrencyConverter.captureSnapshot()
        repository.primeFromCache()
        assertEquals(ExchangeRateApi.PROVIDER_NAME, live.providerName)
        assertEquals(ExchangeRateApi.PROVIDER_URL, live.providerUrl)
        assertEquals(quotedAt, live.quotedAtEpochMillis)
        assertSame(live, CurrencyConverter.captureSnapshot())
        assertColdCacheRestores(repository, live)
    }
    private fun assertColdCacheRestores(repository: ExchangeRateRepository, live: RatesSnapshot) {
        restorePublication()
        repository.primeFromCache()
        val restored = CurrencyConverter.captureSnapshot()
        assertEquals(live.ratesPerUsd, restored.ratesPerUsd)
        assertEquals(live.quotedAtEpochMillis, restored.quotedAtEpochMillis)
        assertEquals(live.providerName, restored.providerName)
        assertEquals(live.providerUrl, restored.providerUrl)
        repository.primeFromCache()
        assertSame(restored, CurrencyConverter.captureSnapshot())
    }

    @Test fun legacyCacheLoadsWithUnknownOriginAndDoesNotReplaceLiveAttribution() {
        prefs.edit().clear()
            .putString("rates_json", "{\"USD\":1.0,\"EUR\":0.7}")
            .putLong("quoted_at_millis", quotedAt).commit()
        val repository = repository(false) { fail("cache must not refresh widget") }
        repository.primeFromCache()
        val legacy = CurrencyConverter.captureSnapshot()
        assertEquals(0.7, legacy.ratesPerUsd.getValue("EUR"), 0.0)
        assertEquals(quotedAt, legacy.quotedAtEpochMillis)
        assertTrue(legacy.providerName.isNullOrEmpty())
        assertTrue(legacy.providerUrl.isNullOrEmpty())
        repository.primeFromCache()
        assertSame(legacy, CurrencyConverter.captureSnapshot())
        CurrencyConverter.updateFromUsdBase(rates, quotedAt, "Live Feed", "https://live.example")
        val live = CurrencyConverter.captureSnapshot()
        repository.primeFromCache()
        assertSame(live, CurrencyConverter.captureSnapshot())
    }

    @Test fun olderAttributedCacheDoesNotReplaceNewerLivePublication() = runBlocking {
        prefs.edit().clear().commit()
        val repository = repository(true) {}
        assertTrue(repository.refreshIfStale(true))
        CurrencyConverter.updateFromUsdBase(rates + ("EUR" to 0.8), quotedAt + 1000,
            "Newer Feed", "https://newer.example")
        val live = CurrencyConverter.captureSnapshot()
        repository.primeFromCache()
        assertSame(live, CurrencyConverter.captureSnapshot())
    }

}
