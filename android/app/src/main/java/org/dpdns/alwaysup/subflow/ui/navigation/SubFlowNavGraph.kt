package org.dpdns.alwaysup.subflow.ui.navigation

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import org.dpdns.alwaysup.subflow.R
import org.dpdns.alwaysup.subflow.data.billing.BillingEvent
import org.dpdns.alwaysup.subflow.data.billing.BillingManager
import org.dpdns.alwaysup.subflow.data.preferences.PreferencesManager
import org.dpdns.alwaysup.subflow.data.repository.AuthRepository
import org.dpdns.alwaysup.subflow.data.repository.QuotaReachedException
import org.dpdns.alwaysup.subflow.data.repository.SignInCancelledException
import org.dpdns.alwaysup.subflow.data.repository.SignInNotConfiguredException
import org.dpdns.alwaysup.subflow.data.repository.SubscriptionRepository
import org.dpdns.alwaysup.subflow.domain.model.PresetService
import org.dpdns.alwaysup.subflow.domain.model.ProTier
import org.dpdns.alwaysup.subflow.domain.util.ExportUtils
import org.dpdns.alwaysup.subflow.ui.screens.add.AddSubscriptionScreen
import org.dpdns.alwaysup.subflow.ui.screens.analytics.AnalyticsScreen
import org.dpdns.alwaysup.subflow.ui.screens.dashboard.DashboardScreen
import org.dpdns.alwaysup.subflow.ui.screens.detail.SubscriptionDetailScreen
import org.dpdns.alwaysup.subflow.ui.screens.onboarding.OnboardingScreen
import org.dpdns.alwaysup.subflow.ui.screens.paywall.PaywallScreen
import org.dpdns.alwaysup.subflow.ui.screens.settings.SettingsScreen
import org.dpdns.alwaysup.subflow.ui.components.SubFlowSnackbarHost
import org.dpdns.alwaysup.subflow.ui.util.rememberHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Dashboard : Screen("dashboard")
    data object Analytics : Screen("analytics")
    data object Settings : Screen("settings")
    data object AddSubscription : Screen("add_subscription")
    data object SubscriptionDetail : Screen("subscription_detail/{subId}") {
        fun createRoute(subId: String) = "subscription_detail/$subId"
    }
    data object EditSubscription : Screen("edit_subscription/{subId}") {
        fun createRoute(subId: String) = "edit_subscription/$subId"
    }
    data object Paywall : Screen("paywall")
}

