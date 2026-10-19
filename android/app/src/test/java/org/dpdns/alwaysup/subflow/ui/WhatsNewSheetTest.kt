package org.dpdns.alwaysup.subflow.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.dpdns.alwaysup.subflow.R
import org.dpdns.alwaysup.subflow.ui.components.WhatsNewSheet
import org.dpdns.alwaysup.subflow.ui.theme.SubFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The sheet that says what changed after an update (BIN-60).
 *
 * It is the one place a closed-test tester is asked for a reply, so both of
 * its buttons have to do what they say.
 */
@RunWith(RobolectricTestRunner::class)
class WhatsNewSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private var dismissed = 0
    private var feedback = 0

    private fun show() = compose.setContent {
        SubFlowTheme {
            WhatsNewSheet(onFeedback = { feedback++ }, onDismiss = { dismissed++ })
        }
    }

    @Test
    fun `it lists what changed`() {
        show()
        compose.onNodeWithText(context.getString(R.string.whats_new_title)).assertIsDisplayed()
        listOf(R.string.whats_new_item_1, R.string.whats_new_item_2, R.string.whats_new_item_3).forEach {
            compose.onNodeWithText(context.getString(it)).assertIsDisplayed()
        }
    }

    @Test
    fun `got it dismisses the sheet and asks for nothing`() {
        show()
        compose.onNodeWithText(context.getString(R.string.whats_new_done)).performClick()
        assertEquals(1, dismissed)
        assertEquals(0, feedback)
    }

    @Test
    fun `send feedback asks for a reply`() {
        show()
        compose.onNodeWithText(context.getString(R.string.whats_new_feedback)).performClick()
        assertEquals(1, feedback)
    }
}
