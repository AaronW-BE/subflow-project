package org.dpdns.alwaysup.subflow.ui.components

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import org.dpdns.alwaysup.subflow.BuildConfig
import org.dpdns.alwaysup.subflow.R
import org.dpdns.alwaysup.subflow.data.ads.LocalAdsConsent
import org.dpdns.alwaysup.subflow.domain.util.CurrencyFormatter
import org.dpdns.alwaysup.subflow.ui.theme.AppleIndigo
import org.dpdns.alwaysup.subflow.ui.util.rememberHaptics

@Composable
fun AppleCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    backgroundBrush: Brush? = null,
    border: BorderStroke? = BorderStroke(Dp.Hairline, MaterialTheme.colorScheme.outline),
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val haptics = rememberHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // iOS spring response on touch: quick squeeze, fluid spring rebound.
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.965f else 1.0f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 450f),
        label = "appleCardSpringScale"
    )

    val shape = RoundedCornerShape(cornerRadius)

    Surface(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClickLabel = onClickLabel,
                        role = Role.Button,
                        onClick = {
                            haptics.tick()
                            onClick()
                        }
                    )
                } else Modifier
            ),
        shape = shape,
        color = if (backgroundBrush == null) backgroundColor else Color.Transparent,
        border = border
    ) {
        Column(
            modifier = Modifier
                .then(if (backgroundBrush != null) Modifier.background(backgroundBrush) else Modifier)
                .padding(contentPadding),
            content = content
        )
    }
}

/**
 * Swipe-to-delete row. The swipe only *arms* the delete; the caller shows an
 * undo snackbar, so a mis-swipe is always recoverable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableSubscriptionCard(
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    contentDescription: String? = null,
    content: @Composable () -> Unit
) {
    val haptics = rememberHaptics()
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                haptics.confirm()
                onDelete()
                true
            } else false
        },
        // Require a deliberate swipe past half the row rather than a flick.
        positionalThreshold = { distance -> distance * 0.5f }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.delete_action),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        },
        modifier = modifier
    ) {
        AppleCard(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (contentDescription != null) {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    } else Modifier
                ),
            onClick = onClick
        ) {
            content()
        }
    }
}

/** Apple-style segmented control with a sliding pill indicator. */
@Composable
fun <T> CupertinoSegmentedControl(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemLabel: @Composable (T) -> String = { it.toString() }
) {
    val haptics = rememberHaptics()
    val selectedIndex = items.indexOf(selectedItem).coerceAtLeast(0)
    val density = LocalDensity.current

    var containerWidthPx by remember { mutableFloatStateOf(0f) }

    val segmentWidthPx = if (items.isNotEmpty() && containerWidthPx > 0f) {
        (containerWidthPx - with(density) { 6.dp.toPx() }) / items.size
    } else 0f

    val pillOffsetXPx by animateFloatAsState(
        targetValue = segmentWidthPx * selectedIndex,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "slidingPillAnimation"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onGloballyPositioned { containerWidthPx = it.size.width.toFloat() }
            .padding(3.dp)
    ) {
        if (segmentWidthPx > 0f) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(pillOffsetXPx.toInt(), 0) }
                    .width(with(density) { segmentWidthPx.toDp() })
                    .fillMaxHeight()
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(11.dp))
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.surface)
            )
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "tabTextColor"
                )
                val label = itemLabel(item)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(11.dp))
                        .semantics {
                            contentDescription = label
                            role = Role.Tab
                            selected = isSelected
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (!isSelected) {
                                haptics.tick()
                                onItemSelected(item)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = textColor
                        )
                    )
                }
            }
        }
    }
}

/** Brand badge: the service's initial on its signature colour. */
@Composable
fun BrandIconBadge(
    name: String,
    brandColorHex: String,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    cornerRadius: Dp = 13.dp
) {
    val parsedColor = remember(brandColorHex) { parseHexColor(brandColorHex) }
    // Several real brands are near-black (Notion, Apple TV+, GitHub) and a few
    // are near-white. Painted raw, those tiles dissolve into the card they sit
    // on - invisible on OLED black in particular. Nudge only those toward
    // legibility; brands with adequate contrast are left exactly as authored.
    val surface = MaterialTheme.colorScheme.surface
    val tileColor = remember(parsedColor, surface) { legibleOn(parsedColor, surface) }
    val gradient = remember(tileColor) {
        Brush.linearGradient(listOf(tileColor, tileColor.copy(alpha = 0.82f)))
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(gradient)
            // The initial is decoration; the row already announces the name.
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.trim().take(1).uppercase().ifBlank { "?" },
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = (size.value * 0.44).sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
        )
    }
}

// Plain function, so the fallback has to be a constant rather than a theme
// lookup. It is only reached when a stored brand colour fails to parse.
fun parseHexColor(hex: String, fallback: Color = AppleIndigo): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) {
    fallback
}

private fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

/**
 * Blends [color] toward whichever pole [ground] is not, until the pair clears a
 * modest contrast floor. 1.35:1 is well under the text threshold on purpose:
 * this is a filled shape, not a glyph, and the aim is a visible edge rather
 * than a recoloured logo.
 */
internal fun legibleOn(color: Color, ground: Color): Color {
    if (contrastRatio(color, ground) >= 1.35f) return color
    val pole = if (ground.luminance() < 0.5f) Color.White else Color.Black
    var mix = 0f
    var out = color
    while (mix < 0.6f && contrastRatio(out, ground) < 1.35f) {
        mix += 0.05f
        out = lerp(color, pole, mix)
    }
    return out
}

