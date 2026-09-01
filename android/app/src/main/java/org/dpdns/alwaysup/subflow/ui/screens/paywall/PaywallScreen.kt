package org.dpdns.alwaysup.subflow.ui.screens.paywall

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dpdns.alwaysup.subflow.R
import org.dpdns.alwaysup.subflow.data.billing.BillingConnectionState
import org.dpdns.alwaysup.subflow.data.billing.ProPlan
import org.dpdns.alwaysup.subflow.domain.model.ProTier
import org.dpdns.alwaysup.subflow.domain.util.CurrencyFormatter
import org.dpdns.alwaysup.subflow.ui.components.AppleCard
import org.dpdns.alwaysup.subflow.ui.components.PrimaryButton
import org.dpdns.alwaysup.subflow.ui.components.SectionHeader
import org.dpdns.alwaysup.subflow.ui.components.ShimmerBox
import java.util.Locale

private data class ProFeature(val titleRes: Int, val subtitleRes: Int)

private val PRO_FEATURES = listOf(
    ProFeature(R.string.feat_unlimited, R.string.feat_unlimited_sub),
    ProFeature(R.string.feat_push_alerts, R.string.feat_push_alerts_sub),
    ProFeature(R.string.feat_analytics, R.string.feat_analytics_sub),
    ProFeature(R.string.feat_rates, R.string.feat_rates_sub),
    ProFeature(R.string.feat_no_ads, R.string.feat_no_ads_sub)
)

