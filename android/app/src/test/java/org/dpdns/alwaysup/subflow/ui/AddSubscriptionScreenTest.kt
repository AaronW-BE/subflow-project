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
import org.dpdns.alwaysup.subflow.domain.util.MonthlyBudget
import org.dpdns.alwaysup.subflow.domain.util.CurrencyFormatter
import org.dpdns.alwaysup.subflow.ui.screens.add.AddSubscriptionScreen
import org.dpdns.alwaysup.subflow.ui.theme.SubFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.dpdns.alwaysup.subflow.domain.util.DateCalculators
import java.time.LocalDate
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
    private var upgradeTaps = 0

    private fun show(
        existing: Subscription? = null,
        isPro: Boolean = false,
        budget: MonthlyBudget? = null,
        others: Double = 0.0
    ) = compose.setContent {
        SubFlowTheme {
            AddSubscriptionScreen(
                presets = emptyList(),
                primaryCurrency = "USD",
                isPro = isPro,
                existingSubscription = existing,
                monthlyBudget = budget,
                otherMonthlyTotal = others,
                onSaveSubscription = { saved = it },
                onCancel = {},
                onUpgradeClick = { upgradeTaps++ }
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

    // --------------------------------------------------------------- budget

    private fun usd(amount: Double) = CurrencyFormatter.format(amount, "USD", java.util.Locale.getDefault())

    @Test
    fun `before saving, it says the budget this would go over`() {
        show(isPro = true, budget = MonthlyBudget(100.0, "USD"), others = 90.0)
        field(1).performTextInput("15.49")
        compose.onNodeWithText(
            context.getString(R.string.budget_after_add_over, usd(5.49), usd(100.0))
        ).assertExists()
    }

    @Test
    fun `and what would be left when it fits`() {
        show(isPro = true, budget = MonthlyBudget(100.0, "USD"), others = 50.0)
        field(1).performTextInput("15.49")
        compose.onNodeWithText(
            context.getString(R.string.budget_after_add_left, usd(34.51), usd(100.0))
        ).assertExists()
    }

    @Test
    fun `a free user sees no budget line`() {
        show(isPro = false, budget = MonthlyBudget(100.0, "USD"), others = 90.0)
        field(1).performTextInput("15.49")
        compose.onNodeWithText(
            context.getString(R.string.budget_after_add_over, usd(5.49), usd(100.0))
        ).assertDoesNotExist()
    }

    // ------------------------------------------------ locked reminder chips

    @Test
    fun `a locked reminder chip first says the day it would remind on`() {
        show()
        compose.onNodeWithText(context.getString(R.string.reminder_lead_value, 3)).performClick()
        compose.waitForIdle()

        // A new subscription starts today, so the charge is a month out.
        val charge = DateCalculators.computeNextRenewalDate(LocalDate.now().toString(), BillingCycle.MONTHLY)
        val day = DateCalculators.reminderDate(charge, 3) ?: throw AssertionError("no reminder day")
        compose.onNodeWithText(
            context.getString(
                R.string.reminder_pro_preview,
                DateCalculators.formatMedium(day.toString(), java.util.Locale.getDefault()),
                3
            )
        ).assertExists()
        assertEquals("the paywall waits for a second tap", 0, upgradeTaps)

        compose.onNodeWithText(context.getString(R.string.unlock_with_pro)).performClick()
        assertEquals(1, upgradeTaps)
    }

    @Test
    fun `for a Pro user the same chip just picks the lead`() {
        show(isPro = true)
        compose.onNodeWithText(context.getString(R.string.reminder_lead_value, 3)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.unlock_with_pro)).assertDoesNotExist()
        assertEquals(0, upgradeTaps)
    }
}