@Composable
fun SubFlowNavHost(
    navController: NavHostController,
    subscriptionRepository: SubscriptionRepository,
    authRepository: AuthRepository,
    preferencesManager: PreferencesManager,
    billingManager: BillingManager,
    adsConsentManager: org.dpdns.alwaysup.subflow.data.ads.AdsConsentManager,
    pendingSubscriptionId: String? = null,
    onPendingSubscriptionHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val snackbarHostState = remember { SnackbarHostState() }

    // `null` until Room emits, so the dashboard can tell "loading" from "empty".
    val subscriptionsOrNull by subscriptionRepository.activeSubscriptions
        .collectAsState(initial = null)
    val subscriptions = subscriptionsOrNull.orEmpty()
    val isLoading = subscriptionsOrNull == null

    val isPro by authRepository.isPro.collectAsState()
    val proTier by authRepository.proTier.collectAsState()
    val currentUser by authRepository.currentUser.collectAsState()
    val primaryCurrency by preferencesManager.currency.collectAsState()
    val onboardingComplete by preferencesManager.onboardingComplete.collectAsState()
    val plans by billingManager.plans.collectAsState()
    val billingConnection by billingManager.connectionState.collectAsState()
    val catalogueLoaded by billingManager.catalogueLoaded.collectAsState()

    var presets by remember { mutableStateOf<List<PresetService>>(emptyList()) }

    LaunchedEffect(Unit) {
        if (currentUser == null) authRepository.signInAsGuest()
        presets = subscriptionRepository.fetchPresets()
        subscriptionRepository.rollForwardDueRenewals()
        subscriptionRepository.refreshExchangeRates()
    }

    // Deep link from a renewal notification.
    LaunchedEffect(pendingSubscriptionId) {
        val id = pendingSubscriptionId ?: return@LaunchedEffect
        navController.navigate(Screen.SubscriptionDetail.createRoute(id))
        onPendingSubscriptionHandled()
    }

    // Billing outcomes become snackbars rather than silent log lines.
    val messages = billingMessages()
    LaunchedEffect(Unit) {
        billingManager.events.collectLatest { event ->
            val message = when (event) {
                BillingEvent.PurchaseSuccess -> messages.purchaseSuccess
                BillingEvent.PurchaseCancelled -> messages.purchaseCancelled
                BillingEvent.PurchasePending -> messages.purchasePending
                BillingEvent.RestoredPro -> messages.restoreSuccess
                BillingEvent.RestoreNothingFound -> messages.restoreNothing
                BillingEvent.PlansUnavailable -> messages.plansUnavailable
                is BillingEvent.PurchaseFailed -> messages.purchaseFailed
            }
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
            if (event == BillingEvent.PurchaseSuccess) {
                navController.popBackStack(Screen.Paywall.route, inclusive = true)
            }
        }
    }

    /**
     * Opens a link, preferring the app that owns it and falling back to an
     * in-app Custom Tab rather than the browser disambiguation dialog.
     *
     * A bare ACTION_VIEW made the system ask "open with?" every time, because
     * our own privacy and terms pages are claimed by no app and every installed
     * browser matches. androidx.browser is already in the graph via the ads SDK,
     * so the tab costs nothing in bundle size.
     */
    fun openUrl(url: String) {
        val uri = Uri.parse(url)

        // Play links belong to the Play Store app on every API level, and its
        // web fallback is strictly worse (manage-subscription and rate-app both
        // land here). Never wrap these.
        if (uri.host?.endsWith("play.google.com") == true) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            return
        }

        // REQUIRE_NON_BROWSER throws instead of quietly landing in a browser,
        // which is what separates "Netflix owns netflix.com" from "only
        // browsers can open this". API 30+; below that we go straight to a tab.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, uri)
                        .addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
                )
                return
            } catch (_: ActivityNotFoundException) {
                // Nothing but browsers claim it - fall through to the tab.
            }
        }

        runCatching {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(context, uri)
        }.onFailure {
            // No Custom Tabs provider on the device at all.
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        }
    }

    fun showMessage(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val bottomBarRoutes = listOf(Screen.Dashboard.route, Screen.Analytics.route, Screen.Settings.route)
    val showBottomBar = currentRoute in bottomBarRoutes

    Scaffold(
        snackbarHost = { SubFlowSnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            // Slides away on full-screen routes instead of popping out of existence.
            androidx.compose.animation.AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        haptics.tick()
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (onboardingComplete) Screen.Dashboard.route else Screen.Onboarding.route,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
                ) + fadeIn(tween(240))
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
                ) + fadeOut(tween(200))
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
                ) + fadeIn(tween(240))
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
                ) + fadeOut(tween(200))
            }
        ) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onFinished = {
                        preferencesManager.completeOnboarding()
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    },
                    onSeePro = {
                        preferencesManager.completeOnboarding()
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                        navController.navigate(Screen.Paywall.route)
                    }
                )
            }

            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    subscriptions = subscriptions,
                    isPro = isPro,
                    isLoading = isLoading,
                    primaryCurrency = primaryCurrency,
                    onAddClick = {
                        // Intercept the quota before the form is even opened: a
                        // paywall after 60 seconds of typing feels like a trap.
                        if (!isPro && subscriptions.count { it.isActive && !it.isDeleted } >=
                            SubscriptionRepository.FREE_TIER_LIMIT
                        ) {
                            navController.navigate(Screen.Paywall.route)
                        } else {
                            navController.navigate(Screen.AddSubscription.route)
                        }
                    },
                    onSubscriptionClick = { id ->
                        navController.navigate(Screen.SubscriptionDetail.createRoute(id))
                    },
                    onDeleteSubscription = { id ->
                        scope.launch { subscriptionRepository.deleteSubscription(id) }
                    },
                    onRestoreSubscription = { id ->
                        scope.launch { subscriptionRepository.restoreSubscription(id) }
                    },
                    onPaywallClick = { navController.navigate(Screen.Paywall.route) }
                )
            }

            composable(Screen.Analytics.route) {
                val exportReady = stringResource(R.string.export_ready)
                val exportFailed = stringResource(R.string.export_failed)
                AnalyticsScreen(
                    subscriptions = subscriptions,
                    isPro = isPro,
                    primaryCurrency = primaryCurrency,
                    onPaywallClick = { navController.navigate(Screen.Paywall.route) },
                    onExportCsv = {
                        val active = subscriptions.filter { it.isActive && !it.isDeleted }
                        val intent = ExportUtils.shareIntent(
                            context = context,
                            fileName = ExportUtils.timestampedName("subflow-report", "csv"),
                            content = ExportUtils.buildCsv(active, primaryCurrency),
                            mimeType = "text/csv",
                            subject = "SubFlow report"
                        )
                        if (intent != null) {
                            showMessage(exportReady)
                            runCatching { context.startActivity(Intent.createChooser(intent, null)) }
                        } else {
                            showMessage(exportFailed)
                        }
                    }
                )
            }

            composable(Screen.Settings.route) {
                val syncOffline = stringResource(R.string.sync_offline)
                val signInUnavailable = stringResource(R.string.sign_in_unavailable)
                val signInCancelled = stringResource(R.string.sign_in_cancelled)
                val signInFailed = stringResource(R.string.sign_in_failed)
                val restoreFailed = stringResource(R.string.restore_failed)
                val restoreReadFailed = stringResource(R.string.restore_read_failed)
                val resetDone = stringResource(R.string.reset_done)
                val testSent = stringResource(R.string.test_notification_sent)
                val exportFailed = stringResource(R.string.export_failed)
                val shareText = stringResource(R.string.share_app_text, playStoreUrl(context))

                SettingsScreen(
                    user = currentUser,
                    isPro = isPro,
                    proTier = proTier,
                    preferencesManager = preferencesManager,
                    isGoogleSignInAvailable = authRepository.isGoogleSignInAvailable,
                    isPrivacyOptionsRequired = adsConsentManager.isPrivacyOptionsRequired,
                    onSignInClick = {
                        scope.launch {
                            if (activity == null) return@launch
                            val result = authRepository.signInWithGoogle(activity)
                            result.onFailure { error ->
                                showMessage(
                                    when (error) {
                                        is SignInNotConfiguredException -> signInUnavailable
                                        is SignInCancelledException -> signInCancelled
                                        else -> signInFailed
                                    }
                                )
                            }
                        }
                    },
                    onSignOutClick = { authRepository.signOut() },
                    onSyncClick = {
                        scope.launch {
                            val res = subscriptionRepository.syncWithServer(authRepository.authToken.value)
                            showMessage(
                                res.fold(
                                    onSuccess = { count -> context.getString(R.string.sync_complete, count) },
                                    onFailure = { syncOffline }
                                )
                            )
                        }
                    },
                    onPaywallClick = { navController.navigate(Screen.Paywall.route) },
                    onManageSubscription = {
                        openUrl(billingManager.manageSubscriptionUrl(skuFor(proTier)))
                    },
                    onRestorePurchases = { billingManager.restorePurchases() },
                    onPrivacyOptionsClick = {
                        activity?.let { adsConsentManager.showPrivacyOptions(it) }
                    },
                    onBackupClick = {
                        scope.launch {
                            val json = subscriptionRepository.exportBackupJson()
                            val intent = ExportUtils.shareIntent(
                                context = context,
                                fileName = ExportUtils.timestampedName("subflow-backup", "json"),
                                content = json,
                                mimeType = "application/json",
                                subject = "SubFlow backup"
                            )
                            if (intent != null) {
                                runCatching { context.startActivity(Intent.createChooser(intent, null)) }
                            } else {
                                showMessage(exportFailed)
                            }
                        }
                    },
                    onRestoreFromFile = { uri ->
                        scope.launch {
                            val json = withContext(Dispatchers.IO) {
                                runCatching {
                                    context.contentResolver.openInputStream(uri)
                                        ?.bufferedReader()
                                        ?.use { it.readText() }
                                }.getOrNull()
                            }
                            if (json.isNullOrBlank()) {
                                showMessage(restoreReadFailed)
                                return@launch
                            }
                            val res = subscriptionRepository.restoreBackupJson(json)
                            showMessage(
                                res.fold(
                                    onSuccess = { n -> context.getString(R.string.restore_success_count, n) },
                                    onFailure = { restoreFailed }
                                )
                            )
                        }
                    },
                    onClearAllData = {
                        scope.launch {
                            subscriptionRepository.clearAllData()
                            showMessage(resetDone)
                        }
                    },
                    onTestNotification = {
                        org.dpdns.alwaysup.subflow.data.notifications.RenewalNotificationWorker
                            .sendTestNotification(context)
                        showMessage(testSent)
                    },
                    onOpenUrl = ::openUrl,
                    onShareApp = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        runCatching { context.startActivity(Intent.createChooser(send, null)) }
                    },
                    onRateApp = { openUrl(playStoreUrl(context)) }
                )
            }

            composable(Screen.AddSubscription.route) {
                AddSubscriptionScreen(
                    presets = presets,
                    primaryCurrency = primaryCurrency,
                    isPro = isPro,
                    onSaveSubscription = { newSub ->
                        scope.launch {
                            val res = subscriptionRepository.saveSubscription(newSub, isPro)
                            if (res.isSuccess) {
                                navController.popBackStack()
                                showMessage(context.getString(R.string.saved_subscription, newSub.name))
                            } else if (res.exceptionOrNull() is QuotaReachedException) {
                                navController.popBackStack()
                                navController.navigate(Screen.Paywall.route)
                            }
                        }
                    },
                    onCancel = { navController.popBackStack() },
                    onUpgradeClick = { navController.navigate(Screen.Paywall.route) }
                )
            }

            composable(
                route = Screen.SubscriptionDetail.route,
                arguments = listOf(navArgument("subId") { type = NavType.StringType })
            ) { backStack ->
                val subId = backStack.arguments?.getString("subId").orEmpty()
                SubscriptionDetailScreen(
                    subscription = subscriptions.find { it.id == subId },
                    onBack = { navController.popBackStack() },
                    onEdit = { s -> navController.navigate(Screen.EditSubscription.createRoute(s.id)) },
                    onDelete = { id ->
                        scope.launch {
                            subscriptionRepository.deleteSubscription(id)
                            navController.popBackStack()
                        }
                    },
                    onOpenUrl = ::openUrl
                )
            }

            composable(
                route = Screen.EditSubscription.route,
                arguments = listOf(navArgument("subId") { type = NavType.StringType })
            ) { backStack ->
                val subId = backStack.arguments?.getString("subId").orEmpty()
                val sub = subscriptions.find { it.id == subId }
                if (sub == null) {
                    LaunchedEffect(subId) { navController.popBackStack() }
                } else {
                    AddSubscriptionScreen(
                        presets = presets,
                        primaryCurrency = primaryCurrency,
                        isPro = isPro,
                        existingSubscription = sub,
                        onSaveSubscription = { updatedSub ->
                            scope.launch {
                                subscriptionRepository.saveSubscription(updatedSub, isPro)
                                navController.popBackStack()
                                showMessage(context.getString(R.string.saved_subscription, updatedSub.name))
                            }
                        },
                        onCancel = { navController.popBackStack() },
                        onUpgradeClick = { navController.navigate(Screen.Paywall.route) }
                    )
                }
            }

            composable(
                route = Screen.Paywall.route,
                // The paywall is a modal: it rises from the bottom, it does not
                // slide in like a sibling page.
                enterTransition = { slideInVertically { it } + fadeIn(tween(200)) },
                exitTransition = { fadeOut(tween(150)) },
                popEnterTransition = { fadeIn(tween(150)) },
                popExitTransition = { slideOutVertically { it } + fadeOut(tween(200)) }
            ) {
                PaywallScreen(
                    plans = plans,
                    connectionState = billingConnection,
                    catalogueLoaded = catalogueLoaded,
                    isPro = isPro,
                    proTier = proTier,
                    onDismiss = { navController.popBackStack() },
                    onPurchaseTier = { tier ->
                        activity?.let { billingManager.launchPurchaseFlow(it, tier) }
                    },
                    onRestorePurchases = { billingManager.restorePurchases() },
                    onManageSubscription = {
                        openUrl(billingManager.manageSubscriptionUrl(skuFor(proTier)))
                    },
                    onRetry = { billingManager.refresh() },
                    onOpenUrl = ::openUrl
                )
            }
        }
    }
}

