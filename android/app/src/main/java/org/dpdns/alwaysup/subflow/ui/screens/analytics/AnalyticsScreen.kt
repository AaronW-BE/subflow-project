package org.dpdns.alwaysup.subflow.ui.screens.analytics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dpdns.alwaysup.subflow.R
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.util.CurrencyConverter
import org.dpdns.alwaysup.subflow.domain.util.CurrencyFormatter
import org.dpdns.alwaysup.subflow.domain.util.DateCalculators
import org.dpdns.alwaysup.subflow.ui.components.*
import org.dpdns.alwaysup.subflow.ui.screens.dashboard.localizedCategory
import org.dpdns.alwaysup.subflow.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One point on the spend history curve. */
data class MonthlySpendPoint(val label: String, val amount: Double)

@Composable
fun AnalyticsScreen(
    subscriptions: List<Subscription>,
    isPro: Boolean,
    primaryCurrency: String = "USD",
    onPaywallClick: () -> Unit,
    onExportCsv: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) { configuration.locales.get(0) ?: Locale.getDefault() }

    var timeView by rememberSaveable { mutableStateOf(TimeView.MONTHLY) }

    val activeSubs = remember(subscriptions) { subscriptions.filter { it.isActive && !it.isDeleted } }
    val totalMonthly = remember(activeSubs, primaryCurrency) {
        activeSubs.sumOf { CurrencyConverter.convert(it.monthlyAmount, it.currency, primaryCurrency) }
    }
    val totalYearly = totalMonthly * 12.0
    val dailyAverage = if (totalMonthly > 0) totalMonthly * 12.0 / 365.0 else 0.0

    val categoryTotals = remember(activeSubs, primaryCurrency) {
        activeSubs.groupBy { it.category }
            .mapValues { (_, list) ->
                list.sumOf { CurrencyConverter.convert(it.monthlyAmount, it.currency, primaryCurrency) }
            }
            .toList()
            .sortedByDescending { it.second }
    }

    val topSubs = remember(activeSubs, primaryCurrency) {
        activeSubs.sortedByDescending {
            CurrencyConverter.convert(it.monthlyAmount, it.currency, primaryCurrency)
        }.take(5)
    }

    val topExpensePct = if (totalMonthly > 0 && topSubs.isNotEmpty()) {
        (CurrencyConverter.convert(topSubs.first().monthlyAmount, topSubs.first().currency, primaryCurrency) /
            totalMonthly * 100.0).toInt()
    } else 0

    // Real history, derived from when each subscription actually started.
    val history = remember(activeSubs, primaryCurrency, locale) {
        buildSpendHistory(activeSubs, primaryCurrency, locale)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "title") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 16.dp, bottom = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.analytics_title),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    )
                )
            }
        }

        if (activeSubs.isEmpty()) {
            item(key = "empty") {
                AppleCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.analytics_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@LazyColumn
        }

        item(key = "segment") {
            CupertinoSegmentedControl(
                items = TimeView.entries,
                selectedItem = timeView,
                onItemSelected = { timeView = it },
                itemLabel = { stringResource(it.labelRes) }
            )
        }

        item(key = "hero") {
            AppleCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(
                        if (timeView == TimeView.MONTHLY) {
                            R.string.metric_monthly_commitment
                        } else {
                            R.string.metric_annual_run_rate
                        }
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                TabularCurrencyText(
                    amount = if (timeView == TimeView.MONTHLY) totalMonthly else totalYearly,
                    currencyCode = primaryCurrency,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                ProGate(isPro = isPro, onUnlock = onPaywallClick) {
                    Column {
                        SpendTrendChart(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(124.dp),
                            points = history,
                            annualise = timeView == TimeView.ANNUAL,
                            currencyCode = primaryCurrency
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            history.forEach { point ->
                                Text(
                                    text = point.label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.history_caption),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        item(key = "metrics") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.metric_daily_avg)
                ) {
                    TabularCurrencyText(
                        amount = dailyAverage,
                        currencyCode = primaryCurrency,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    )
                }
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.metric_top_share)
                ) {
                    Text(
                        text = stringResource(R.string.metric_share_percent, topExpensePct),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        maxLines = 1
                    )
                }
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.metric_tracked)
                ) {
                    Text(
                        text = pluralStringResource(R.plurals.metric_services, activeSubs.size, activeSubs.size),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        maxLines = 1
                    )
                }
            }
        }

        item(key = "categories_header") {
            SectionHeader(text = stringResource(R.string.category_breakdown).uppercase())
        }

        item(key = "categories") {
            AppleCard(modifier = Modifier.fillMaxWidth()) {
                ProGate(isPro = isPro, onUnlock = onPaywallClick) {
                    CategoryBreakdown(
                        categoryTotals = categoryTotals,
                        totalMonthly = totalMonthly,
                        primaryCurrency = primaryCurrency
                    )
                }
            }
        }

        item(key = "top_header") {
            SectionHeader(text = stringResource(R.string.top_expenses).uppercase())
        }

        itemsIndexed(topSubs, key = { _, sub -> sub.id }) { idx, sub ->
            TopCommitmentRow(
                rank = idx + 1,
                sub = sub,
                primaryCurrency = primaryCurrency
            )
        }

        item(key = "export") {
            AppleCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { if (isPro) onExportCsv() else onPaywallClick() }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.FileDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.export_report),
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(
                                    if (isPro) R.string.export_report_sub_pro else R.string.export_report_sub_free
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isPro) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (!isPro) ProBadge()
                }
            }
        }

        item(key = "footer") { Spacer(modifier = Modifier.height(28.dp)) }
    }
}