/**
 * Pro paywall.
 *
 * Every price shown here comes from Google Play's ProductDetails, never from a
 * hard-coded string: Play localises prices per country and taxes, and a
 * mismatch between the displayed price and the charged price is a policy
 * violation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaywallScreen(
    plans: Map<ProTier, ProPlan>,
    connectionState: BillingConnectionState,
    catalogueLoaded: Boolean,
    isPro: Boolean,
    proTier: ProTier,
    onDismiss: () -> Unit,
    onPurchaseTier: (ProTier) -> Unit,
    onRestorePurchases: () -> Unit,
    onManageSubscription: () -> Unit,
    onRetry: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) { configuration.locales.get(0) ?: Locale.getDefault() }

    val annual = plans[ProTier.ANNUAL]
    val monthly = plans[ProTier.MONTHLY]
    val lifetime = plans[ProTier.LIFETIME]

    // Default to the plan with a trial, else the annual plan, else whatever loaded.
    var selectedTier by remember(plans.keys) {
        mutableStateOf(
            when {
                annual != null -> ProTier.ANNUAL
                monthly != null -> ProTier.MONTHLY
                lifetime != null -> ProTier.LIFETIME
                else -> ProTier.ANNUAL
            }
        )
    }

    val savingsPercent = remember(annual, monthly) { computeAnnualSavings(annual, monthly) }
    val selectedPlan = plans[selectedTier]

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // The copy is deliberately allowed to run past the fold, but a hard
            // clip through a line of text reads as a rendering fault rather than
            // as "scroll for more", so the edge is faded into the background.
            Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ProBadgeHeader()

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.paywall_headline),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 27.sp,
                        lineHeight = 33.sp,
                        textAlign = TextAlign.Center
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.paywall_subhead),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (isPro) {
                    AlreadyProCard(proTier = proTier, onManageSubscription = onManageSubscription)
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Plans sit above the feature copy, not below it. Placed last
                // they fell under the fold on an ordinary phone and the Lifetime
                // tier was never seen. Pinning them to the bottom bar fixed that
                // but collapsed the whole page at large font scales, so they
                // scroll with everything else - just from a position high enough
                // that all three are visible on first paint at normal sizes.
                if (!isPro) {
                    when {
                        plans.isNotEmpty() -> {
                            PlanList(
                                annual = annual,
                                monthly = monthly,
                                lifetime = lifetime,
                                selectedTier = selectedTier,
                                savingsPercent = savingsPercent,
                                locale = locale,
                                onSelect = { selectedTier = it }
                            )
                        }
                        // Connected but the catalogue came back empty means the
                        // products are missing from the Play Console - showing
                        // skeletons for ever would be a dead end.
                        catalogueLoaded || connectionState == BillingConnectionState.UNAVAILABLE -> {
                            PlansUnavailableCard(onRetry = onRetry)
                        }
                        else -> {
                            PlanSkeletons()
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }

                FeatureList()

                Spacer(modifier = Modifier.height(16.dp))
            }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(28.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                )
            }

            // Sticky action area: only the CTA and the legal links, which stay
            // short at any font scale, so they can never crowd out the page.
            Surface(
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 12.dp, bottom = 8.dp)
                ) {
                    if (!isPro) {
                        PrimaryButton(
                            text = ctaLabel(selectedPlan),
                            enabled = selectedPlan != null,
                            onClick = { onPurchaseTier(selectedTier) }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        AnimatedVisibility(
                            visible = selectedPlan != null,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Text(
                                text = legalFootnote(selectedPlan),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    textAlign = TextAlign.Center
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // A plain Row cannot fit three links plus separators at large
                    // font scales - "Privacy Policy" ended up wrapping one letter
                    // per line down the edge of the screen. FlowRow moves the
                    // overflow onto a second line instead.
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (!isPro) {
                            TextButton(onClick = onRestorePurchases) {
                                Text(
                                    text = stringResource(R.string.btn_restore),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            LegalDot()
                        }
                        TextButton(onClick = { onOpenUrl(TERMS_URL) }) {
                            Text(
                                text = stringResource(R.string.terms_of_service),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        LegalDot()
                        TextButton(onClick = { onOpenUrl(PRIVACY_URL) }) {
                            Text(
                                text = stringResource(R.string.privacy_policy),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegalDot() {
    Text(
        text = "·",
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun ProBadgeHeader() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    )
                )
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.WorkspacePremium,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = stringResource(R.string.subflow_pro),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun FeatureList() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PRO_FEATURES.forEach { feature ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Column {
                    Text(
                        text = stringResource(feature.titleRes),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(feature.subtitleRes),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanList(
    annual: ProPlan?,
    monthly: ProPlan?,
    lifetime: ProPlan?,
    selectedTier: ProTier,
    savingsPercent: Int,
    locale: Locale,
    onSelect: (ProTier) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        annual?.let { plan ->
            PlanCard(
                plan = plan,
                selected = selectedTier == ProTier.ANNUAL,
                title = stringResource(R.string.plan_annual),
                priceLine = stringResource(R.string.plan_per_year, plan.formattedPrice),
                supportLine = effectiveMonthlyLine(plan, locale)
                    ?: stringResource(R.string.plan_annual_note),
                badge = when {
                    plan.hasFreeTrial -> stringResource(R.string.badge_free_trial, plan.freeTrialDays)
                    savingsPercent > 0 -> stringResource(R.string.badge_save_percent, savingsPercent)
                    else -> stringResource(R.string.badge_best_value)
                },
                onClick = { onSelect(ProTier.ANNUAL) }
            )
        }
        monthly?.let { plan ->
            PlanCard(
                plan = plan,
                selected = selectedTier == ProTier.MONTHLY,
                title = stringResource(R.string.plan_monthly),
                priceLine = stringResource(R.string.plan_per_month, plan.formattedPrice),
                supportLine = stringResource(R.string.plan_monthly_note),
                badge = if (plan.hasFreeTrial) {
                    stringResource(R.string.badge_free_trial, plan.freeTrialDays)
                } else null,
                onClick = { onSelect(ProTier.MONTHLY) }
            )
        }
        lifetime?.let { plan ->
            PlanCard(
                plan = plan,
                selected = selectedTier == ProTier.LIFETIME,
                title = stringResource(R.string.plan_lifetime),
                priceLine = stringResource(R.string.plan_one_time, plan.formattedPrice),
                supportLine = stringResource(R.string.plan_lifetime_note),
                badge = null,
                onClick = { onSelect(ProTier.LIFETIME) }
            )
        }
    }
}

@Composable
private fun PlanCard(
    plan: ProPlan,
    selected: Boolean,
    title: String,
    priceLine: String,
    supportLine: String,
    badge: String?,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.985f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f),
        label = "planCardScale"
    )

    AppleCard(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        cornerRadius = 18.dp,
        backgroundColor = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            // The selected plan keeps a real 2dp ring; the rest are hairlines.
            width = if (selected) 2.dp else Dp.Hairline,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        onClick = onClick,
        onClickLabel = title
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Radio indicator
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (badge != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primary) {
                            Text(
                                text = badge,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    text = supportLine,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = priceLine,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                fontWeight = FontWeight.Bold,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun PlanSkeletons() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(text = stringResource(R.string.loading_plans))
        repeat(3) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                cornerRadius = 18.dp
            )
        }
    }
}

@Composable
private fun PlansUnavailableCard(onRetry: () -> Unit) {
    AppleCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.plans_unavailable),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onRetry,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.retry), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AlreadyProCard(proTier: ProTier, onManageSubscription: () -> Unit) {
    AppleCard(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(Dp.Hairline, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.already_pro_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.already_pro_body),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // Lifetime is a one-time product, so there is nothing to manage in Play.
        if (proTier == ProTier.MONTHLY || proTier == ProTier.ANNUAL) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onManageSubscription,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.manage_subscription), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---------------------------------------------------------------- helpers

private const val TERMS_URL = "https://subflow.alwaysup.dpdns.org/terms.html"
private const val PRIVACY_URL = "https://subflow.alwaysup.dpdns.org/privacy.html"

@Composable
private fun ctaLabel(plan: ProPlan?): String = when {
    plan == null -> stringResource(R.string.btn_continue)
    plan.hasFreeTrial -> stringResource(R.string.btn_start_trial, plan.freeTrialDays)
    else -> stringResource(R.string.btn_continue)
}

@Composable
private fun legalFootnote(plan: ProPlan?): String = when {
    plan == null -> ""
    plan.hasFreeTrial -> stringResource(
        R.string.trial_footnote,
        plan.freeTrialDays,
        recurringPriceLabel(plan)
    )
    plan.tier == ProTier.LIFETIME -> stringResource(R.string.lifetime_footnote, plan.formattedPrice)
    else -> stringResource(R.string.sub_footnote, recurringPriceLabel(plan))
}

@Composable
private fun recurringPriceLabel(plan: ProPlan): String = when (plan.tier) {
    ProTier.ANNUAL -> stringResource(R.string.plan_per_year, plan.formattedPrice)
    ProTier.MONTHLY -> stringResource(R.string.plan_per_month, plan.formattedPrice)
    else -> plan.formattedPrice
}

/** "$1.67 / month" for the annual plan, so the comparison is like-for-like. */
@Composable
private fun effectiveMonthlyLine(plan: ProPlan, locale: Locale): String? {
    if (plan.tier != ProTier.ANNUAL || plan.priceAmountMicros <= 0) return null
    val perMonth = CurrencyFormatter.formatPlayPrice(
        micros = plan.priceAmountMicros / 12,
        currencyCode = plan.priceCurrencyCode,
        locale = locale
    )
    return stringResource(R.string.plan_effective_monthly, perMonth)
}

/** Whole-percent saving of the annual plan against 12 months of the monthly plan. */
private fun computeAnnualSavings(annual: ProPlan?, monthly: ProPlan?): Int {
    if (annual == null || monthly == null) return 0
    if (annual.priceAmountMicros <= 0 || monthly.priceAmountMicros <= 0) return 0
    if (annual.priceCurrencyCode != monthly.priceCurrencyCode) return 0
    val twelveMonths = monthly.priceAmountMicros * 12.0
    val saving = (1.0 - annual.priceAmountMicros / twelveMonths) * 100.0
    return saving.toInt().coerceIn(0, 95)
}
