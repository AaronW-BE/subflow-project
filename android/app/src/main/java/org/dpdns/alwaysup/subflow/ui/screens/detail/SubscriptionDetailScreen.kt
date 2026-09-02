package org.dpdns.alwaysup.subflow.ui.screens.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dpdns.alwaysup.subflow.R
import org.dpdns.alwaysup.subflow.domain.model.BillingCycle
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.util.CurrencyFormatter
import org.dpdns.alwaysup.subflow.domain.util.DateCalculators
import org.dpdns.alwaysup.subflow.ui.components.AppleCard
import org.dpdns.alwaysup.subflow.ui.components.AppleGroupedCard
import org.dpdns.alwaysup.subflow.ui.components.AppleListRow
import org.dpdns.alwaysup.subflow.ui.components.BrandIconBadge
import org.dpdns.alwaysup.subflow.ui.components.SectionHeader
import org.dpdns.alwaysup.subflow.ui.components.TabularCurrencyText
import org.dpdns.alwaysup.subflow.ui.screens.dashboard.localizedCategory
import java.util.Locale

@Composable
fun SubscriptionDetailScreen(
    subscription: Subscription?,
    onBack: () -> Unit,
    onEdit: (Subscription) -> Unit,
    onDelete: (String) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) { configuration.locales.get(0) ?: Locale.getDefault() }

    if (subscription == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.not_found))
        }
        return
    }

    val daysLeft = DateCalculators.calculateDaysUntil(subscription.nextBillDate)
    val periodDays = remember(subscription.nextBillDate, subscription.cycle) {
        DateCalculators.periodLengthDays(subscription.nextBillDate, subscription.cycle)
    }
    val elapsedDays = (periodDays - daysLeft).coerceIn(0, periodDays.toLong())
    val progressPct = (elapsedDays.toFloat() / periodDays.toFloat()).coerceIn(0f, 1f)

    val cyclesPaid = remember(subscription) {
        DateCalculators.cyclesElapsed(subscription.firstBillDate, subscription.cycle)
    }
    val paidSoFar = cyclesPaid * subscription.amount

    val urgent = daysLeft in 0..3
    val cancelUrl = remember(subscription.name) { cancellationUrlFor(subscription.name) }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    onClick = onBack
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircleIconButton(
                        icon = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit),
                        onClick = { onEdit(subscription) }
                    )
                    CircleIconButton(
                        icon = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error,
                        background = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        onClick = { showDeleteConfirmDialog = true }
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "hero") {
                AppleCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BrandIconBadge(
                            name = subscription.name,
                            brandColorHex = subscription.colorHex,
                            size = 64.dp,
                            cornerRadius = 18.dp,
                            iconUri = subscription.iconUrl
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = subscription.name,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            Text(
                                text = localizedCategory(subscription.category),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        CountdownRing(
                            progress = progressPct,
                            daysLeft = daysLeft,
                            urgent = urgent
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        TabularCurrencyText(
                            amount = subscription.amount,
                            currencyCode = subscription.currency,
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp)
                        )
                        Text(
                            text = stringResource(
                                R.string.detail_billed_cycle,
                                cycleLabel(subscription.cycle)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item(key = "billing_header") {
                SectionHeader(text = stringResource(R.string.detail_billing_details))
            }

            item(key = "billing") {
                AppleGroupedCard(modifier = Modifier.fillMaxWidth()) {
                    AppleListRow(
                        title = stringResource(R.string.detail_cycle),
                        valueText = cycleLabel(subscription.cycle)
                    )
                    AppleListRow(
                        title = stringResource(R.string.detail_next_renewal),
                        valueText = DateCalculators.formatMedium(subscription.nextBillDate, locale)
                    )
                    AppleListRow(
                        title = stringResource(R.string.detail_first_payment),
                        valueText = DateCalculators.formatMedium(subscription.firstBillDate, locale)
                    )
                    AppleListRow(
                        title = stringResource(R.string.detail_monthly_equivalent),
                        valueText = CurrencyFormatter.format(
                            subscription.monthlyAmount,
                            subscription.currency,
                            locale
                        )
                    )
                    AppleListRow(
                        title = stringResource(R.string.detail_yearly_commitment),
                        valueText = CurrencyFormatter.format(
                            subscription.yearlyAmount,
                            subscription.currency,
                            locale
                        )
                    )
                    // Cumulative spend is the number that actually changes behaviour.
                    AppleListRow(
                        title = stringResource(R.string.detail_paid_so_far),
                        valueText = CurrencyFormatter.format(paidSoFar, subscription.currency, locale)
                    )
                    AppleListRow(
                        title = stringResource(R.string.detail_reminder),
                        valueText = if (subscription.reminderDaysBefore > 0) {
                            if (subscription.reminderDaysBefore == 1) {
                                stringResource(R.string.reminder_lead_one)
                            } else {
                                stringResource(R.string.reminder_lead_value, subscription.reminderDaysBefore)
                            }
                        } else {
                            stringResource(R.string.detail_reminder_off)
                        },
                        showDivider = subscription.notes.isNotBlank()
                    )
                    if (subscription.notes.isNotBlank()) {
                        AppleListRow(
                            title = stringResource(R.string.detail_notes),
                            valueText = subscription.notes,
                            showDivider = false
                        )
                    }
                }
            }

            item(key = "cancel_link") {
                AppleCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpenUrl(cancelUrl) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.detail_manage_official),
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.detail_manage_official_sub),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item(key = "delete") {
                Button(
                    onClick = { showDeleteConfirmDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.detail_remove),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            item(key = "footer") { Spacer(modifier = Modifier.height(28.dp)) }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    stringResource(R.string.delete_confirm_title, subscription.name),
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text(stringResource(R.string.delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete(subscription.id)
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    background: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
) {
    IconButton(
        onClick = onClick,
        // 48dp hit area with a 38dp visual, per the touch-target minimum.
        modifier = Modifier.size(48.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun CountdownRing(progress: Float, daysLeft: Long, urgent: Boolean) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 800),
        label = "ringProgress"
    )
    val trackColor = MaterialTheme.colorScheme.outline
    val arcColor = if (urgent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(130.dp)) {
            val strokeWidth = 10.dp.toPx()
            drawCircle(
                color = trackColor,
                radius = (size.minDimension - strokeWidth) / 2f,
                style = Stroke(strokeWidth)
            )
            drawArc(
                color = arcColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = Stroke(strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                daysLeft < 0 -> Text(
                    text = stringResource(R.string.detail_overdue),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
                daysLeft == 0L -> Text(
                    text = stringResource(R.string.detail_due_today),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.Center
                )
                else -> {
                    Text(
                        text = daysLeft.toString(),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            fontFeatureSettings = "tnum"
                        ),
                        color = if (urgent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.detail_days_left),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun cycleLabel(cycle: BillingCycle): String = when (cycle) {
    BillingCycle.WEEKLY -> stringResource(R.string.cycle_weekly)
    BillingCycle.MONTHLY -> stringResource(R.string.cycle_monthly)
    BillingCycle.QUARTERLY -> stringResource(R.string.cycle_quarterly)
    BillingCycle.ANNUALLY -> stringResource(R.string.cycle_yearly)
}

/**
 * Best-effort shortcut to the provider's own cancellation page. Anything not
 * recognised falls back to the Play subscriptions centre, which covers every
 * subscription actually billed through Google.
 */
private fun cancellationUrlFor(name: String): String {
    val key = name.lowercase().trim()
    return when {
        key.contains("netflix") -> "https://www.netflix.com/youraccount"
        key.contains("spotify") -> "https://www.spotify.com/account/subscription"
        key.contains("youtube") -> "https://www.youtube.com/paid_memberships"
        key.contains("chatgpt") || key.contains("openai") -> "https://chat.openai.com/#settings/Subscription"
        key.contains("claude") -> "https://claude.ai/settings/billing"
        key.contains("icloud") || key.contains("apple") -> "https://support.apple.com/billing"
        key.contains("disney") -> "https://www.disneyplus.com/account/subscription"
        key.contains("amazon") || key.contains("prime") -> "https://www.amazon.com/mc"
        key.contains("adobe") -> "https://account.adobe.com/plans"
        key.contains("dropbox") -> "https://www.dropbox.com/account/plan"
        key.contains("notion") -> "https://www.notion.so/my-account"
        key.contains("github") -> "https://github.com/settings/billing"
        key.contains("microsoft") || key.contains("365") -> "https://account.microsoft.com/services"
        key.contains("google") -> "https://one.google.com/settings"
        else -> "https://play.google.com/store/account/subscriptions"
    }
}
