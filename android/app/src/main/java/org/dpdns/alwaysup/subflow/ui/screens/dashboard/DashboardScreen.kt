package org.dpdns.alwaysup.subflow.ui.screens.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dpdns.alwaysup.subflow.R
import org.dpdns.alwaysup.subflow.data.repository.SubscriptionRepository
import org.dpdns.alwaysup.subflow.domain.model.BillingCycle
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.util.CurrencyConverter
import org.dpdns.alwaysup.subflow.domain.util.DateCalculators
import org.dpdns.alwaysup.subflow.ui.components.*
import org.dpdns.alwaysup.subflow.ui.theme.*
import org.dpdns.alwaysup.subflow.ui.util.rememberHaptics
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class DashboardSortOrder(val stringResId: Int) {
    RENEWAL_DATE(R.string.sort_renewal_date),
    PRICE_HIGH(R.string.sort_price_high),
    PRICE_LOW(R.string.sort_price_low),
    NAME_ASC(R.string.sort_name_asc)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    subscriptions: List<Subscription>,
    isPro: Boolean,
    isLoading: Boolean,
    primaryCurrency: String = "USD",
    onAddClick: () -> Unit,
    onSubscriptionClick: (String) -> Unit,
    onDeleteSubscription: (String) -> Unit,
    onRestoreSubscription: (String) -> Unit = {},
    onPaywallClick: () -> Unit
) {
    val haptics = rememberHaptics()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val undoText = stringResource(R.string.undo)
    val searchFocus = remember { FocusRequester() }

    var selectedCategory by rememberSaveable { mutableStateOf("All") }
    var isAnnualView by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortOrder by rememberSaveable { mutableStateOf(DashboardSortOrder.RENEWAL_DATE) }
    var showSortSheet by remember { mutableStateOf(false) }

    val categories = listOf(
        "All" to R.string.filter_all,
        "Streaming" to R.string.filter_streaming,
        "Productivity" to R.string.filter_productivity,
        "Cloud" to R.string.filter_cloud,
        "Utilities" to R.string.filter_utilities,
        "Health" to R.string.filter_health
    )

    val activeSubs = remember(subscriptions) { subscriptions.filter { it.isActive && !it.isDeleted } }
    val totalMonthly = remember(activeSubs, primaryCurrency) {
        activeSubs.sumOf { CurrencyConverter.convert(it.monthlyAmount, it.currency, primaryCurrency) }
    }
    val totalYearly = totalMonthly * 12.0

    val filteredSubs = remember(activeSubs, selectedCategory, searchQuery, sortOrder, primaryCurrency) {
        activeSubs
            .filter { sub ->
                val matchCat = selectedCategory == "All" ||
                    sub.category.equals(selectedCategory, ignoreCase = true) ||
                    // "Entertainment" is the legacy label for what is now "Streaming".
                    (selectedCategory == "Streaming" && sub.category.equals("Entertainment", true))
                val q = searchQuery.trim()
                val matchQuery = q.isBlank() ||
                    sub.name.contains(q, ignoreCase = true) ||
                    sub.category.contains(q, ignoreCase = true) ||
                    sub.notes.contains(q, ignoreCase = true)
                matchCat && matchQuery
            }
            .let { list ->
                when (sortOrder) {
                    DashboardSortOrder.RENEWAL_DATE -> list.sortedBy { DateCalculators.calculateDaysUntil(it.nextBillDate) }
                    DashboardSortOrder.PRICE_HIGH -> list.sortedByDescending {
                        CurrencyConverter.convert(it.monthlyAmount, it.currency, primaryCurrency)
                    }
                    DashboardSortOrder.PRICE_LOW -> list.sortedBy {
                        CurrencyConverter.convert(it.monthlyAmount, it.currency, primaryCurrency)
                    }
                    DashboardSortOrder.NAME_ASC -> list.sortedBy { it.name.lowercase() }
                }
            }
    }

    val nextRenewalSub = activeSubs.minByOrNull { DateCalculators.calculateDaysUntil(it.nextBillDate) }
    val nextRenewalDays = nextRenewalSub?.let { DateCalculators.calculateDaysUntil(it.nextBillDate) }

    val currentConfig = LocalConfiguration.current
    val todayFormatted = remember(currentConfig) {
        val currentLocale = currentConfig.locales.get(0) ?: Locale.getDefault()
        // A fixed "EEEE, MMM d" pattern produces "星期五, 8月 28" in Chinese and
        // similar oddities elsewhere. An ICU skeleton lets each locale choose
        // its own field order and separators.
        runCatching {
            val icuFormat = android.icu.text.DateFormat.getInstanceForSkeleton("MMMEd", currentLocale)
            icuFormat.format(java.util.Date())
        }.getOrElse {
            LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM d", currentLocale))
        }.uppercase(currentLocale)
    }

    // Large title collapses into a compact centered bar once the hero scrolls away.
    val collapseThresholdPx = with(density) { 56.dp.toPx() }
    val isCollapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > collapseThresholdPx
        }
    }

    Scaffold(
        snackbarHost = {
            SubFlowSnackbarHost(hostState = snackbarHostState, modifier = Modifier.padding(bottom = 12.dp))
        },
        bottomBar = { if (!isPro) AdMobAdaptiveBanner() },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "header") {
                    DashboardHeader(
                        dateLabel = todayFormatted,
                        isPro = isPro,
                        onPaywallClick = onPaywallClick,
                        onAddClick = {
                            haptics.tick()
                            onAddClick()
                        }
                    )
                }

                item(key = "hero") {
                    HeroSpendCard(
                        isDark = isDark,
                        isAnnualView = isAnnualView,
                        onToggleView = {
                            haptics.tick()
                            isAnnualView = !isAnnualView
                        },
                        totalMonthly = totalMonthly,
                        totalYearly = totalYearly,
                        primaryCurrency = primaryCurrency,
                        activeCount = activeSubs.size,
                        nextRenewalSub = nextRenewalSub,
                        nextRenewalDays = nextRenewalDays,
                        isLoading = isLoading
                    )
                }

                // Free-tier meter: makes the limit visible before it is hit, which
                // converts far better than only surfacing it as a blocking wall.
                if (!isPro && activeSubs.isNotEmpty()) {
                    item(key = "quota") {
                        QuotaMeter(
                            used = activeSubs.size,
                            limit = SubscriptionRepository.FREE_TIER_LIMIT,
                            onUpgrade = onPaywallClick
                        )
                    }
                }

                if (activeSubs.isNotEmpty() || searchQuery.isNotBlank()) {
                    item(key = "search") {
                        SearchAndSortRow(
                            searchQuery = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onSearchDone = { keyboard?.hide() },
                            focusRequester = searchFocus,
                            sortOrder = sortOrder,
                            onSortClick = { showSortSheet = true }
                        )
                    }

                    item(key = "categories") {
                        CategoryFilterRow(
                            categories = categories,
                            selectedCategory = selectedCategory,
                            onSelect = {
                                haptics.tick()
                                selectedCategory = it
                            }
                        )
                    }
                }

                when {
                    isLoading && activeSubs.isEmpty() -> {
                        items(4, key = { "skeleton_$it" }) { SubscriptionRowSkeleton() }
                    }

                    filteredSubs.isEmpty() -> {
                        item(key = "empty") {
                            EmptyState(
                                query = searchQuery,
                                onAddClick = onAddClick,
                                onClearSearch = { searchQuery = "" }
                            )
                        }
                    }

                    else -> {
                        items(filteredSubs, key = { it.id }) { sub ->
                            SubscriptionRow(
                                sub = sub,
                                onClick = { onSubscriptionClick(sub.id) },
                                onDelete = {
                                    val deletedName = sub.name
                                    val deletedId = sub.id
                                    onDeleteSubscription(deletedId)
                                    scope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        val res = snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.deleted_item, deletedName),
                                            actionLabel = undoText,
                                            duration = SnackbarDuration.Short
                                        )
                                        if (res == SnackbarResult.ActionPerformed) {
                                            onRestoreSubscription(deletedId)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                item(key = "footer_space") { Spacer(modifier = Modifier.height(28.dp)) }
            }

            // Collapsed bar
            AnimatedVisibility(
                visible = isCollapsed,
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut() + slideOutVertically { -it / 2 },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .height(44.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = stringResource(R.string.nav_subscriptions),
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = Dp.Hairline
                        )
                    }
                }
            }
        }
    }

    if (showSortSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp)
            ) {
                Text(
                    text = stringResource(R.string.sort_options),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                DashboardSortOrder.entries.forEach { opt ->
                    val isSelected = opt == sortOrder
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                haptics.tick()
                                sortOrder = opt
                                showSortSheet = false
                            }
                            .heightIn(min = 48.dp)
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(opt.stringResId),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------- pieces

@Composable
private fun DashboardHeader(
    dateLabel: String,
    isPro: Boolean,
    onPaywallClick: () -> Unit,
    onAddClick: () -> Unit
) {
    val upgradeLabel = stringResource(R.string.upgrade_to_pro)
    val addLabel = stringResource(R.string.add_subscription)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.nav_subscriptions),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isPro) {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onPaywallClick)
                        .semantics { contentDescription = upgradeLabel },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 34.dp)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "PRO",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onAddClick
                    )
                    .semantics { contentDescription = addLabel },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun HeroSpendCard(
    isDark: Boolean,
    isAnnualView: Boolean,
    onToggleView: () -> Unit,
    totalMonthly: Double,
    totalYearly: Double,
    primaryCurrency: String,
    activeCount: Int,
    nextRenewalSub: Subscription?,
    nextRenewalDays: Long?,
    isLoading: Boolean
) {
    AppleCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        backgroundBrush = if (isDark) HeroCardDarkGradient else HeroCardLightGradient,
        border = BorderStroke(Dp.Hairline, if (isDark) HairlineDark else HairlineLight)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        if (isAnnualView) R.string.projected_annual_spend else R.string.total_monthly_spend
                    ).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                    ),
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.clickable(onClick = onToggleView)
                ) {
                    Text(
                        text = stringResource(
                            if (isAnnualView) R.string.switch_to_monthly else R.string.switch_to_annual
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isLoading) {
                ShimmerBox(modifier = Modifier.width(180.dp).height(40.dp), cornerRadius = 10.dp)
            } else {
                AnimatedContent(
                    targetState = if (isAnnualView) totalYearly else totalMonthly,
                    transitionSpec = { fadeIn(spring()) togetherWith fadeOut(spring()) },
                    label = "heroAmountAnimation"
                ) { displayAmount ->
                    TabularCurrencyText(
                        amount = displayAmount,
                        currencyCode = primaryCurrency,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-1).sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = Dp.Hairline
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pluralStringResource(R.plurals.active_subscriptions_count, activeCount, activeCount),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (nextRenewalDays != null && nextRenewalDays >= 0 && nextRenewalSub != null) {
                    val isUrgent = nextRenewalDays <= 3
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isUrgent) {
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = if (isUrgent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "${nextRenewalSub.name} · " +
                                    if (nextRenewalDays == 0L) {
                                        stringResource(R.string.renewal_today)
                                    } else {
                                        stringResource(R.string.renewal_days_short, nextRenewalDays.toInt())
                                    },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = if (isUrgent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuotaMeter(used: Int, limit: Int, onUpgrade: () -> Unit) {
    val clamped = used.coerceAtMost(limit)
    val progress by animateFloatAsState(
        targetValue = clamped.toFloat() / limit,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 260f),
        label = "quotaProgress"
    )
    // Two distinct states share the nudge: one slot remaining, and none at all.
    // Telling someone at 5 of 5 that they have "one slot left" is both wrong and
    // weakest exactly where the upgrade matters most.
    val atLimit = used >= limit
    val nearLimit = used >= limit - 1
    val trackColor = if (nearLimit) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

    AppleCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(14.dp),
        onClick = if (nearLimit) onUpgrade else null
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.quota_label),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.6.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.quota_progress, used, limit),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                fontWeight = FontWeight.Bold,
                color = if (nearLimit) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = trackColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {}
        )

        AnimatedVisibility(visible = nearLimit) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(
                        if (atLimit) R.string.quota_nudge_title_full else R.string.quota_nudge_title
                    ),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.quota_nudge_body),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 17.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onUpgrade)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.quota_nudge_cta),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchAndSortRow(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onSearchDone: () -> Unit,
    focusRequester: FocusRequester,
    sortOrder: DashboardSortOrder,
    onSortClick: () -> Unit
) {
    val searchLabel = stringResource(R.string.search_subscriptions)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(42.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearchDone() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .semantics { contentDescription = searchLabel },
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_subscriptions),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        innerTextField()
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.clear_search),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        Surface(
            onClick = onSortClick,
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = stringResource(R.string.sort_options),
                    tint = if (sortOrder != DashboardSortOrder.RENEWAL_DATE) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    categories: List<Pair<String, Int>>,
    selectedCategory: String,
    onSelect: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp)
    ) {
        items(categories, key = { it.first }) { (catKey, catLabelRes) ->
            val isSelected = catKey == selectedCategory
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                label = "catFilterBg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                label = "catFilterText"
            )
            val label = stringResource(catLabelRes)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(backgroundColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(catKey) }
                    .semantics {
                        contentDescription = label
                        selected = isSelected
                    }
                    .heightIn(min = 36.dp)
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 13.sp,
                        color = textColor,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    query: String,
    onAddClick: () -> Unit,
    onClearSearch: () -> Unit
) {
    val searching = query.isNotBlank()
    AppleCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        cornerRadius = 20.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (searching) Icons.Default.Search else Icons.Default.Inbox,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(
                    if (searching) R.string.empty_search_title else R.string.empty_title
                ),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (searching) {
                    stringResource(R.string.empty_search_body, query)
                } else {
                    stringResource(R.string.empty_body)
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            if (searching) {
                OutlinedButton(
                    onClick = onClearSearch,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(stringResource(R.string.clear_search_action))
                }
            } else {
                PrimaryButton(
                    text = stringResource(R.string.empty_cta),
                    onClick = onAddClick,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SubscriptionRow(
    sub: Subscription,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val daysLeft = DateCalculators.calculateDaysUntil(sub.nextBillDate)
    val localizedCategory = localizedCategory(sub.category)
    val renewalText = when {
        daysLeft < 0L -> stringResource(R.string.renewal_overdue)
        daysLeft == 0L -> stringResource(R.string.renewal_today)
        else -> stringResource(R.string.renewal_days_left, daysLeft.toInt())
    }
    val cycleText = when (sub.cycle) {
        BillingCycle.WEEKLY -> stringResource(R.string.cycle_short_weekly)
        BillingCycle.MONTHLY -> stringResource(R.string.cycle_short_monthly)
        BillingCycle.QUARTERLY -> stringResource(R.string.cycle_short_quarterly)
        BillingCycle.ANNUALLY -> stringResource(R.string.cycle_short_yearly)
    }
    val urgent = daysLeft in 0..3

    SwipeableSubscriptionCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        onDelete = onDelete,
        contentDescription = "${sub.name}, $localizedCategory, $renewalText"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandIconBadge(
                name = sub.name,
                brandColorHex = sub.colorHex,
                size = 44.dp,
                cornerRadius = 12.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sub.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TabularCurrencyText(
                        amount = sub.amount,
                        currencyCode = sub.currency,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = localizedCategory,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (urgent) {
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            }
                        ) {
                            Text(
                                text = renewalText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    fontWeight = if (urgent) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (urgent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = cycleText,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Maps stored category keys (including legacy ones) onto translated labels. */
@Composable
fun localizedCategory(category: String): String = when (category.lowercase()) {
    "all" -> stringResource(R.string.filter_all)
    "streaming", "entertainment" -> stringResource(R.string.filter_streaming)
    "productivity" -> stringResource(R.string.filter_productivity)
    "cloud" -> stringResource(R.string.filter_cloud)
    "utilities" -> stringResource(R.string.filter_utilities)
    "health" -> stringResource(R.string.filter_health)
    else -> category
}
