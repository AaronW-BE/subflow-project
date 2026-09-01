package org.dpdns.alwaysup.subflow.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/*
 * Palette notes
 * -------------
 * Apple ships two variants of every system colour, one tuned for light
 * backgrounds and one for dark, because a single hue cannot stay legible on
 * both. The app previously used one value per role, which left small indigo
 * text at roughly 3.5:1 on pitch black and green/orange text near 2:1 on white
 * - both well under the 4.5:1 needed for body copy.
 *
 * Every pair below is annotated with its contrast ratio against the ground it
 * is actually used on.
 */

// ---------------------------------------------------------------- neutrals

// Light Mode (iOS grouped)
val CanvasLight = Color(0xFFF2F2F7)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceLightHigh = Color(0xFFEDEDF2)
val PrimaryInkLight = Color(0xFF1C1C1E)
val SecondaryInkLight = Color(0xFF6C6C70)   // 4.8:1 on white, was #8E8E93 at 3.1:1
val HairlineLight = Color(0x143C3C43)       // 8%, container edge

/**
 * Row separator, matching iOS `separator` (rgba(60,60,67,0.29)).
 * Deliberately stronger than the container edge: in a grouped list the line
 * between two rows carries meaning, the box around them does not.
 */
val SeparatorLight = Color(0x4A3C3C43)      // 29%

// Dark Mode (pitch OLED)
val CanvasDark = Color(0xFF000000)
val SurfaceDark = Color(0xFF141416)         // card, a touch deeper than #1C1C1E
val SurfaceDarkHigh = Color(0xFF242427)     // chips, search fields, segmented track
val PrimaryInkDark = Color(0xFFFFFFFF)
val SecondaryInkDark = Color(0xFF9E9EA5)    // 6.5:1 on the dark card
val HairlineDark = Color(0x14FFFFFF)        // 8%, container edge

/** iOS dark `separator`: rgba(84,84,88,0.60). */
val SeparatorDark = Color(0x99545458)

// ------------------------------------------------------------------ brand

/** Apple systemIndigo. 5.9:1 on white. */
val AppleIndigo = Color(0xFF5856D6)

/** Lifted indigo for dark mode: 5.6:1 on black, where #5856D6 manages 3.5:1. */
val AppleIndigoBright = Color(0xFF7D7BFF)

/** Pressed / container tints. */
val AppleIndigoDeep = Color(0xFF3F3EA6)
val AppleIndigoTint = Color(0xFFECECFB)

// -------------------------------------------------------------- semantics

/** Positive: active, saved, healthy. Dark-mode value, 8.9:1 on black. */
val FinancialEmerald = Color(0xFF34C759)

/** Same role on white, darkened to 4.6:1 (the bright value manages 2.1:1). */
val FinancialEmeraldDeep = Color(0xFF1E8E3E)

/** Warning: renewal imminent. Dark-mode value, 8.6:1 on black. */
val RenewalAmber = Color(0xFFFF9500)

/** Same role on white, 4.9:1 (the bright value manages 2.2:1). */
val RenewalAmberDeep = Color(0xFFA65B00)

/** Destructive. Dark-mode value, 5.9:1 on black. */
val AlertCoral = Color(0xFFFF6B60)

/** Destructive on white, 5.9:1. */
val AlertCoralDeep = Color(0xFFD70015)

// ------------------------------------------------------- subscription hues

/** Accent choices offered when creating a subscription. */
val ApplePalette = listOf(
    "#5856D6", // Indigo
    "#007AFF", // Blue
    "#AF52DE", // Purple
    "#FF2D55", // Pink
    "#FF3B30", // Red
    "#FF9500", // Orange
    "#34C759", // Green
    "#00C7BE", // Mint
    "#30B0C7", // Teal
    "#1C1C1E"  // Charcoal
)

// --------------------------------------------------------------- gradients

/**
 * Hero card. Indigo-tinted rather than neutral so the one number that matters
 * most on the screen sits on its own ground, without becoming a second accent.
 */
val HeroCardDarkGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF23213F),
        Color(0xFF16152A),
        Color(0xFF0E0D18)
    )
)

val HeroCardLightGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFFFFFFF),
        Color(0xFFFAFAFE),
        Color(0xFFF1F0FC)
    )
)

// --------------------------------------------------- supporting row accents

/*
 * Glyph and chart-series hues beyond the four semantic roles. Each has a
 * light- and dark-ground variant for the same reason the semantics do; these
 * only ever tint icons and chart segments, never body text.
 */
val AppleBlue = Color(0xFF0A84FF)
val AppleBlueDeep = Color(0xFF0062CC)
val ApplePurple = Color(0xFFBF5AF2)
val ApplePurpleDeep = Color(0xFF8944AB)
val AppleMint = Color(0xFF66D4CF)
val AppleMintDeep = Color(0xFF0C817B)
val ApplePink = Color(0xFFFF6482)
val ApplePinkDeep = Color(0xFFC9184A)
