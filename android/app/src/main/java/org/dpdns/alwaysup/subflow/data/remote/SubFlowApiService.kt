package org.dpdns.alwaysup.subflow.data.remote

import com.google.gson.annotations.SerializedName
import org.dpdns.alwaysup.subflow.BuildConfig
import org.dpdns.alwaysup.subflow.domain.model.BillingCycle
import org.dpdns.alwaysup.subflow.domain.model.PresetService
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.model.UserProfile
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

data class GoogleLoginRequest(@SerializedName("id_token") val idToken: String)

data class PurchaseReportRequest(
    @SerializedName("product_id") val productId: String,
    @SerializedName("purchase_token") val purchaseToken: String,
    @SerializedName("order_id") val orderId: String,
    @SerializedName("package_name") val packageName: String
)

data class PurchaseReportResponse(
    @SerializedName("acknowledged") val acknowledged: Boolean,
    @SerializedName("pro_tier") val proTier: String
)
data class GuestLoginRequest(@SerializedName("guest_id") val guestId: String)

data class AuthResponse(
    @SerializedName("user") val user: UserDto,
    @SerializedName("token") val token: String
)

data class UserDto(
    val id: String,
    val email: String,
    val name: String,
    val picture: String,
    @SerializedName("auth_provider") val authProvider: String,
    @SerializedName("is_pro") val isPro: Boolean,
    @SerializedName("pro_tier") val proTier: String
) {
    fun toDomain(): UserProfile = UserProfile(
        id = id,
        email = email,
        name = name,
        picture = picture,
        authProvider = authProvider,
        isPro = isPro,
        proTier = org.dpdns.alwaysup.subflow.domain.model.ProTier.fromKey(proTier)
    )
}

data class PresetDto(
    val id: String,
    val name: String,
    val category: String,
    @SerializedName("brand_color") val brandColor: String,
    @SerializedName("icon_url") val iconUrl: String,
    @SerializedName("default_cycle") val defaultCycle: String,
    @SerializedName("default_amount_usd") val defaultAmountUsd: Double,
    @SerializedName("website_url") val websiteUrl: String,
    @SerializedName("is_popular") val isPopular: Boolean
) {
    fun toDomain(): PresetService = PresetService(
        id = id,
        name = name,
        category = category,
        brandColor = brandColor,
        iconUrl = iconUrl,
        defaultCycle = BillingCycle.fromKey(defaultCycle),
        defaultAmountUSD = defaultAmountUsd,
        websiteUrl = websiteUrl,
        isPopular = isPopular
    )
}

data class PresetsResponse(
    val count: Int,
    val presets: List<PresetDto>
)

data class CurrencyRatesResponse(
    @SerializedName("base_currency") val baseCurrency: String,
    val rates: Map<String, Double>,
    /** Provider's quote time, ISO-8601. Not when this device fetched it. */
    @SerializedName("updated_at") val updatedAt: String? = null,
    /** Attribution the feed's terms require wherever the rates are shown. */
    val provider: String? = null,
    @SerializedName("provider_url") val providerUrl: String? = null,
    /**
     * Whether the server fetched these with its own API key.
     *
     * False means it is relaying the same public endpoint this app can reach
     * directly, so there is nothing to gain by going through it and one more
     * thing that can be down.
     */
    val keyed: Boolean = false
)

data class SyncRequestDto(
    @SerializedName("last_sync_timestamp") val lastSyncTimestamp: Long,
    val subscriptions: List<Subscription>
)

data class SyncResponseDto(
    @SerializedName("server_timestamp") val serverTimestamp: Long,
    val subscriptions: List<Subscription>
)

interface SubFlowApiService {
    @POST("auth/google")
    suspend fun loginGoogle(@Body req: GoogleLoginRequest): Response<AuthResponse>

    @POST("auth/guest")
    suspend fun loginGuest(@Body req: GuestLoginRequest): Response<AuthResponse>

    @GET("presets")
    suspend fun getPresets(): Response<PresetsResponse>

    @GET("rates")
    suspend fun getRates(): Response<CurrencyRatesResponse>

    @POST("billing/purchase")
    suspend fun reportPurchase(
        @Header("Authorization") token: String,
        @Body req: PurchaseReportRequest
    ): Response<PurchaseReportResponse>

    @POST("sync")
    suspend fun syncSubscriptions(
        @Header("Authorization") token: String,
        @Body req: SyncRequestDto
    ): Response<SyncResponseDto>

    companion object {
        @Volatile
        private var instance: SubFlowApiService? = null

        /**
         * Single shared Retrofit instance. Each call site used to build its own,
         * which meant a separate OkHttp connection pool and thread pool per
         * repository.
         */
        fun create(): SubFlowApiService = instance ?: synchronized(this) {
            instance ?: build().also { instance = it }
        }

        private fun build(): SubFlowApiService {
            val builder = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)

            if (BuildConfig.NETWORK_LOGGING) {
                // Bodies contain id tokens, so this is debug-only by construction.
                builder.addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
                )
            }

            return Retrofit.Builder()
                .baseUrl(BuildConfig.API_BASE_URL)
                .client(builder.build())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SubFlowApiService::class.java)
        }
    }
}
