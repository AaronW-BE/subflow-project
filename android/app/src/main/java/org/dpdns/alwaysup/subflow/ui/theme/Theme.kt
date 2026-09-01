package org.dpdns.alwaysup.subflow.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import org.dpdns.alwaysup.subflow.data.preferences.ThemeMode

/*
 * Semantic roles are mapped onto Material's slots so screens never name a raw
 * colour:
 *
 *   primary   -> brand indigo
 *   secondary -> positive / healthy (emerald)
 *   tertiary  -> renewal warning (amber)
 *   error     -> destructive (coral)
 *
 * Each slot carries the variant tuned for that mode, so the same call site is
 * legible in both without a single `if (isDark)` in the UI layer.
 */

private val DarkColorScheme = darkColorScheme(
    primary = AppleIndigoBright,
    onPrimary = Color.White,
    primaryContainer = AppleIndigoDeep,
    onPrimaryContainer = PrimaryInkDark,

    secondary = FinancialEmerald,
    onSecondary = CanvasDark,
    tertiary = RenewalAmber,
    onTertiary = CanvasDark,
    error = AlertCoral,
    onError = CanvasDark,

    background = CanvasDark,
    onBackground = PrimaryInkDark,
    surface = SurfaceDark,
    onSurface = PrimaryInkDark,
    surfaceVariant = SurfaceDarkHigh,
    onSurfaceVariant = SecondaryInkDark,
    // Used by the themed snackbar; keep it in the app's own greys rather than
    // Material's default light-on-dark inverse slab.
    inverseSurface = SurfaceDarkHigh,
    inverseOnSurface = PrimaryInkDark,
    // outline = container edge (faint), outlineVariant = row separator (stronger).
    outline = HairlineDark,
    outlineVariant = SeparatorDark,
    scrim = CanvasDark
)

private val LightColorScheme = lightColorScheme(
    primary = AppleIndigo,
    onPrimary = Color.White,
    primaryContainer = AppleIndigoTint,
    onPrimaryContainer = AppleIndigoDeep,

    secondary = FinancialEmeraldDeep,
    onSecondary = Color.White,
    tertiary = RenewalAmberDeep,
    onTertiary = Color.White,
    error = AlertCoralDeep,
    onError = Color.White,

    background = CanvasLight,
    onBackground = PrimaryInkLight,
    surface = SurfaceLight,
    onSurface = PrimaryInkLight,
    surfaceVariant = SurfaceLightHigh,
    onSurfaceVariant = SecondaryInkLight,
    inverseSurface = PrimaryInkLight,
    inverseOnSurface = SurfaceLight,
    outline = HairlineLight,
    outlineVariant = SeparatorLight
)

@Composable
fun SubFlowTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // The bar colours themselves come from enableEdgeToEdge(); only the
            // icon contrast has to follow the in-app theme, which may differ
            // from the system theme when the user forces light or dark.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppleTypography,
        content = content
    )
}
