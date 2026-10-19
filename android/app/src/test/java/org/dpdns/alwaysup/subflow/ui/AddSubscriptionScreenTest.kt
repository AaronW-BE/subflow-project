package org.dpdns.alwaysup.subflow.ui

import android.content.Context
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import org.dpdns.alwaysup.subflow.R
import org.dpdns.alwaysup.subflow.domain.model.BillingCycle
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.ui.screens.add.AddSubscriptionScreen
import org.dpdns.alwaysup.subflow.ui.theme.SubFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Adding a subscription, and editing one.
 *
 * The form's fields are found by being editable - name, then price, then
 * notes - rather than by placeholder text, which is the first thing a copy
 * edit changes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h2400dp")
class AddSubscriptionScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private var saved: Subscription? = null

    private fun show(existing: Subscription? = null) = compose.setContent {
        SubFlowTheme {
            AddSubscriptionScreen(
                presets = emptyList(),
                primaryCurrency = "USD",
                isPro = false,
                existingSubscription = existing,
                onSaveSubscription = { saved = it },
                onCancel = {}
            )
        }
    }

    private fun field(index: Int) = compose.onAllNodes(hasSetTextAction())[index]

    private fun save() {
        compose.onNodeWithText(context.getString(R.string.save)).performClick()
        compose.waitForIdle()
    }

    @Test
    fun `a new subscription is saved with what was typed`() {
        show()
        field(0).performTextInput("Netflix")
        field(1).performTextInput("15.49")
        save()

        val s = saved ?: throw AssertionError("nothing was saved")
        assertEquals("Netflix", s.name)
        assertEquals(15.49, s.amount, 0.0)
        assertEquals("USD", s.currency)
        assertEquals(BillingCycle.MONTHLY, s.cycle)
        assertTrue("a new one gets an id", s.id.isNotBlank())
    }

    @Test
    fun `without a name nothing is saved`() {
        show()
        field(1).performTextInput("15.49")
        save()
        assertNull(saved)
    }

    @Test
    fun `editing keeps the subscription's id and changes what was edited`() {
        val existing = Subscription(
            id = "sub_existing",
            name = "Spotify",
            category = "Streaming",
            amount = 9.99,
            currency = "USD",
            firstBillDate = "2026-01-10",
            nextBillDate = "2026-11-10"
        )
        show(existing)
        field(1).performTextClearance()
        field(1).performTextInput("11.99")
        save()

        val s = saved ?: throw AssertionError("nothing was saved")
        assertEquals("sub_existing", s.id)
        assertEquals("Spotify", s.name)
        assertEquals(11.99, s.amount, 0.0)
        assertEquals("2026-01-10", s.firstBillDate)
    }
}
