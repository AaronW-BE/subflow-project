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
import org.dpdns.alwaysup.subflow.domain.model.PresetService
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
        others: Double = 0.0,
        otherCurrency: String = "USD"
    ) = compose.setContent {
        SubFlowTheme {
            AddSubscriptionScreen(
                presets = emptyList(),
                primaryCurrency = "USD",
                isPro = isPro,
                existingSubscription = existing,
                monthlyBudget = budget,
                budgetSubscriptions = listOf(org.dpdns.alwaysup.subflow.domain.model.Subscription(id = "other", name = "Other", amount = others, currency = otherCurrency, firstBillDate = "2026-01-01", nextBillDate = "2026-03-01")),
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
    fun `built-in service is visible when picker opens`() {
        val preset = PresetService(
            id = "netflix",
            name = "Netflix",
            category = "Streaming",
            brandColor = "#E50914",
            iconUrl = "https://netflix.com/logo.png",
            defaultAmountUSD = 15.49
        )
        compose.setContent {
            SubFlowTheme {
                AddSubscriptionScreen(
                    presets = listOf(preset),
                    primaryCurrency = "USD",
                    onSaveSubscription = { saved = it },
                    onCancel = {}
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.choose_service)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Netflix").assertExists()
        compose.onNodeWithText(context.getString(R.string.custom_service)).assertExists()
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

    @Test
    fun `basic trial edit preserves keep decision and advance reminder`() {
        val existing = Subscription(
            id = "kept_trial", name = "Trial", amount = 0.0,
            firstBillDate = "2099-06-01", nextBillDate = "2099-06-18",
            isTrial = true, trialEndDate = "2099-06-18",
            trialKeepEndDate = "2099-06-18", reminderDaysBefore = 3, postTrialAmount = 9.99
        )
        show(existing)
        field(0).performTextClearance()
        field(0).performTextInput("Renamed trial")
        save()
        val result = saved ?: throw AssertionError("nothing was saved")
        assertEquals("Renamed trial", result.name)
        assertEquals(existing.trialKeepEndDate, result.trialKeepEndDate)
        assertTrue(result.isTrialPending)
        assertEquals(0.0, result.amount, 0.0)
        assertEquals(existing.reminderDaysBefore, result.reminderDaysBefore)
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

    @Test
    fun `missing existing coverage shows unavailable and still saves`() {
        show(isPro = true, budget = MonthlyBudget(100.0, "USD"), others = 10.0, otherCurrency = "XXX")
        field(0).performTextInput("New")
        field(1).performTextInput("15.49")
        compose.onNodeWithText(context.getString(R.string.overview_budget_unavailable)).assertExists()
        compose.onNodeWithText(context.getString(R.string.budget_after_add_left, usd(74.51), usd(100.0))).assertDoesNotExist()
        save()
        assertEquals(15.49, requireNotNull(saved).amount, 0.0)
    }

    @Test
    fun `repairing missing edited cost cannot claim a complete transition`() {
        val existing = Subscription(id = "edit", name = "Unknown", amount = 10.0, currency = "XXX", firstBillDate = "2026-01-01", nextBillDate = "2026-03-01")
        compose.setContent { SubFlowTheme { AddSubscriptionScreen(presets = emptyList(), primaryCurrency = "USD", isPro = true,
            existingSubscription = existing, monthlyBudget = MonthlyBudget(100.0, "USD"), budgetSubscriptions = listOf(existing),
            onSaveSubscription = { saved = it }, onCancel = {}) } }
        field(1).performTextClearance()
        field(1).performTextInput("0")
        compose.onNodeWithText(context.getString(R.string.overview_budget_unavailable)).assertExists()
    }

    @Test
    fun `editing paused subscription preserves paused state and current zero budget contribution`() {
        val paused = Subscription(id = "paused", name = "Paused", amount = 10.0, currency = "XXX", firstBillDate = "2026-01-01", nextBillDate = "2026-03-01", isActive = false)
        compose.setContent { SubFlowTheme { AddSubscriptionScreen(presets = emptyList(), primaryCurrency = "USD", isPro = true,
            existingSubscription = paused, monthlyBudget = MonthlyBudget(100.0, "USD"), budgetSubscriptions = listOf(paused),
            onSaveSubscription = { saved = it }, onCancel = {}) } }
        field(1).performTextClearance()
        field(1).performTextInput("20")
        compose.onNodeWithText(context.getString(R.string.budget_after_add_left, usd(100.0), usd(100.0))).assertExists()
        save()
        assertTrue(!requireNotNull(saved).isActive)
        assertEquals(20.0, requireNotNull(saved).amount, 0.0)
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