/** Currency display with tabular figures so digits do not jitter as values change. */
@Composable
fun TabularCurrencyText(
    amount: Double,
    currencyCode: String = "USD",
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) {
        configuration.locales.get(0) ?: java.util.Locale.getDefault()
    }
    Text(
        text = CurrencyFormatter.format(amount, currencyCode, locale),
        modifier = modifier,
        maxLines = 1,
        style = style.copy(
            fontWeight = FontWeight.Bold,
            color = color,
            fontFeatureSettings = "tnum"
        )
    )
}

/** iOS grouped-table container. */
@Composable
fun AppleGroupedCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    AppleCard(
        modifier = modifier,
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(0.dp)
    ) {
        content()
    }
}

/**
 * The single row separator used across every grouped list.
 *
 * `Dp.Hairline` renders exactly one physical pixel at any density, which is
 * what a table separator should be; `0.5.dp` was rounding to 1-2px depending
 * on the device. The colour is `outlineVariant` - stronger than the container
 * edge, because the line between two rows carries more meaning than the box
 * around them.
 *
 * [startInset] aligns the line with the row's text: 62dp past a leading glyph,
 * 16dp without one.
 */
@Composable
fun AppleRowSeparator(startInset: Dp = 16.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = startInset),
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = Dp.Hairline
    )
}

/** Uppercase grouped-table section header. */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
        ),
        modifier = modifier.padding(start = 4.dp, top = 4.dp, bottom = 6.dp)
    )
}

@Composable
fun AppleListRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconBackground: Color = iconTint.copy(alpha = 0.15f),
    valueText: String? = null,
    enabled: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val haptics = rememberHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val contentAlpha = if (enabled) 1f else 0.4f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null && enabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = {
                            haptics.tick()
                            onClick()
                        }
                    )
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 48dp minimum touch target for every actionable row.
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconBackground.copy(alpha = iconBackground.alpha * contentAlpha)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint.copy(alpha = contentAlpha),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                    )
                }
            }

            if (valueText != null) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.widthIn(max = 190.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            if (trailingContent != null) {
                trailingContent()
            } else if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f * contentAlpha),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (showDivider) {
            AppleRowSeparator(startInset = if (icon != null) 62.dp else 16.dp)
        }
    }
}

/** 52dp full-width primary action, per the design system. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    leadingContent: (@Composable () -> Unit)? = null
) {
    val haptics = rememberHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "primaryButtonScale"
    )

    Button(
        onClick = {
            haptics.confirm()
            onClick()
        },
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .scale(scale),
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            disabledContainerColor = containerColor.copy(alpha = 0.35f)
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp)
    ) {
        if (leadingContent != null) {
            leadingContent()
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Small "PRO" pill used to mark gated features. */
@Composable
fun ProBadge(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = color
    ) {
        Text(
            text = "PRO",
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.6.sp),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            // Never let the pill break into "PR / O" in a cramped row.
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Snackbar host matching the design system.
 *
 * Material's default snackbar paints itself on `inverseSurface`, which in a
 * pitch-black OLED theme is a light grey slab with dark text - correct by
 * Material's rules, jarring next to everything else here.
 */
@Composable
fun SubFlowSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
    ) { data ->
        Snackbar(
            snackbarData = data,
            shape = RoundedCornerShape(14.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            actionColor = MaterialTheme.colorScheme.primary,
            actionContentColor = MaterialTheme.colorScheme.primary,
            dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ------------------------------------------------------------------ skeletons

/**
 * Shimmering placeholder. Used while the first Room emission is in flight so a
 * cold start shows structure instead of an "empty" state that is about to be
 * replaced.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )

    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(progress * 900f - 450f, 0f),
                    end = Offset(progress * 900f, 300f)
                )
            )
    )
}

@Composable
fun SubscriptionRowSkeleton(modifier: Modifier = Modifier) {
    AppleCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShimmerBox(modifier = Modifier.size(44.dp), cornerRadius = 12.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.45f).height(15.dp))
                Spacer(modifier = Modifier.height(8.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.7f).height(11.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            ShimmerBox(modifier = Modifier.width(56.dp).height(16.dp))
        }
    }
}

// ------------------------------------------------------------------------ ads

/**
 * Anchored adaptive banner for the free tier.
 *
 * Renders nothing until UMP consent allows an ad request, and destroys the
 * AdView when it leaves composition - a leaked AdView keeps refreshing (and
 * billing impressions) after the screen is gone.
 */
@Composable
fun AdMobAdaptiveBanner(
    modifier: Modifier = Modifier,
    adUnitId: String = BuildConfig.ADMOB_BANNER_UNIT_ID
) {
    val consent = LocalAdsConsent.current
    // consent is null in @Preview; fall back to rendering nothing.
    val canRequestAds = if (consent != null) {
        consent.canRequestAds.collectAsState().value
    } else {
        false
    }

    if (!canRequestAds) return

    val adLabel = stringResource(R.string.cd_advertisement)
    val configuration = LocalConfiguration.current
    val adWidthDp = configuration.screenWidthDp

    var adView by remember { mutableStateOf<AdView?>(null) }

    DisposableEffect(Unit) {
        onDispose { adView?.destroy() }
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = adLabel },
        factory = { ctx ->
            AdView(ctx).apply {
                val activity = ctx as? Activity
                val size = if (activity != null) {
                    // Ads 25.x deprecates this in favour of the "large" anchored
                    // adaptive family (getLargeAnchoredAdaptiveBannerAdSize),
                    // which returns a taller slot. This banner is the dashboard's
                    // bottomBar, so adopting it would eat visible list rows -
                    // that is a layout decision, not part of an SDK upgrade.
                    // The deprecated call still works and keeps today's height.
                    @Suppress("DEPRECATION")
                    AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, adWidthDp)
                } else {
                    AdSize.BANNER
                }
                setAdSize(size)
                this.adUnitId = adUnitId
                loadAd(AdRequest.Builder().build())
                adView = this
            }
        }
    )
}