@Composable
private fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    // A Surface `border` strokes all four edges, so the bar was outlined down
    // its sides and along the bottom, hugging the screen edges. A tab bar needs
    // exactly one separator: the hairline along its top.
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp
    ) {
        Column {
            HorizontalDivider(
                thickness = Dp.Hairline,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(60.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val navItems = listOf(
                    Triple(Screen.Dashboard.route, stringResource(R.string.nav_subscriptions), Icons.AutoMirrored.Filled.List),
                    Triple(Screen.Analytics.route, stringResource(R.string.nav_analytics), Icons.Default.Insights),
                    Triple(Screen.Settings.route, stringResource(R.string.nav_settings), Icons.Default.Settings)
                )

                navItems.forEach { (route, title, icon) ->
                    NavItem(
                        title = title,
                        icon = icon,
                        isSelected = currentRoute == route,
                        onClick = { onNavigate(route) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "navIconScale"
    )
    val tint = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .semantics {
                contentDescription = title
                role = Role.Tab
                selected = isSelected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .scale(scale),
            tint = tint
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = title, fontSize = 11.sp, color = tint, maxLines = 1)
    }
}

private data class BillingMessages(
    val purchaseSuccess: String,
    val purchaseCancelled: String,
    val purchasePending: String,
    val purchaseFailed: String,
    val restoreSuccess: String,
    val restoreNothing: String,
    val plansUnavailable: String
)

@Composable
private fun billingMessages() = BillingMessages(
    purchaseSuccess = stringResource(R.string.purchase_success),
    purchaseCancelled = stringResource(R.string.purchase_cancelled),
    purchasePending = stringResource(R.string.purchase_pending),
    purchaseFailed = stringResource(R.string.purchase_failed),
    restoreSuccess = stringResource(R.string.restore_success),
    restoreNothing = stringResource(R.string.restore_none_found),
    plansUnavailable = stringResource(R.string.plans_unavailable)
)

private fun skuFor(tier: ProTier): String? = when (tier) {
    ProTier.MONTHLY -> BillingManager.SKU_MONTHLY
    ProTier.ANNUAL -> BillingManager.SKU_ANNUAL
    else -> null
}

private fun playStoreUrl(context: Context): String =
    "https://play.google.com/store/apps/details?id=${context.packageName.removeSuffix(".debug")}"
