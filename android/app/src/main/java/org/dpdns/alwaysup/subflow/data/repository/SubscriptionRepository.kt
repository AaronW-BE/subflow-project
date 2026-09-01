package org.dpdns.alwaysup.subflow.data.repository

import android.content.Context
import android.util.Log
import org.dpdns.alwaysup.subflow.data.local.SubFlowDatabase
import org.dpdns.alwaysup.subflow.data.local.SubscriptionEntity
import org.dpdns.alwaysup.subflow.data.remote.SubFlowApiService
import org.dpdns.alwaysup.subflow.data.remote.SyncRequestDto
import org.dpdns.alwaysup.subflow.domain.model.BillingCycle
import org.dpdns.alwaysup.subflow.domain.model.PresetService
import org.dpdns.alwaysup.subflow.domain.model.SubFlowBackupContainer
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.util.CurrencyConverter
import org.dpdns.alwaysup.subflow.domain.util.DateCalculators
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.dpdns.alwaysup.subflow.BuildConfig

/** Raised by [SubscriptionRepository.saveSubscription] when the free tier is full. */
class QuotaReachedException : Exception("Free tier quota reached")

class SubscriptionRepository(
    context: Context,
    private val api: SubFlowApiService = SubFlowApiService.create()
) {
    private val dao = SubFlowDatabase.getDatabase(context).subscriptionDao()
    private val prefs = context.getSharedPreferences("subflow_prefs", Context.MODE_PRIVATE)

    val activeSubscriptions: Flow<List<Subscription>> = dao.observeActiveSubscriptions()
        .map { list -> list.map { it.toDomain() } }

    suspend fun getActiveCount(): Int = dao.getActiveCount()

    suspend fun getById(id: String): Subscription? = dao.getById(id)?.toDomain()

    suspend fun saveSubscription(subscription: Subscription, isPro: Boolean): Result<Unit> {
        val existing = dao.getById(subscription.id)
        if (existing == null && !isPro && dao.getActiveCount() >= FREE_TIER_LIMIT) {
            return Result.failure(QuotaReachedException())
        }

        // Always store a renewal date that is genuinely in the future, otherwise
        // the countdown and the reminder worker both go stale.
        val normalised = subscription.copy(
            nextBillDate = DateCalculators.computeNextRenewalDate(
                subscription.firstBillDate,
                subscription.cycle
            ),
            updatedAt = System.currentTimeMillis()
        )
        dao.insertOrUpdate(SubscriptionEntity.fromDomain(normalised))
        return Result.success(Unit)
    }

    suspend fun deleteSubscription(id: String) = dao.markDeleted(id)

    suspend fun restoreSubscription(id: String) = dao.restoreDeleted(id)

    suspend fun clearAllData() = dao.clearAll()

    /**
     * Advances any renewal date that has already passed. Runs on launch so a
     * user returning after a month does not see a wall of "overdue" rows.
     */
    suspend fun rollForwardDueRenewals(): Int {
        val stale = dao.getActiveSubscriptions().filter {
            DateCalculators.calculateDaysUntil(it.nextBillDate) < 0
        }
        stale.forEach { entity ->
            val domain = entity.toDomain()
            val next = DateCalculators.computeNextRenewalDate(domain.nextBillDate, domain.cycle)
            dao.insertOrUpdate(
                SubscriptionEntity.fromDomain(domain.copy(nextBillDate = next))
            )
        }
        return stale.size
    }

    suspend fun fetchPresets(): List<PresetService> = if (!BuildConfig.BACKEND_ENABLED) {
        defaultLocalPresets().map { it.copy(category = normaliseCategory(it.category)) }
    } else try {
        val res = api.getPresets()
        val remote = res.body()?.presets?.map { it.toDomain() }.orEmpty()
        // Merge so a thin server catalogue never shrinks the local one.
        val merged = (remote + defaultLocalPresets()).distinctBy { it.id }
        (if (merged.isEmpty()) defaultLocalPresets() else merged)
            .map { it.copy(category = normaliseCategory(it.category)) }
    } catch (e: Exception) {
        defaultLocalPresets()
    }

    /**
     * "Entertainment" is the legacy label the server catalogue still uses for
     * what the app now calls "Streaming". Normalising on load keeps the
     * category picker and the dashboard filter in agreement.
     */
    private fun normaliseCategory(category: String): String = when (category.lowercase()) {
        "entertainment" -> "Streaming"
        else -> category
    }

    /**
     * Refreshes exchange rates. Pro-only in the UI, but the fetch is harmless
     * for everyone and keeps the cache warm for a later upgrade.
     */
    suspend fun refreshExchangeRates(): Boolean = if (!BuildConfig.BACKEND_ENABLED) {
        false
    } else try {
        val res = api.getRates()
        val rates = res.body()?.rates
        if (res.isSuccessful && !rates.isNullOrEmpty()) {
            CurrencyConverter.updateFromUsdBase(rates)
            true
        } else false
    } catch (e: Exception) {
        Log.d(TAG, "Rate refresh skipped: ${e.message}")
        false
    }

    suspend fun syncWithServer(token: String?): Result<Int> {
        if (!BuildConfig.BACKEND_ENABLED) {
            return Result.failure(IllegalStateException("SYNC_DISABLED"))
        }
        if (token.isNullOrBlank()) return Result.failure(IllegalStateException("NOT_SIGNED_IN"))
        return try {
            val lastSync = prefs.getLong("last_sync_timestamp", 0)
            val modified = dao.getModifiedSince(lastSync).map { it.toDomain() }

            val res = api.syncSubscriptions(
                token = "Bearer $token",
                req = SyncRequestDto(lastSyncTimestamp = lastSync, subscriptions = modified)
            )

            val body = res.body()
            if (res.isSuccessful && body != null) {
                if (body.subscriptions.isNotEmpty()) {
                    dao.insertAll(body.subscriptions.map { SubscriptionEntity.fromDomain(it) })
                }
                prefs.edit().putLong("last_sync_timestamp", body.serverTimestamp).apply()
                Result.success(body.subscriptions.size)
            } else {
                Result.failure(Exception("Sync failed: ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportBackupJson(): String {
        val subs = dao.getActiveSubscriptions().map { it.toDomain() }
        val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
        return gson.toJson(
            SubFlowBackupContainer(
                version = BACKUP_VERSION,
                exportTimestamp = System.currentTimeMillis(),
                subscriptions = subs
            )
        )
    }

    suspend fun restoreBackupJson(jsonString: String): Result<Int> = try {
        val backup = com.google.gson.Gson().fromJson(jsonString, SubFlowBackupContainer::class.java)
        val subs = backup?.subscriptions
        if (subs.isNullOrEmpty()) {
            Result.failure(IllegalArgumentException("Backup data is empty or invalid"))
        } else {
            val entities = subs.map { sub ->
                SubscriptionEntity.fromDomain(
                    sub.copy(
                        updatedAt = System.currentTimeMillis(),
                        isDeleted = false,
                        nextBillDate = DateCalculators.computeNextRenewalDate(
                            sub.firstBillDate.ifBlank { sub.nextBillDate },
                            sub.cycle
                        )
                    )
                )
            }
            dao.insertAll(entities)
            Result.success(entities.size)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Bundled catalogue. Prices are typical US list prices used only as a
     * starting value in the form; the user edits them to what they actually pay.
     */
    private fun defaultLocalPresets(): List<PresetService> = listOf(
        preset("netflix", "Netflix", "Streaming", "#E50914", 15.49, "https://netflix.com"),
        preset("spotify", "Spotify", "Streaming", "#1DB954", 11.99, "https://spotify.com"),
        preset("youtube", "YouTube Premium", "Streaming", "#FF0000", 13.99, "https://youtube.com"),
        preset("disney", "Disney+", "Streaming", "#113CCF", 15.99, "https://disneyplus.com"),
        preset("max", "HBO Max", "Streaming", "#7B2BF9", 16.99, "https://max.com"),
        preset("appletv", "Apple TV+", "Streaming", "#1C1C1E", 9.99, "https://tv.apple.com"),
        preset("primevideo", "Prime Video", "Streaming", "#00A8E1", 8.99, "https://primevideo.com"),
        preset("hulu", "Hulu", "Streaming", "#1CE783", 18.99, "https://hulu.com"),
        preset("crunchyroll", "Crunchyroll", "Streaming", "#F47521", 7.99, "https://crunchyroll.com"),
        preset("applemusic", "Apple Music", "Streaming", "#FA243C", 10.99, "https://music.apple.com"),
        preset("chatgpt", "ChatGPT Plus", "Productivity", "#10A37F", 20.00, "https://openai.com"),
        preset("claude", "Claude Pro", "Productivity", "#D97757", 20.00, "https://claude.ai"),
        preset("github_copilot", "GitHub Copilot", "Productivity", "#24292F", 10.00, "https://github.com"),
        preset("notion", "Notion", "Productivity", "#111111", 10.00, "https://notion.so"),
        preset("figma", "Figma", "Productivity", "#F24E1E", 15.00, "https://figma.com"),
        preset("adobe_cc", "Adobe Creative Cloud", "Productivity", "#DA1F26", 59.99, "https://adobe.com"),
        preset("microsoft365", "Microsoft 365", "Productivity", "#D83B01", 9.99, "https://microsoft.com"),
        preset("slack", "Slack", "Productivity", "#4A154B", 8.75, "https://slack.com"),
        preset("icloud", "iCloud+", "Cloud", "#007AFF", 2.99, "https://apple.com"),
        preset("google_one", "Google One", "Cloud", "#4285F4", 1.99, "https://one.google.com"),
        preset("dropbox", "Dropbox", "Cloud", "#0061FF", 11.99, "https://dropbox.com"),
        preset("backblaze", "Backblaze", "Cloud", "#E21E29", 9.00, "https://backblaze.com"),
        preset("amazon_prime", "Amazon Prime", "Utilities", "#FF9900", 14.99, "https://amazon.com"),
        preset("nordvpn", "NordVPN", "Utilities", "#4687FF", 12.99, "https://nordvpn.com"),
        preset("1password", "1Password", "Utilities", "#1A8CFF", 3.99, "https://1password.com"),
        preset("psplus", "PlayStation Plus", "Gaming", "#0070D1", 10.99, "https://playstation.com"),
        preset("xbox_gamepass", "Xbox Game Pass", "Gaming", "#107C10", 16.99, "https://xbox.com"),
        preset("nintendo_online", "Nintendo Switch Online", "Gaming", "#E60012", 3.99, "https://nintendo.com"),
        preset("gym", "Gym membership", "Health", "#E11D48", 45.00, ""),
        preset("headspace", "Headspace", "Health", "#F47D31", 12.99, "https://headspace.com"),
        preset("strava", "Strava", "Health", "#FC4C02", 11.99, "https://strava.com"),
        preset("duolingo", "Duolingo Super", "Education", "#58CC02", 12.99, "https://duolingo.com"),
        preset("nytimes", "The New York Times", "News", "#1C1C1E", 4.00, "https://nytimes.com"),
        preset("medium", "Medium", "News", "#000000", 5.00, "https://medium.com")
    )

    private fun preset(
        id: String,
        name: String,
        category: String,
        color: String,
        amount: Double,
        url: String
    ) = PresetService(
        id = id,
        name = name,
        category = category,
        brandColor = color,
        iconUrl = "",
        defaultCycle = BillingCycle.MONTHLY,
        defaultAmountUSD = amount,
        websiteUrl = url,
        isPopular = true
    )

    companion object {
        private const val TAG = "SubFlowRepo"

        /** Free tier ceiling. Mirrored in ADR 0002 and on the paywall. */
        const val FREE_TIER_LIMIT = 5
        const val BACKUP_VERSION = 1
    }
}
