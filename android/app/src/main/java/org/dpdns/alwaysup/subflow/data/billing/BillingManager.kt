package org.dpdns.alwaysup.subflow.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import org.dpdns.alwaysup.subflow.data.repository.AuthRepository
import org.dpdns.alwaysup.subflow.domain.model.ProTier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Where the Play billing connection currently stands, for the paywall UI. */
enum class BillingConnectionState { CONNECTING, READY, UNAVAILABLE }

/**
 * A purchasable Pro plan, priced by Google Play in the user's own currency.
 * Never hard-code prices in the UI: Play localises them per country and the
 * store listing must match what the user is actually charged.
 */
data class ProPlan(
    val productId: String,
    val tier: ProTier,
    val formattedPrice: String,
    val priceAmountMicros: Long,
    val priceCurrencyCode: String,
    val offerToken: String?,
    val freeTrialDays: Int
) {
    val hasFreeTrial: Boolean get() = freeTrialDays > 0
}

/** One-shot outcomes the UI turns into a snackbar. */
sealed interface BillingEvent {
    data object PurchaseSuccess : BillingEvent
    data object PurchaseCancelled : BillingEvent
    data object PurchasePending : BillingEvent
    data class PurchaseFailed(val debugMessage: String) : BillingEvent
    data object RestoredPro : BillingEvent
    data object RestoreNothingFound : BillingEvent
    data object PlansUnavailable : BillingEvent
}

