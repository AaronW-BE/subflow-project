package org.dpdns.alwaysup.subflow.data.remote

import com.google.gson.annotations.SerializedName
import org.dpdns.alwaysup.subflow.BuildConfig
import org.dpdns.alwaysup.subflow.domain.model.BillingCycle
import org.dpdns.alwaysup.subflow.domain.model.PresetService
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.model.TrialOutcome
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

/**
 * A subscription as it travels to and from the server.
 *
 * Deliberately not the domain [Subscription]. The default Gson instance names
 * fields exactly as Kotlin declares them and writes enums with `name()`, so
 * sending the domain type put `firstBillDate` and `"MONTHLY"` on the wire
 * where the server reads `first_bill_date` and `"monthly"`. Of sixteen fields
 * only six ever lined up - id, name, category, amount, currency and notes -
 * and every date, the active flag, the colour, the icon and the update
 * timestamp were dropped in both directions, silently, because JSON that does
 * not match simply decodes as absent.
 *
 * Fixing that by renaming the domain type's fields was not an option: the same
 * class and the same Gson instance write the backup file, so the rename would
 * have made every backup already sitting on a user's disk restore as blanks.
 *
 * Strings are nullable here rather than defaulted because Gson builds objects
 * without running the constructor - an absent key leaves a null behind
 * whatever the declared type says, and `omitempty` on the server means absent
 * keys are normal.
 */
data class SubscriptionDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("amount") val amount: Double = 0.0,
    @SerializedName("currency") val currency: String? = null,
    /** The cycle's own key ("monthly"), not the enum constant ("MONTHLY"). */
    @SerializedName("cycle") val cycle: String? = null,
    @SerializedName("first_bill_date") val firstBillDate: String? = null,
    @SerializedName("next_bill_date") val nextBillDate: String? = null,
    @SerializedName("reminder_days_before") val reminderDaysBefore: Int = 0,
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("color_hex") val colorHex: String? = null,
    @SerializedName("icon_url") val iconUrl: String? = null,
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("updated_at") val updatedAt: Long = 0L,
    @SerializedName("is_deleted") val isDeleted: Boolean = false,
    @SerializedName("is_trial") val isTrial: Boolean = false,
    @SerializedName("trial_end_date") val trialEndDate: String? = null,
    @SerializedName("trial_converts") val trialConverts: Boolean = true,
    @SerializedName("post_trial_amount") val postTrialAmount: Double = 0.0,
    @SerializedName("post_trial_cycle") val postTrialCycle: String? = null,
    @SerializedName("trial_outcome") val trialOutcome: String? = null
) {
    fun toDomain(): Subscription = Subscription(
        id = id,
        name = name.orEmpty(),
        category = category ?: "Streaming",
        amount = amount,
        currency = currency ?: "USD",
        cycle = BillingCycle.fromKey(cycle.orEmpty()),
        firstBillDate = firstBillDate.orEmpty(),
        nextBillDate = nextBillDate.orEmpty(),
        reminderDaysBefore = reminderDaysBefore,
        isActive = isActive,
        colorHex = colorHex ?: "#5856D6",
        iconUrl = iconUrl.orEmpty(),
        notes = notes.orEmpty(),
        updatedAt = updatedAt,
        isDeleted = isDeleted,
        isTrial = isTrial,
        trialEndDate = trialEndDate.orEmpty(),
        trialConverts = trialConverts,
        postTrialAmount = postTrialAmount,
        postTrialCycle = BillingCycle.fromKey(postTrialCycle.orEmpty()),
        trialOutcome = TrialOutcome.fromKey(trialOutcome.orEmpty())
    )

    companion object {
        fun fromDomain(d: Subscription): SubscriptionDto = SubscriptionDto(
            id = d.id,
            name = d.name,
            category = d.category,
            amount = d.amount,
            currency = d.currency,
            cycle = d.cycle.key,
            firstBillDate = d.firstBillDate,
            nextBillDate = d.nextBillDate,
            reminderDaysBefore = d.reminderDaysBefore,
            isActive = d.isActive,
            colorHex = d.colorHex,
            iconUrl = d.iconUrl,
            notes = d.notes,
            updatedAt = d.updatedAt,
            isDeleted = d.isDeleted,
            isTrial = d.isTrial,
            trialEndDate = d.trialEndDate,
            trialConverts = d.trialConverts,
            postTrialAmount = d.postTrialAmount,
            postTrialCycle = d.postTrialCycle.key,
            trialOutcome = d.trialOutcome.key
        )
    }
}

data class SyncRequestDto(
    @SerializedName("last_sync_timestamp") val lastSyncTimestamp: Long,
    @SerializedName("subscriptions") val subscriptions: List<SubscriptionDto>
)

data class SyncResponseDto(
    @SerializedName("server_timestamp") val serverTimestamp: Long,
    @SerializedName("subscriptions") val subscriptions: List<SubscriptionDto>
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
