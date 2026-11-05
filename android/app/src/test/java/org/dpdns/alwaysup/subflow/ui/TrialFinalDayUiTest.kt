package org.dpdns.alwaysup.subflow.ui

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.dpdns.alwaysup.subflow.R
import org.dpdns.alwaysup.subflow.data.preferences.PreferencesManager
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.util.Trials
import org.dpdns.alwaysup.subflow.ui.components.*
import org.dpdns.alwaysup.subflow.ui.screens.add.preservedTrialKeepDate
import org.dpdns.alwaysup.subflow.ui.theme.SubFlowTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h2400dp")
class TrialFinalDayUiTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun text(key: Int) = context.getString(key)
    private fun tap(key: Int) = compose.onNodeWithText(text(key)).performClick()
    private fun trial() = Subscription(id = "trial", firstBillDate = "2099-06-01", nextBillDate = "2099-06-18", name = "Trial", amount = 0.0, isTrial = true,
        trialEndDate = "2099-06-18", reminderDaysBefore = 3)

    @Test fun settingsDefaultOffAndEnableShowsTimeWithoutPrompt() {
        val prefs = PreferencesManager(context)
        assertFalse(prefs.trialFinalDayEnabled.value)
        assertEquals(540, prefs.trialFinalDayMinuteOfDay.value)
        assertFalse(prefs.trialFinalDayExactEnabled.value)
        var timeClicks = 0
        var exactClicks = 0
        compose.setContent {
            var enabled by remember { mutableStateOf(false) }
            SubFlowTheme { TrialFinalDaySettingsContent(enabled, "09:00", false, false, true,
                onEnabled = { enabled = it }, onTime = { timeClicks++ }, onExact = { exactClicks++ }) }
        }
        compose.onNodeWithText(text(R.string.trial_final_exact)).assertDoesNotExist()
        compose.onAllNodes(isToggleable())[0].assertIsOff().performClick()
        compose.onNodeWithText(context.getString(R.string.trial_final_time, "09:00")).assertExists().performClick()
        assertEquals(1, timeClicks)
        assertEquals(0, exactClicks)
    }

    @Test fun deniedPrecisionAndBlockedNotificationsAreSeparate() {
        var selected = true
        compose.setContent { SubFlowTheme {
            TrialFinalDaySettingsContent(true, "09:00", true, false, false,
                onEnabled = {}, onTime = {}, onExact = { selected = it })
        } }
        compose.onNodeWithText(text(R.string.trial_final_access_no)).assertExists()
        compose.onNodeWithText(text(R.string.trial_final_ordinary)).assertExists()
        compose.onNodeWithText(text(R.string.trial_final_notifications_blocked)).assertExists()
        compose.onNodeWithText(text(R.string.trial_final_precise)).assertDoesNotExist()
        compose.onAllNodes(isToggleable())[1].performClick()
        assertFalse(selected)
    }

    @Test fun keepAndUndoChangeOnlyDecision() {
        val original = trial()
        var result = original
        compose.setContent {
            var current by remember { mutableStateOf(original) }
            SubFlowTheme { TrialDecisionCard(current) { current = it; result = it } }
        }
        tap(R.string.trial_keep)
        assertEquals(original.copy(trialKeepEndDate = original.trialEndDate), result)
        assertTrue(result.isTrialPending)
        assertEquals(0.0, result.amount, 0.0)
        assertEquals(original.reminderDaysBefore, result.reminderDaysBefore)
        tap(R.string.trial_undo_keep)
        assertEquals(original, result)
    }

    @Test fun providerCancellationDoesNothingUntilConfirmed() {
        val original = trial()
        var result: Subscription? = null
        compose.setContent { SubFlowTheme { TrialDecisionCard(original) { result = it } } }
        tap(R.string.trial_provider_cancelled)
        assertNull(result)
        compose.onNodeWithText(text(R.string.trial_cancel_confirm_body)).assertExists()
        tap(R.string.trial_cancel_back)
        assertNull(result)
        tap(R.string.trial_provider_cancelled)
        tap(R.string.trial_cancel_confirm)
        assertEquals(Trials.cancel(original), result)
        assertFalse(result!!.isActive)
    }

    @Test fun editPreservesOnlySamePendingTrialDate() {
        val kept = Trials.keep(trial())
        assertEquals(kept.trialEndDate, preservedTrialKeepDate(kept.copy(name = "Edited"), true, kept.trialEndDate))
        assertEquals("", preservedTrialKeepDate(kept, true, "2099-06-19"))
        assertEquals("", preservedTrialKeepDate(kept, false, kept.trialEndDate))
        assertEquals("", preservedTrialKeepDate(null, true, kept.trialEndDate))
        assertEquals("", preservedTrialKeepDate(Trials.cancel(kept), true, kept.trialEndDate))
    }
}
