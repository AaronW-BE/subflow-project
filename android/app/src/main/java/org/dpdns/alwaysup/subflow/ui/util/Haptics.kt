package org.dpdns.alwaysup.subflow.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Whether the user has haptics switched on in Settings. Provided once at the
 * root of the app so every component honours the preference - previously the
 * toggle was stored but never read, so turning it off did nothing.
 */
val LocalHapticsEnabled = compositionLocalOf { true }

@Immutable
class SubFlowHaptics(
    private val delegate: HapticFeedback,
    private val enabled: Boolean
) {
    /** Light tick: selection changes, chips, segmented control, toggles. */
    fun tick() {
        if (enabled) delegate.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    /** Heavier confirm: destructive swipes, saves, purchases. */
    fun confirm() {
        if (enabled) delegate.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

@Composable
@ReadOnlyComposable
private fun currentHapticsEnabled(): Boolean = LocalHapticsEnabled.current

@Composable
fun rememberHaptics(): SubFlowHaptics {
    val delegate = LocalHapticFeedback.current
    val enabled = currentHapticsEnabled()
    return remember(delegate, enabled) { SubFlowHaptics(delegate, enabled) }
}
