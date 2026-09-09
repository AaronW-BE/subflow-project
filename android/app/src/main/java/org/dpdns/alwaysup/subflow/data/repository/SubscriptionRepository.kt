package org.dpdns.alwaysup.subflow.data.repository

import android.content.Context
import android.util.Log
import org.dpdns.alwaysup.subflow.data.local.SubFlowDatabase
import org.dpdns.alwaysup.subflow.data.local.SubscriptionEntity
import org.dpdns.alwaysup.subflow.data.remote.SubFlowApiService
import org.dpdns.alwaysup.subflow.data.remote.SubscriptionDto
import org.dpdns.alwaysup.subflow.data.remote.SyncRequestDto
import org.dpdns.alwaysup.subflow.domain.model.BillingCycle
import org.dpdns.alwaysup.subflow.domain.model.PresetService
import org.dpdns.alwaysup.subflow.domain.model.SubFlowBackupContainer
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.model.TrialOutcome
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
    private val exchangeRates = ExchangeRateRepository(context)

    init {
        // Load the cached quote before anything renders a total. Without this
        // the first frame after a cold launch would use the built-in table and
        // then visibly correct itself once the network answered.
        exchangeRates.primeFromCache()
    }

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
        //
        // A trial is the exception, and normalising it here is what lets the
        // rest of the app stay ignorant of trials: its next dated event is the
        // end of the trial, and it costs nothing until then. Forcing amount to
        // zero at the single write path is why no total, chart or breakdown has
        // to remember to exclude trials - and why none of them can forget to.
        val normalised = if (subscription.isTrial) {
            subscription.copy(
                amount = 0.0,
                nextBillDate = subscription.trialEndDate.ifBlank { subscription.firstBillDate },
                updatedAt = System.currentTimeMillis()
            )
        } else {
            subscription.copy(
                nextBillDate = DateCalculators.computeNextRenewalDate(
                    subscription.firstBillDate,
                    subscription.cycle
                ),
                updatedAt = System.currentTimeMillis()
            )
        }
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
        // Trials are skipped: their date is an end, not a renewal. Advancing it
        // would replace a finished trial awaiting the user's answer with a
        // countdown to a month that was never going to happen.
        val stale = dao.getActiveSubscriptions().filter {
            !it.isTrial && DateCalculators.calculateDaysUntil(it.nextBillDate) < 0
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
     * Refreshes exchange rates, in every build type.
     *
     * This deliberately does not check BACKEND_ENABLED. That flag also gates
     * sign-in and cloud sync, so requiring it would have meant the choice was
     * "stale rates" or "start uploading the user's subscription list" - see
     * ExchangeRateRepository for why rates get their own path.
     *
     * At most one request per provider publication, and a failure leaves the
     * previously loaded rates alone.
     */
    suspend fun refreshExchangeRates(): Boolean = exchangeRates.refreshIfStale()

    suspend fun syncWithServer(token: String?): Result<Int> {
        if (!BuildConfig.BACKEND_ENABLED) {
            return Result.failure(IllegalStateException("SYNC_DISABLED"))
        }
        if (token.isNullOrBlank()) return Result.failure(IllegalStateException("NOT_SIGNED_IN"))
        return try {
            val lastSync = prefs.getLong("last_sync_timestamp", 0)
            val modified = dao.getModifiedSince(lastSync)
                .map { SubscriptionDto.fromDomain(it.toDomain()) }

            val res = api.syncSubscriptions(
                token = "Bearer $token",
                req = SyncRequestDto(lastSyncTimestamp = lastSync, subscriptions = modified)
            )

            val body = res.body()
            if (res.isSuccessful && body != null) {
                if (body.subscriptions.isNotEmpty()) {
                    dao.insertAll(
                        body.subscriptions.map { SubscriptionEntity.fromDomain(it.toDomain()) }
                    )
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
            val entities = subs.map { raw ->
                val sub = raw.repaired()
                SubscriptionEntity.fromDomain(
                    sub.copy(
                        updatedAt = System.currentTimeMillis(),
                        isDeleted = false,
                        nextBillDate = if (sub.isTrial) {
                            sub.trialEndDate.ifBlank { sub.nextBillDate }
                        } else {
                            DateCalculators.computeNextRenewalDate(
                                sub.firstBillDate.ifBlank { sub.nextBillDate },
                                sub.cycle
                            )
                        }
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
     * Fills in whatever a backup file left out.
     *
     * Gson builds objects without running the Kotlin constructor, so a field
     * missing from the JSON arrives as null no matter how non-null its declared
     * type is. Every backup written before trial support lacks the six trial
     * fields, so without this a restore feeds nulls into NOT NULL columns and
     * fails on the user's own data. The older fields were exposed to the same
     * hazard all along; it was simply unreachable while the app itself wrote
     * every field it read.
     */
    @Suppress("USELESS_ELVIS")
    private fun Subscription.repaired(): Subscription = copy(
        name = name ?: "",
        category = category ?: "Streaming",
        currency = currency ?: "USD",
        cycle = cycle ?: BillingCycle.MONTHLY,
        firstBillDate = firstBillDate ?: "",
        nextBillDate = nextBillDate ?: "",
        colorHex = colorHex ?: "#5856D6",
        iconUrl = iconUrl ?: "",
        notes = notes ?: "",
        trialEndDate = trialEndDate ?: "",
        postTrialCycle = postTrialCycle ?: BillingCycle.MONTHLY,
        trialOutcome = trialOutcome ?: TrialOutcome.PENDING
    )

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
        /**
         * 2 since trial fields were added. Restore ignores this number and
         * repairs whatever is missing, so a v1 file still imports.
         */
        const val BACKUP_VERSION = 2
    }
}
