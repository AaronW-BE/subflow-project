package org.dpdns.alwaysup.subflow.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Hues that sit outside the four semantic roles: Settings row glyphs and the
 * category chart series.
 *
 * These used to be inline hex literals scattered through the screens, which
 * meant the same "blue" was a different value in two places and neither
 * adapted to the theme. Reading them from here keeps the palette in one file
 * and picks the variant that is legible on the current ground.
 */
object SubFlowAccents {

    private val isDark: Boolean
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val blue: Color @Composable @ReadOnlyComposable get() = if (isDark) AppleBlue else AppleBlueDeep
    val purple: Color @Composable @ReadOnlyComposable get() = if (isDark) ApplePurple else ApplePurpleDeep
    val mint: Color @Composable @ReadOnlyComposable get() = if (isDark) AppleMint else AppleMintDeep
    val pink: Color @Composable @ReadOnlyComposable get() = if (isDark) ApplePink else ApplePinkDeep

    /**
     * Ordered series for the category breakdown. Starts with the semantic
     * roles so the most significant slices read as "brand, healthy, warning"
     * before drifting into decorative hues.
     */
    val chartSeries: List<Color>
        @Composable @ReadOnlyComposable get() = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary,
            blue,
            purple,
            pink,
            mint
        )
}
