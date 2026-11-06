package org.dpdns.alwaysup.subflow.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.dpdns.alwaysup.subflow.R
import org.dpdns.alwaysup.subflow.domain.util.*
import org.dpdns.alwaysup.subflow.ui.navigation.LocalCalculationSnapshot
import org.dpdns.alwaysup.subflow.ui.screens.settings.BudgetDialog
import org.dpdns.alwaysup.subflow.ui.theme.SubFlowTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h2400dp")
class BudgetDialogTest {
    // Keep the real editor state, validation and callbacks; only host the slots inline.
    // Robolectric's separate AlertDialog window repeatedly requests layout and never idles.
    private val inlineFrame: @Composable (@Composable () -> Unit, @Composable () -> Unit, @Composable () -> Unit, @Composable () -> Unit) -> Unit = { title, text, confirm, dismiss ->
        Column { title(); text(); confirm(); dismiss() }
    }
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun `original JPY draft survives rate and home currency changes and saves original currency`() {
        val home = mutableStateOf("USD")
        val rates = mutableStateOf(RatesSnapshot(1, emptyMap()))
        var saved: MonthlyBudget? = null
        compose.setContent {
            CompositionLocalProvider(LocalCalculationSnapshot provides CalculationSnapshot(home.value, LocalDate.of(2026, 2, 1), rates.value)) {
                SubFlowTheme { Box(Modifier.fillMaxSize()) {} ; BudgetDialog(MonthlyBudget(1234.0, "JPY"), home.value, { saved = it }, {}, frame = inlineFrame) }
            }
        }
        compose.onNodeWithText(context.getString(R.string.budget_original_currency, "JPY")).assertExists()
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("2345")
        compose.runOnIdle { rates.value = RatesSnapshot(2, mapOf("USD" to 1.0, "JPY" to 150.0)); home.value = "EUR" }
        compose.onNode(hasSetTextAction()).assertTextContains("2345")
        compose.onNodeWithText(context.getString(R.string.monthly_budget_hint, "JPY")).assertExists()
        compose.onNodeWithText(context.getString(R.string.save)).performClick()
        assertEquals(MonthlyBudget(2345.0, "JPY"), saved)
    }

    @Test fun `unavailable original KRW rejects fractional input and removal remains possible`() {
        var removed = false
        compose.setContent { SubFlowTheme { Box(Modifier.fillMaxSize()) {} ; BudgetDialog(MonthlyBudget(2000.0, "KRW"), "XXX", { removed = it == null }, {}, frame = inlineFrame) } }
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("2.5")
        compose.onNodeWithText(context.getString(R.string.save)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.budget_remove)).performClick()
        assertTrue(removed)
    }

    @Test fun `convertible original budget edits home currency frozen at opening`() {
        var saved: MonthlyBudget? = null
        compose.setContent {
            CompositionLocalProvider(LocalCalculationSnapshot provides CalculationSnapshot("USD", LocalDate.of(2026, 2, 1), RatesSnapshot(1, mapOf("USD" to 1.0, "EUR" to 0.8)))) {
                SubFlowTheme { Box(Modifier.fillMaxSize()) {} ; BudgetDialog(MonthlyBudget(80.0, "EUR"), "USD", { saved = it }, {}, frame = inlineFrame) }
            }
        }
        compose.onNode(hasSetTextAction()).assertTextContains("100")
        compose.onNodeWithText(context.getString(R.string.save)).performClick()
        assertEquals(MonthlyBudget(100.0, "USD"), saved)
    }
}