class BillingManager(
    private val context: Context,
    private val authRepository: AuthRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : PurchasesUpdatedListener, BillingClientStateListener {

    companion object {
        private const val TAG = "SubFlowBilling"

        const val SKU_MONTHLY = "subflow_sub_monthly"
        const val SKU_ANNUAL = "subflow_sub_annual"
        const val SKU_LIFETIME = "subflow_inapp_lifetime"

        val SUBSCRIPTION_SKUS = setOf(SKU_MONTHLY, SKU_ANNUAL)
        val ALL_SKUS = setOf(SKU_MONTHLY, SKU_ANNUAL, SKU_LIFETIME)

        private const val MAX_RECONNECT_DELAY_MS = 60_000L
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        // Billing 8+ reconnects on its own after the service drops, with its own
        // backoff. We keep a retry for a *failed handshake* below, which that
        // does not cover, but the disconnect path is now the library's job.
        .enableAutoServiceReconnection()
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    private val _plans = MutableStateFlow<Map<ProTier, ProPlan>>(emptyMap())
    val plans: StateFlow<Map<ProTier, ProPlan>> = _plans.asStateFlow()

    private val _connectionState = MutableStateFlow(BillingConnectionState.CONNECTING)
    val connectionState: StateFlow<BillingConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<BillingEvent> = _events.asSharedFlow()

    /**
     * True once both product queries have come back, whatever they returned.
     * Without this the paywall cannot tell "still loading" from "connected, but
     * these products do not exist in the Play Console" and would show skeletons
     * for ever.
     */
    private val _catalogueLoaded = MutableStateFlow(false)
    val catalogueLoaded: StateFlow<Boolean> = _catalogueLoaded.asStateFlow()

    // Set from the two product-query callbacks and read by both, so the writes
    // have to be visible across threads or the catalogue can stay "loading".
    @Volatile private var subsQueryDone = false
    @Volatile private var inappQueryDone = false

    // Written from both product-query callbacks, which Play may deliver on
    // different threads.
    private val productDetailsCache = ConcurrentHashMap<String, ProductDetails>()
    private var reconnectDelayMs = 1_000L
    private val restoreRequested = AtomicBoolean(false)

    /** True from startConnection() until Play answers, so retries do not overlap. */
    private val connecting = AtomicBoolean(false)

    // ---------------------------------------------------------------- lifecycle

    fun startConnection() {
        if (billingClient.isReady) {
            queryExistingPurchases()
            return
        }
        // `isReady` is false for the whole handshake, so guarding on it alone let
        // init, onResume and the reconnect timer all call startConnection() while
        // one was already in flight. Play answers the extras with DEVELOPER_ERROR,
        // and the old code treated that as a real failure: it flipped the state to
        // UNAVAILABLE and set catalogueLoaded, so the paywall could show its
        // "billing unavailable" copy while a perfectly good connection was still
        // being established.
        if (!connecting.compareAndSet(false, true)) return
        _connectionState.value = BillingConnectionState.CONNECTING
        runCatching { billingClient.startConnection(this) }
            .onFailure {
                Log.e(TAG, "startConnection threw", it)
                connecting.set(false)
                _connectionState.value = BillingConnectionState.UNAVAILABLE
            }
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        connecting.set(false)
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            reconnectDelayMs = 1_000L
            _connectionState.value = BillingConnectionState.READY
            queryAvailableProducts()
            queryExistingPurchases()
        } else {
            Log.e(TAG, "Billing setup failed (${billingResult.responseCode}): ${billingResult.debugMessage}")
            _connectionState.value = BillingConnectionState.UNAVAILABLE
            // Nothing more is coming, so let the paywall leave its loading state.
            _catalogueLoaded.value = true
            // BILLING_UNAVAILABLE means no Play Store / unsupported country: do not
            // hammer the service, but a transient SERVICE_* error is worth retrying.
            if (billingResult.responseCode != BillingClient.BillingResponseCode.BILLING_UNAVAILABLE) {
                scheduleReconnect()
            }
        }
    }

    override fun onBillingServiceDisconnected() {
        // enableAutoServiceReconnection() means the library is already retrying;
        // calling startConnection() here as well would fight it. Reflect the
        // state for the paywall and let it reconnect.
        Log.w(TAG, "Billing service disconnected; awaiting automatic reconnection")
        connecting.set(false)
        _connectionState.value = BillingConnectionState.CONNECTING
    }

    /**
     * Retries a handshake that Play answered with a transient error. Automatic
     * reconnection only covers a connection that was established and then
     * dropped, so without this a user who opened the paywall during a blip
     * would sit on "unavailable" until they navigated away and back.
     */
    private fun scheduleReconnect() {
        val delayMs = reconnectDelayMs
        reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
        scope.launch {
            delay(delayMs)
            // Route through startConnection so the in-flight guard applies here too.
            if (!billingClient.isReady) startConnection()
        }
    }

    fun endConnection() {
        if (billingClient.isReady) billingClient.endConnection()
    }

    /** Call from Activity.onResume: catches purchases made outside the app. */
    fun refresh() {
        if (billingClient.isReady) {
            if (_plans.value.isEmpty()) queryAvailableProducts()
            queryExistingPurchases()
        } else {
            startConnection()
        }
    }

    // ---------------------------------------------------------------- catalogue

    private fun queryAvailableProducts() {
        subsQueryDone = false
        inappQueryDone = false
        _catalogueLoaded.value = false

        val subs = QueryProductDetailsParams.newBuilder()
            .setProductList(
                SUBSCRIPTION_SKUS.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                }
            ).build()

        val inapp = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(SKU_LIFETIME)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            ).build()

        billingClient.queryProductDetailsAsync(subs) { result, queryResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                logUnfetched(queryResult)
                handleDetails(queryResult.productDetailsList)
            } else {
                Log.e(TAG, "Subscription products unavailable: ${result.debugMessage}")
                _events.tryEmit(BillingEvent.PlansUnavailable)
            }
            subsQueryDone = true
            markCatalogueLoadedIfComplete()
        }

        billingClient.queryProductDetailsAsync(inapp) { result, queryResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                logUnfetched(queryResult)
                handleDetails(queryResult.productDetailsList)
            } else {
                Log.e(TAG, "One-time product unavailable: ${result.debugMessage}")
            }
            inappQueryDone = true
            markCatalogueLoadedIfComplete()
        }
    }

    /**
     * Billing 8 changed an OK response from "here are the products we found" to
     * "here are the products we found, and here is why the rest are missing".
     * Before that a mis-configured product was indistinguishable from one that
     * does not exist, which is how the Lifetime tier once went missing from the
     * paywall with nothing in the log to say why.
     */
    private fun logUnfetched(result: QueryProductDetailsResult) {
        result.unfetchedProductList.forEach {
            Log.e(TAG, "Product not fetched: ${it.productId} (${it.productType}) status=${it.statusCode}")
        }
    }

    private fun markCatalogueLoadedIfComplete() {
        if (subsQueryDone && inappQueryDone) _catalogueLoaded.value = true
    }

    private fun mergePlans(newPlans: List<ProPlan>) {
        if (newPlans.isEmpty()) return
        // The SUBS and INAPP queries are issued together and Play delivers their
        // callbacks independently, so `_plans.value = _plans.value + ...` was a
        // read-modify-write two threads could interleave: whichever wrote last
        // with a stale read silently dropped the other's tier. That is exactly
        // how the Lifetime plan went missing from the paywall while the log
        // showed Play had returned it. `update` retries on conflict.
        _plans.update { current -> current + newPlans.associateBy { it.tier } }
    }

    private fun ProductDetails.toProPlan(): ProPlan? {
        val tier = tierFor(productId) ?: return null
        return when (productType) {
            BillingClient.ProductType.SUBS -> {
                // Prefer the offer that includes a free trial, then the cheapest
                // recurring phase. Play returns base plans and offers together.
                val offers = subscriptionOfferDetails.orEmpty()
                if (offers.isEmpty()) return null
                val bestOffer = offers.firstOrNull { it.freeTrialDays() > 0 } ?: offers.first()
                val recurringPhase = bestOffer.pricingPhases.pricingPhaseList
                    .lastOrNull { it.priceAmountMicros > 0 }
                    ?: bestOffer.pricingPhases.pricingPhaseList.last()
                ProPlan(
                    productId = productId,
                    tier = tier,
                    formattedPrice = recurringPhase.formattedPrice,
                    priceAmountMicros = recurringPhase.priceAmountMicros,
                    priceCurrencyCode = recurringPhase.priceCurrencyCode,
                    offerToken = bestOffer.offerToken,
                    freeTrialDays = bestOffer.freeTrialDays()
                )
            }
            else -> {
                // A one-time product configured with Play's "purchase options"
                // model exposes its price through the offer *list*; the singular
                // legacy field is only populated for products that predate it.
                // Reading the legacy field alone drops such a product silently.
                // Lifetime has one purchase option today - take the cheapest if
                // that ever changes, matching how the SUBS branch picks an offer.
                val offer = oneTimePurchaseOfferDetailsList
                    .orEmpty()
                    .minByOrNull { it.priceAmountMicros }
                    ?: oneTimePurchaseOfferDetails
                    ?: return null
                ProPlan(
                    productId = productId,
                    tier = tier,
                    formattedPrice = offer.formattedPrice,
                    priceAmountMicros = offer.priceAmountMicros,
                    priceCurrencyCode = offer.priceCurrencyCode,
                    // Purchase options are chosen by token, exactly like a
                    // subscription offer. Legacy products have none.
                    offerToken = offer.offerToken?.takeIf { it.isNotBlank() },
                    freeTrialDays = 0
                )
            }
        }
    }

    /** Length of the leading zero-cost pricing phase, in days. 0 when there is none. */
    private fun ProductDetails.SubscriptionOfferDetails.freeTrialDays(): Int {
        val freePhase = pricingPhases.pricingPhaseList.firstOrNull { it.priceAmountMicros == 0L } ?: return 0
        return parseIso8601PeriodDays(freePhase.billingPeriod)
    }

    // ---------------------------------------------------------------- purchase

    fun launchPurchaseFlow(activity: Activity, tier: ProTier) {
        val plan = _plans.value[tier]
        if (plan == null) {
            // No local unlock fallback: granting Pro without a purchase would give
            // the entitlement away for free to anyone who opens the paywall offline.
            Log.w(TAG, "No product details for $tier; cannot start purchase")
            _events.tryEmit(BillingEvent.PlansUnavailable)
            if (!billingClient.isReady) startConnection() else queryAvailableProducts()
            return
        }

        val productDetails = productDetailsCache[plan.productId]
        if (productDetails == null) {
            _events.tryEmit(BillingEvent.PlansUnavailable)
            queryAvailableProducts()
            return
        }

        val paramsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
        plan.offerToken?.let { paramsBuilder.setOfferToken(it) }

        val flowBuilder = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(paramsBuilder.build()))

        // Ties the purchase to the local account so Play can flag abuse and so
        // the backend can reconcile a purchase token to a user.
        authRepository.obfuscatedAccountId()?.let { flowBuilder.setObfuscatedAccountId(it) }

        val result = billingClient.launchBillingFlow(activity, flowBuilder.build())
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.e(TAG, "launchBillingFlow failed: ${result.debugMessage}")
            _events.tryEmit(BillingEvent.PurchaseFailed(result.debugMessage))
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val handled = purchases.orEmpty().map { handlePurchase(it) }
                when {
                    handled.any { it == PurchaseOutcome.PURCHASED } ->
                        _events.tryEmit(BillingEvent.PurchaseSuccess)
                    handled.any { it == PurchaseOutcome.PENDING } ->
                        _events.tryEmit(BillingEvent.PurchasePending)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED ->
                _events.tryEmit(BillingEvent.PurchaseCancelled)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // Happens when a purchase exists but was never acknowledged.
                queryExistingPurchases()
            }
            else -> {
                Log.e(TAG, "Purchase update error: ${billingResult.debugMessage}")
                _events.tryEmit(BillingEvent.PurchaseFailed(billingResult.debugMessage))
            }
        }
    }

    private enum class PurchaseOutcome { PURCHASED, PENDING, IGNORED }

    private fun handlePurchase(purchase: Purchase): PurchaseOutcome {
        val tier = purchase.products.mapNotNull { tierFor(it) }.maxByOrNull { it.rank() }
            ?: return PurchaseOutcome.IGNORED

        return when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                authRepository.updateProStatus(true, tier)
                if (!purchase.isAcknowledged) {
                    val params = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient.acknowledgePurchase(params) { ack ->
                        if (ack.responseCode != BillingClient.BillingResponseCode.OK) {
                            // Unacknowledged purchases are auto-refunded after 3 days,
                            // so a failure here has to be retried on the next launch.
                            Log.e(TAG, "Acknowledge failed: ${ack.debugMessage}")
                        }
                    }
                }
                // Best-effort server-side record; entitlement is never blocked on it.
                scope.launch {
                    authRepository.reportPurchase(
                        productId = purchase.products.firstOrNull().orEmpty(),
                        purchaseToken = purchase.purchaseToken,
                        orderId = purchase.orderId
                    )
                }
                PurchaseOutcome.PURCHASED
            }
            Purchase.PurchaseState.PENDING -> PurchaseOutcome.PENDING
            else -> PurchaseOutcome.IGNORED
        }
    }

    // ---------------------------------------------------------------- restore

    fun restorePurchases() {
        restoreRequested.set(true)
        if (billingClient.isReady) queryExistingPurchases() else startConnection()
    }

    fun queryExistingPurchases() {
        var subsDone = false
        var inappDone = false
        var subsOk = false
        var inappOk = false
        val owned = mutableListOf<Purchase>()

        fun finishIfComplete() {
            if (!(subsDone && inappDone)) return
            val activeTier = owned
                .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .flatMap { it.products }
                .mapNotNull { tierFor(it) }
                .maxByOrNull { it.rank() }

            if (activeTier != null) {
                owned.forEach { handlePurchase(it) }
                if (restoreRequested.getAndSet(false)) {
                    _events.tryEmit(BillingEvent.RestoredPro)
                }
            } else if (subsOk && inappOk) {
                // Both queries succeeded and returned nothing: the entitlement is
                // genuinely gone (expired, refunded, different account). Only revoke
                // on a clean result so a network blip cannot lock a paying user out.
                if (authRepository.isPro.value) {
                    Log.i(TAG, "No active purchase found - revoking cached Pro entitlement")
                    authRepository.updateProStatus(false, ProTier.FREE)
                }
                if (restoreRequested.getAndSet(false)) {
                    _events.tryEmit(BillingEvent.RestoreNothingFound)
                }
            } else {
                restoreRequested.set(false)
            }
        }

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { result, purchases ->
            subsOk = result.responseCode == BillingClient.BillingResponseCode.OK
            if (subsOk) owned += purchases
            subsDone = true
            finishIfComplete()
        }

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { result, purchases ->
            inappOk = result.responseCode == BillingClient.BillingResponseCode.OK
            if (inappOk) owned += purchases
            inappDone = true
            finishIfComplete()
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun tierFor(productId: String): ProTier? = when (productId) {
        SKU_MONTHLY -> ProTier.MONTHLY
        SKU_ANNUAL -> ProTier.ANNUAL
        SKU_LIFETIME -> ProTier.LIFETIME
        else -> null
    }

    private fun ProTier.rank(): Int = when (this) {
        ProTier.FREE -> 0
        ProTier.MONTHLY -> 1
        ProTier.ANNUAL -> 2
        ProTier.LIFETIME -> 3
    }

    /** Deep-link to the Play subscription centre for an existing subscriber. */
    fun manageSubscriptionUrl(productId: String?): String {
        val pkg = context.packageName
        return if (productId.isNullOrBlank()) {
            "https://play.google.com/store/account/subscriptions"
        } else {
            "https://play.google.com/store/account/subscriptions?sku=$productId&package=$pkg"
        }
    }

    /**
     * Keeps the raw [ProductDetails] alongside the UI-facing [ProPlan] so the
     * purchase flow can hand Play back the exact object it gave us.
     */
    private fun handleDetails(details: List<ProductDetails>) {
        details.forEach { productDetailsCache[it.productId] = it }
        mergePlans(details.mapNotNull { it.toProPlan() })
    }
}

/**
 * Parses the subset of ISO-8601 durations Play uses for billing periods
 * ("P1W", "P3D", "P1M", "P1Y") into whole days.
 */
internal fun parseIso8601PeriodDays(period: String): Int {
    val match = Regex("""^P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)W)?(?:(\d+)D)?$""").find(period) ?: return 0
    val (y, m, w, d) = match.destructured
    return (y.toIntOrNull() ?: 0) * 365 +
        (m.toIntOrNull() ?: 0) * 30 +
        (w.toIntOrNull() ?: 0) * 7 +
        (d.toIntOrNull() ?: 0)
}