enum class TimeView(val labelRes: Int) {
    MONTHLY(R.string.segment_monthly),
    ANNUAL(R.string.segment_annual)
}

// -------------------------------------------------------------------- pieces

/**
 * Renders [content] for Pro users; free users see it blurred behind an unlock
 * prompt so the value is visible without being usable.
 */
@Composable
private fun ProGate(
    isPro: Boolean,
    onUnlock: () -> Unit,
    content: @Composable () -> Unit
) {
    if (isPro) {
        content()
        return
    }

    // The teaser needs room for the lock, the copy and the button; a one-row
    // section would otherwise clip the overlay.
    Box(modifier = Modifier.heightIn(min = 168.dp)) {
        Box(modifier = Modifier.blur(9.dp)) { content() }

        Column(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.pro_locked_analytics),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary,
                onClick = onUnlock
            ) {
                Text(
                    text = stringResource(R.string.unlock_with_pro),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MetricTile(
    modifier: Modifier = Modifier,
    label: String,
    value: @Composable () -> Unit
) {
    AppleCard(
        modifier = modifier,
        cornerRadius = 16.dp,
        contentPadding = PaddingValues(14.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(4.dp))
        value()
    }
}

@Composable
private fun CategoryBreakdown(
    categoryTotals: List<Pair<String, Double>>,
    totalMonthly: Double,
    primaryCurrency: String
) {
    if (categoryTotals.isEmpty()) {
        Text(
            text = stringResource(R.string.analytics_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val categoryColors = SubFlowAccents.chartSeries

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
        ) {
            categoryTotals.forEachIndexed { idx, (_, amt) ->
                val pct = if (totalMonthly > 0) (amt / totalMonthly).toFloat() else 0f
                Box(
                    modifier = Modifier
                        .weight(pct.coerceAtLeast(0.01f))
                        .fillMaxHeight()
                        .background(categoryColors[idx % categoryColors.size])
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            categoryTotals.forEachIndexed { idx, (cat, amt) ->
                val pct = if (totalMonthly > 0) (amt / totalMonthly * 100.0).toInt() else 0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(categoryColors[idx % categoryColors.size])
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = localizedCategory(cat),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$pct%",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    TabularCurrencyText(
                        amount = amt,
                        currencyCode = primaryCurrency,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun TopCommitmentRow(
    rank: Int,
    sub: Subscription,
    primaryCurrency: String
) {
    AppleCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#$rank",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.width(34.dp)
            )

            BrandIconBadge(
                name = sub.name,
                brandColorHex = sub.colorHex,
                size = 38.dp,
                cornerRadius = 11.dp,
                iconUri = sub.iconUrl
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sub.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = localizedCategory(sub.category),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                TabularCurrencyText(
                    amount = CurrencyConverter.convert(sub.monthlyAmount, sub.currency, primaryCurrency),
                    currencyCode = primaryCurrency,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                )
                if (!sub.currency.equals(primaryCurrency, ignoreCase = true)) {
                    Text(
                        text = "(${CurrencyFormatter.format(sub.amount, sub.currency)})",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

/**
 * Cubic-Bezier spend curve.
 *
 * The y axis is anchored at zero so the shape reflects real proportions rather
 * than an auto-zoomed window that exaggerates small changes.
 */
@Composable
private fun SpendTrendChart(
    modifier: Modifier = Modifier,
    points: List<MonthlySpendPoint>,
    annualise: Boolean,
    currencyCode: String
) {
    if (points.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = CurrencyFormatter.formatCompact(
                    (points.firstOrNull()?.amount ?: 0.0) * if (annualise) 12 else 1,
                    currencyCode
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val values = points.map { it.amount * if (annualise) 12.0 else 1.0 }
    val maxValue = (values.maxOrNull() ?: 0.0).coerceAtLeast(0.0001)

    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(700),
        label = "chartReveal"
    )

    val lineColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas

        val topPadding = height * 0.12f
        val bottomPadding = height * 0.10f
        val usableHeight = height - topPadding - bottomPadding
        val stepX = width / (values.size - 1)

        val offsets = values.mapIndexed { index, value ->
            val ratio = (value / maxValue).toFloat().coerceIn(0f, 1f)
            Offset(index * stepX, topPadding + usableHeight * (1f - ratio))
        }

        val path = Path()
        val fillPath = Path()
        path.moveTo(offsets[0].x, offsets[0].y)
        fillPath.moveTo(offsets[0].x, height)
        fillPath.lineTo(offsets[0].x, offsets[0].y)

        for (i in 0 until offsets.size - 1) {
            val p0 = offsets[i]
            val p1 = offsets[i + 1]
            val cX = (p0.x + p1.x) / 2f
            path.cubicTo(cX, p0.y, cX, p1.y, p1.x, p1.y)
            fillPath.cubicTo(cX, p0.y, cX, p1.y, p1.x, p1.y)
        }

        fillPath.lineTo(offsets.last().x, height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                listOf(lineColor.copy(alpha = 0.26f * animatedProgress), lineColor.copy(alpha = 0.02f))
            )
        )
        drawPath(
            path = path,
            color = lineColor.copy(alpha = animatedProgress),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        offsets.forEachIndexed { idx, pt ->
            val isLast = idx == offsets.size - 1
            drawCircle(
                color = if (isLast) lineColor else lineColor.copy(alpha = 0.45f),
                radius = if (isLast) 5.dp.toPx() else 3.dp.toPx(),
                center = pt
            )
            if (isLast) {
                drawCircle(color = surfaceColor, radius = 2.4.dp.toPx(), center = pt)
            }
        }
    }
}

/**
 * Rebuilds the last six months of committed spend from each subscription's
 * first bill date, so the curve shows the user's own history rather than a
 * decorative shape.
 */
internal fun buildSpendHistory(
    subs: List<Subscription>,
    primaryCurrency: String,
    locale: Locale,
    today: LocalDate = LocalDate.now()
): List<MonthlySpendPoint> {
    val monthFormatter = DateTimeFormatter.ofPattern("MMM", locale)
    return (5 downTo 0).map { monthsAgo ->
        val month = today.minusMonths(monthsAgo.toLong())
        val endOfMonth = month.withDayOfMonth(month.lengthOfMonth())
        val total = subs
            .filter { sub ->
                val start = DateCalculators.parseOrNull(sub.firstBillDate)
                start == null || !start.isAfter(endOfMonth)
            }
            .sumOf { CurrencyConverter.convert(it.monthlyAmount, it.currency, primaryCurrency) }
        MonthlySpendPoint(
            label = month.format(monthFormatter),
            amount = total
        )
    }
}
