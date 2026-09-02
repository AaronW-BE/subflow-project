package org.dpdns.alwaysup.subflow.data.remote

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import org.dpdns.alwaysup.subflow.BuildConfig
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Live foreign exchange rates, fetched straight from the provider.
 *
 * Deliberately separate from [SubFlowApiService] and from BACKEND_ENABLED.
 * That flag gates sign-in, preset fetching and cloud sync — turning it on to
 * get rates would also start sending the user's subscription list off the
 * device, which is exactly what ADR 0001 says this app does not do. Rates are
 * public reference data and carry nothing about the user, so they get their own
 * path that works in a release build with no backend deployed at all.
 *
 * Going direct also keeps the licence clean: each install is the provider's own
 * end user rather than a client of a server re-serving their data, and their
 * terms permit caching for "customer end-use only".
 *
 * The endpoint needs no API key. It publishes once a day and states when it
 * will publish next, which is what [ExchangeRateRepository] paces itself on.
 * Attribution is required and is rendered in Settings.
 */
interface ExchangeRateApi {

    @GET("v6/latest/USD")
    suspend fun latestUsd(): Response<ExchangeRateResponse>

    companion object {
        const val BASE_URL = "https://open.er-api.com/"

        /** Required by the provider's terms wherever these rates are shown. */
        const val PROVIDER_NAME = "Exchange Rate API"
        const val PROVIDER_URL = "https://www.exchangerate-api.com"

        @Volatile
        private var instance: ExchangeRateApi? = null

        fun create(): ExchangeRateApi = instance ?: synchronized(this) {
            instance ?: build().also { instance = it }
        }

        private fun build(): ExchangeRateApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .apply {
                    if (BuildConfig.NETWORK_LOGGING) {
                        addInterceptor(
                            HttpLoggingInterceptor().apply {
                                level = HttpLoggingInterceptor.Level.BASIC
                            }
                        )
                    }
                }
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ExchangeRateApi::class.java)
        }
    }
}

data class ExchangeRateResponse(
    val result: String?,
    @SerializedName("base_code") val baseCode: String?,
    val rates: Map<String, Double>?,
    /** Provider's quote time, epoch seconds. */
    @SerializedName("time_last_update_unix") val timeLastUpdateUnix: Long = 0L,
    /** When the provider expects to publish again, epoch seconds. */
    @SerializedName("time_next_update_unix") val timeNextUpdateUnix: Long = 0L,
    /**
     * Non-zero once the provider has scheduled this endpoint for shutdown. It
     * exists precisely so callers can notice before the endpoint disappears.
     */
    @SerializedName("time_eol_unix") val timeEolUnix: Long = 0L
)
