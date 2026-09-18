package org.dpdns.alwaysup.subflow.ui.screens.dashboard

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
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
import org.dpdns.alwaysup.subflow.data.repository.ActiveChange
import org.dpdns.alwaysup.subflow.data.repository.SubscriptionRepository
import org.dpdns.alwaysup.subflow.domain.model.BillingCycle
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.util.CurrencyConverter
import org.dpdns.alwaysup.subflow.domain.util.CurrencyFormatter
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

/**
 * The dashboard list: what the category chips and the search box let through,
 * in the order the sort sheet asked for.
 *
 * Active and paused subscriptions go through the same call, so a search finds
 * a subscription whichever state it is in, and the two lists can never drift
 * into filtering differently.
 */
fun List<Subscription>.filterAndSort(
    category: String,
    query: String,
    order: DashboardSortOrder,
    primaryCurrency: String
): List<Subscription> = this
    .filter { sub ->
        val matchCat = category == "All" ||
            sub.category.equals(category, ignoreCase = true) ||
            // "Entertainment" is the legacy label for what is now "Streaming".
            (category == "Streaming" && sub.category.equals("Entertainment", true))
        val q = query.trim()
        val matchQuery = q.isBlank() ||
            sub.name.contains(q, ignoreCase = true) ||
            sub.category.contains(q, ignoreCase = true) ||
            sub.notes.contains(q, ignoreCase = true)
        matchCat && matchQuery
    }
    .let { list ->
        when (order) {
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
    /**
     * Pauses or resumes, answering with what changed. Null means the free tier
     * refused a resume and the caller has already said so - this screen must
     * not then report a success it did not get.
     */
    onSetActive: suspend (String, Boolean) -> ActiveChange? = { _, _ -> null },
    /** Writes a record back as it was, which is what undo needs. */
    onRevertSubscription: suspend (Subscription) -> Unit = {},
    onPaywallClick: () -> Unit,
    showSwipeHint: Boolean = false,
    onSwipeHintSeen: () -> Unit = {}
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
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) { configuration.locales.get(0) ?: Locale.getDefault() }
    val searchFocus = remember { FocusRequester() }

    var selectedCategory by rememberSaveable { mutableStateOf("All") }
    var isAnnualView by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortOrder by rememberSaveable { mutableStateOf(DashboardSortOrder.RENEWAL_DATE) }
    var showSortSheet by remember { mutableStateOf(false) }
    // Collapsed by default: paused subscriptions are the ones the user has
    // said they are not thinking about.
    var pausedExpanded by rememberSaveable { mutableStateOf(false) }

    val categories = listOf(
        "All" to R.string.filter_all,
        "Streaming" to R.string.filter_streaming,
        "Productivity" to R.string.filter_productivity,
        "Cloud" to R.string.filter_cloud,
        "Utilities" to R.string.filter_utilities,
        "Health" to R.string.filter_health
    )

    val activeSubs = remember(subscriptions) { subscriptions.filter { it.isActive && !it.isDeleted } }
    val pausedSubs = remember(subscriptions) { subscriptions.filter { !it.isActive && !it.isDeleted } }
    val totalMonthly = remember(activeSubs, primaryCurrency) {
        activeSubs.sumOf { CurrencyConverter.convert(it.monthlyAmount, it.currency, primaryCurrency) }
    }
    val totalYearly = totalMonthly * 12.0

    val filteredSubs = remember(activeSubs, selectedCategory, searchQuery, sortOrder, primaryCurrency) {
        activeSubs.filterAndSort(selectedCategory, searchQuery, sortOrder, primaryCurrency)
    }
    // The same search and category apply to the paused list, so one query
    // answers "where did my Netflix go" whichever state it is in.
    val filteredPaused = remember(pausedSubs, selectedCategory, searchQuery, sortOrder, primaryCurrency) {
        pausedSubs.filterAndSort(selectedCategory, searchQuery, sortOrder, primaryCurrency)
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

    // Both lists offer both swipes, and the delete handler had already been
    // copied out twice. One definition each, so the active list and the paused
    // section cannot drift apart.
    fun confirmWithUndo(message: String, undo: () -> Unit) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = undoText,
                // Long, not Short. Short is about four seconds, and an
                // undo is not a status message - it is the only way back
                // from a delete. Driving this by hand while testing the
                // pause swipe, knowing exactly where the button was, the
                // window closed twice before the tap landed and the tap
                // hit whatever had moved under it.
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }

    fun deleteWithUndo(sub: Subscription) {
        val id = sub.id
        onSwipeHintSeen()
        onDeleteSubscription(id)
        confirmWithUndo(context.getString(R.string.deleted_item, sub.name)) {
            onRestoreSubscription(id)
        }
    }

    fun togglePaused(sub: Subscription) {
        val name = sub.name
        val resuming = !sub.isActive
        onSwipeHintSeen()
        scope.launch {
            // A resume can be refused: five active plus a paused sixth is a
            // legal state, and resuming the sixth is where the free tier ends.
            // The paywall is the caller's to show, and there is nothing to
            // report or undo here.
            val change = onSetActive(sub.id, resuming) ?: return@launch
            val message = when {
                // Resuming a cancelled trial starts a real charge. Saying only
                // "resumed" would let a swipe begin billing without a word.
                change.convertedToPaid -> context.getString(
                    R.string.resumed_as_paid,
                    name,
                    CurrencyFormatter.format(
                        change.updated.amount,
                        change.updated.currency,
                        locale
                    )
                )
                resuming -> context.getString(R.string.resumed_item, name)
                else -> context.getString(R.string.paused_item, name)
            }
            // Writes the whole record back rather than flipping the flag: a
            // conversion also moved the amount, the cycle, the first-bill date
            // and the trial fields, and none of those return on their own.
            confirmWithUndo(message) {
                scope.launch { onRevertSubscription(change.previous) }
            }
        }
    }

    val addFabLabel = stringResource(R.string.add_subscription)
    val backToTopLabel = stringResource(R.string.back_to_top)
    // Hidden only while the list is being scrolled downwards. Exploring by
    // touch is the one case where it stays put: a screen reader moves through
    // the list by scrolling it, and a control that disappears as you read past
    // it is a control you cannot reach.
    val exploringByTouch = rememberTouchExplorationEnabled()
    val addVisible = exploringByTouch || listState.isScrollingUp()

    Scaffold(
        snackbarHost = {
            SubFlowSnackbarHost(hostState = snackbarHostState, modifier = Modifier.padding(bottom = 12.dp))
        },
        bottomBar = { if (!isPro) AdMobAdaptiveBanner() },
        // The Scaffold's own slot rather than a Box overlay: it is what keeps
        // the button above the ad banner and clear of the navigation bar
        // without this screen having to know how tall either of them is.
        floatingActionButton = {
            // The button floats over the bottom-right of the list, and the
            // bottom-right of a row is its price. Reading down the list meant
            // reading past a button sitting on the column the list exists to
            // show. It gets out of the way while the list is being read and
            // comes back the moment the user reverses - which is also when
            // they have found what they came for and might add another.
            AnimatedVisibility(
                visible = addVisible,
                enter = scaleIn(spring(dampingRatio = 0.6f, stiffness = 500f)) + fadeIn(),
                exit = scaleOut(tween(120)) + fadeOut(tween(120)),
                // Tracks the right edge of the list rather than the right edge
                // of the window. Once the content stops filling a wide screen,
                // a button left in the corner is a button standing on its own
                // in the margin, pointing at nothing. Zero on a phone, where
                // the gutter is the 16dp the Scaffold already gives it.
                modifier = Modifier.padding(
                    end = (contentGutter() - 16.dp).coerceAtLeast(0.dp)
                )
            ) {
                FloatingActionButton(
                    onClick = {
                        haptics.tick()
                        onAddClick()
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    modifier = Modifier.semantics { contentDescription = addFabLabel }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
                    .padding(horizontal = contentGutter()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "header") {
                    DashboardHeader(
                        dateLabel = todayFormatted,
                        isPro = isPro,
                        onPaywallClick = onPaywallClick
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

                if (showSwipeHint && !isLoading && filteredSubs.isNotEmpty()) {
                    item(key = "swipe_hint") {
                        SwipeHintCard(onDismiss = onSwipeHintSeen)
                    }
                }

                when {
                    isLoading && activeSubs.isEmpty() -> {
                        items(4, key = { "skeleton_$it" }) { SubscriptionRowSkeleton() }
                    }

                    filteredSubs.isEmpty() && filteredPaused.isEmpty() -> {
                        item(key = "empty") {
                            EmptyState(
                                query = searchQuery,
                                // Null when the chip row is on "All", which is
                                // what tells the card whether a filter is part
                                // of why it is being shown.
                                categoryLabel = selectedCategory
                                    .takeIf { it != "All" }
                                    ?.let { localizedCategory(it) },
                                onAddClick = onAddClick,
                                onClearFilters = {
                                    searchQuery = ""
                                    selectedCategory = "All"
                                }
                            )
                        }
                    }

                    else -> {
                        items(filteredSubs, key = { it.id }) { sub ->
                            SubscriptionRow(
                                sub = sub,
                                onClick = { onSubscriptionClick(sub.id) },
                                onDelete = { deleteWithUndo(sub) },
                                onTogglePaused = { togglePaused(sub) }
                            )
                        }
                    }
                }

                if (filteredPaused.isNotEmpty()) {
                    item(key = "paused_header") {
                        PausedSectionHeader(
                            count = filteredPaused.size,
                            expanded = pausedExpanded,
                            onToggle = {
                                haptics.tick()
                                pausedExpanded = !pausedExpanded
                            }
                        )
                    }

                    if (pausedExpanded) {
                        items(filteredPaused, key = { it.id }) { sub ->
                            SubscriptionRow(
                                sub = sub,
                                onClick = { onSubscriptionClick(sub.id) },
                                onDelete = { deleteWithUndo(sub) },
                                onTogglePaused = { togglePaused(sub) }
                            )
                        }
                    }
                }

                // Clears the floating button: 56dp of button, 16dp of the
                // margin Scaffold gives it, and the 28dp this always had. A
                // list that scrolls its last row under the button hides the
                // price, which is the column the button sits over.
                item(key = "footer_space") { Spacer(modifier = Modifier.height(100.dp)) }
            }

            // Collapsed bar
            AnimatedVisibility(
                visible = isCollapsed,
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut() + slideOutVertically { -it / 2 },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                // Opaque, not nearly-opaque. At 96% the white cards scrolling
                // underneath came through as legible grey text - the quota
                // card's body sat behind the title, and its button showed as a
                // ghost sliced off at the divider. 4% of a white card on a
                // grey bar is not a frosted effect, it is a rendering fault.
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                // Tapping the title goes back to the top. It
                                // is the convention this app's design language
                                // already borrows, and it is the only way back
                                // to the search field and the sort button once
                                // they have scrolled away - the alternative
                                // was dragging the whole list up by hand.
                                // No ripple: the bar is scenery until it is
                                // needed, and a flash across the full width
                                // would announce itself on every mis-tap.
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClickLabel = backToTopLabel
                                ) {
                                    haptics.tick()
                                    scope.launch { listState.animateScrollToItem(0) }
                                }
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

// ------------------------------------------------------------------ scrolling

/**
 * Whether the last movement of this list was upwards. True before it has
 * moved at all, so nothing is hidden on a screen the user has not scrolled.
 *
 * Reading the two `firstVisibleItem` values rather than `isScrollInProgress`
 * is what makes it settle: a flick that ends still reports the direction it
 * ended in, instead of snapping back the instant the finger lifts.
 */
@Composable
private fun LazyListState.isScrollingUp(): Boolean {
    var previousIndex by remember(this) { mutableIntStateOf(firstVisibleItemIndex) }
    var previousOffset by remember(this) { mutableIntStateOf(firstVisibleItemScrollOffset) }
    return remember(this) {
        derivedStateOf {
            if (previousIndex != firstVisibleItemIndex) {
                previousIndex > firstVisibleItemIndex
            } else {
                previousOffset >= firstVisibleItemScrollOffset
            }.also {
                previousIndex = firstVisibleItemIndex
                previousOffset = firstVisibleItemScrollOffset
            }
        }
    }.value
}

/**
 * Whether the system is exploring the screen by touch - TalkBack and the
 * other screen readers. Read live, because it can be switched on while the
 * app is in front.
 */
@Composable
private fun rememberTouchExplorationEnabled(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    }
    var enabled by remember { mutableStateOf(manager.isTouchExplorationEnabled) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager.addTouchExplorationStateChangeListener(listener)
        onDispose { manager.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}

// -------------------------------------------------------------------- pieces

@Composable
private fun DashboardHeader(
    dateLabel: String,
    isPro: Boolean,
    onPaywallClick: () -> Unit
) {
    val upgradeLabel = stringResource(R.string.upgrade_to_pro)

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
                style = ScreenTitleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Only shown to free users, so there is no disabled state to
            // design: the control is absent rather than dead once Pro is on.
            if (!isPro) {
                // This said "PRO" in a tinted pill, which is the word and very
                // nearly the shape this app uses for two things that are not
                // this: ProBadge marks a feature you cannot reach yet, and the
                // account card wears one to say you already have Pro. The only
                // way to buy Pro was dressed as an announcement that you had
                // it. It now says what it does and carries the chevron every
                // other row that opens a page carries.
                //
                // No contentDescription: the visible text is the label now,
                // and adding one would only give a screen reader the same
                // sentence twice. The role is set because nothing else sets
                // it - a tree dump showed this announcing as a view rather
                // than as a button.
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onPaywallClick)
                        .semantics { role = Role.Button },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) {
                    // Tight on purpose, and measured rather than guessed. At
                    // 360dp the English title wants 207dp and this row is what
                    // is left over: at the paddings and 11sp that looked right
                    // in isolation the title came out as "Subscriptio..." and
                    // the pill sat against it. Every dp taken from here goes
                    // back to the title, because the title column has the
                    // weight, so this is also what buys the gap between them.
                    Row(
                        modifier = Modifier
                            .heightIn(min = 34.dp)
                            .padding(start = 9.dp, end = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = upgradeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp
                            ),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
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
                        style = HeroAmountStyle
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

@OptIn(ExperimentalLayoutApi::class)
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
    val ctaLabel = stringResource(R.string.quota_nudge_cta)

    AppleCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(14.dp),
        onClick = if (nearLimit) onUpgrade else null,
        onClickLabel = if (nearLimit) ctaLabel else null
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

        // One line, not a pitch. The full list of what Pro adds sat here on
        // every launch for as long as the plan stayed full - the tallest
        // thing on the screen, above the subscriptions it is there to track -
        // and the paywall one tap away already says all of it.
        AnimatedVisibility(visible = nearLimit) {
            // A flow, so where the two do not fit side by side - German at a
            // large font on a narrow phone - the link drops under the line
            // instead of squeezing it into a three-line column.
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(
                        if (atLimit) R.string.quota_nudge_title_full else R.string.quota_nudge_title
                    ),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(end = 8.dp)
                )
                // Not a button of its own: the whole card opens the paywall,
                // and a second target inside it would be read out twice.
                Row(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ctaLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(11.dp)
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

/** How much of each edge the chip row dissolves over. */
private val CategoryFadeWidth = 28.dp

@Composable
private fun CategoryFilterRow(
    categories: List<Pair<String, Int>>,
    selectedCategory: String,
    onSelect: (String) -> Unit
) {
    val rowState = rememberLazyListState()
    // Only where there is actually something past the edge. On a wide screen
    // every chip fits and a fade there would be dimming the last one for no
    // reason.
    val fadeStart by remember { derivedStateOf { rowState.canScrollBackward } }
    val fadeEnd by remember { derivedStateOf { rowState.canScrollForward } }

    LazyRow(
        state = rowState,
        // A chip sliced through the middle of its word - "Ut" for Utilities -
        // reads as a layout fault. Faded, the same cut says there is more this
        // way, which is the only thing it was ever trying to say.
        //
        // DstIn multiplies the row's alpha by the gradient's, so it needs the
        // row on its own layer first; without the offscreen strategy the blend
        // would take the whole screen behind it with it.
        modifier = Modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fade = CategoryFadeWidth.toPx().coerceAtMost(size.width / 3f)
                if (fadeStart) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black,
                            startX = 0f,
                            endX = fade
                        ),
                        size = Size(fade, size.height),
                        blendMode = BlendMode.DstIn
                    )
                }
                if (fadeEnd) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.Black,
                            1f to Color.Transparent,
                            startX = size.width - fade,
                            endX = size.width
                        ),
                        topLeft = Offset(size.width - fade, 0f),
                        size = Size(fade, size.height),
                        blendMode = BlendMode.DstIn
                    )
                }
            },
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

/**
 * The card shown when the list has nothing in it.
 *
 * It has to tell two very different stories apart. "You have no
 * subscriptions" is one of them. "You have plenty, but this filter matches
 * none of them" is the other, and it used to be told with the first one's
 * words - someone with five subscriptions who tapped a category chip that
 * matched none of them was informed they had none at all, under a button
 * offering to add their first. Their data had not gone anywhere; the filter
 * had.
 *
 * So anything that narrows the list is named here, and the button clears it
 * rather than offering to add.
 */
@Composable
private fun EmptyState(
    query: String,
    /** The selected category, already translated. Null means "All". */
    categoryLabel: String?,
    onAddClick: () -> Unit,
    onClearFilters: () -> Unit
) {
    val searching = query.isNotBlank()
    val filtering = categoryLabel != null
    // Either one is enough to mean "this list is not empty, it is narrowed".
    val narrowed = searching || filtering
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
                    imageVector = if (narrowed) Icons.Default.Search else Icons.Default.Inbox,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(
                    if (narrowed) R.string.empty_search_title else R.string.empty_title
                ),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = when {
                    // Both are on, and naming only one of them would send the
                    // user hunting for a word that is in the list, under a
                    // chip that hides it.
                    searching && categoryLabel != null ->
                        stringResource(R.string.empty_filter_search_body, query, categoryLabel)
                    searching -> stringResource(R.string.empty_search_body, query)
                    categoryLabel != null ->
                        stringResource(R.string.empty_filter_body, categoryLabel)
                    else -> stringResource(R.string.empty_body)
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            if (narrowed) {
                OutlinedButton(
                    onClick = onClearFilters,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    // It clears both either way. It is only called "clear
                    // search" when a search is the only thing it would clear.
                    Text(
                        stringResource(
                            if (filtering) R.string.clear_filters_action
                            else R.string.clear_search_action
                        )
                    )
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

/**
 * One line telling the user the rows can be swiped, shown until they dismiss
 * it or delete something.
 *
 * Deliberately a row in the list rather than a tooltip or a coach mark: it
 * takes its turn in the scroll, never covers what it describes, and a screen
 * reader reaches it in reading order instead of having it thrown at them.
 */
@Composable
private fun SwipeHintCard(onDismiss: () -> Unit) {
    val dismissLabel = stringResource(R.string.swipe_hint_dismiss)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.swipe_hint),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = dismissLabel,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SubscriptionRow(
    sub: Subscription,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTogglePaused: () -> Unit
) {
    val daysLeft = DateCalculators.calculateDaysUntil(sub.nextBillDate)
    val localizedCategory = localizedCategory(sub.category)
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) { configuration.locales.get(0) ?: Locale.getDefault() }
    // A running trial reuses every slot in this row, because what the row is
    // for does not change: what is it, when is the next thing, what does it
    // cost. The answers differ - the date is an ending, and the cost is still
    // ahead - so only the words change, not the shape.
    val isTrial = sub.isTrialPending
    val renewalText = when {
        !sub.isActive -> stringResource(R.string.paused)
        isTrial && daysLeft < 0L -> stringResource(R.string.trial_ended)
        isTrial && daysLeft == 0L -> stringResource(R.string.trial_ends_today)
        isTrial -> pluralStringResource(
            R.plurals.trial_days_left,
            daysLeft.toInt(),
            daysLeft.toInt()
        )
        daysLeft < 0L -> stringResource(R.string.renewal_overdue)
        daysLeft == 0L -> stringResource(R.string.renewal_today)
        else -> pluralStringResource(
            R.plurals.renewal_days_left,
            daysLeft.toInt(),
            daysLeft.toInt()
        )
    }
    val cycleText = when {
        isTrial && !sub.trialConverts -> stringResource(R.string.trial_no_charge)
        isTrial -> stringResource(
            R.string.trial_then_amount,
            CurrencyFormatter.format(sub.postTrialAmount, sub.currency, locale)
        )
        sub.cycle == BillingCycle.WEEKLY -> stringResource(R.string.cycle_short_weekly)
        sub.cycle == BillingCycle.MONTHLY -> stringResource(R.string.cycle_short_monthly)
        sub.cycle == BillingCycle.QUARTERLY -> stringResource(R.string.cycle_short_quarterly)
        else -> stringResource(R.string.cycle_short_yearly)
    }
    // An ended trial is the most urgent thing this list can show: it is a
    // question the user still owes an answer to, and possibly a charge.
    val urgent = sub.isActive && if (isTrial) daysLeft <= 3L else daysLeft in 0..3

    SwipeableSubscriptionCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        onDelete = onDelete,
        onTogglePaused = onTogglePaused,
        isActive = sub.isActive,
        // The upcoming charge is spoken for a trial: it is the fact the row
        // exists to carry, and element-by-element reading never attaches the
        // "then ..." line to the service it belongs to.
        contentDescription = listOfNotNull(
            sub.name,
            localizedCategory,
            renewalText,
            if (isTrial) cycleText else null
        ).joinToString(", ")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandIconBadge(
                name = sub.name,
                brandColorHex = sub.colorHex,
                size = 44.dp,
                cornerRadius = 12.dp,
                iconUri = sub.iconUrl
            )

            Spacer(modifier = Modifier.width(12.dp))

            SubscriptionRowContent(
                name = sub.name,
                category = localizedCategory,
                renewalText = renewalText,
                urgent = urgent,
                amount = sub.amount,
                currencyCode = sub.currency,
                cycleLabel = cycleText,
                modifier = Modifier.weight(1f),
                freeLabel = if (isTrial) stringResource(R.string.trial_free_amount) else null
            )
        }
    }
}

/**
 * The divider between what the user is paying for and what they have put down.
 *
 * It collapses because a paused subscription is, by definition, one they said
 * they are not thinking about - but it stays on the same screen, because a
 * pause that hid a subscription entirely would be indistinguishable from a
 * delete.
 */
@Composable
private fun PausedSectionHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "pausedChevron"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.dashboard_paused_header, count),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier
                .size(10.dp)
                .rotate(rotation)
        )
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
