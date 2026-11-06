package org.dpdns.alwaysup.subflow.ui

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.dpdns.alwaysup.subflow.R
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.util.*
import org.dpdns.alwaysup.subflow.ui.navigation.LocalCalculationSnapshot
import org.dpdns.alwaysup.subflow.ui.screens.analytics.AnalyticsScreen
import org.dpdns.alwaysup.subflow.ui.theme.SubFlowTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w450dp-h4000dp")
class AnalyticsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun str(key: Int) = context.getString(key)
    private val base = CalculationSnapshot("EUR", LocalDate.of(2026, 9, 18), RatesSnapshot(1, mapOf("USD" to 1.0)))
    private fun sub(id: String, currency: String = "USD") = Subscription(id = id, name = id,
        category = "Category $id", amount = 12.0, currency = currency,
        firstBillDate = "2026-01-01", nextBillDate = "2026-09-01")
    private fun show(subs: List<Subscription>, pro: Boolean = true, snapshot: () -> CalculationSnapshot = { base }) {
        compose.setContent {
            CompositionLocalProvider(LocalCalculationSnapshot provides snapshot()) {
                SubFlowTheme { AnalyticsScreen(subs, pro, primaryCurrency = "USD", onPaywallClick = {}, onExportCsv = {}) }
            }
        }
    }
    @Test fun partialCostsSuspendComparisonsButKeepCountsKnownCategoryAndConvertedPortion() {
        show(listOf(sub("Known"), sub("Unknown", "XXX")))
        compose.onAllNodesWithText(str(R.string.overview_partial)).assertCountEquals(2)
        compose.onNodeWithText("Category Known").assertIsDisplayed()
        compose.onNodeWithText("Category Unknown").assertDoesNotExist()
        compose.onNodeWithText("Known", substring = false).assertDoesNotExist()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.metric_services, 2, 2)).assertIsDisplayed()
        compose.onAllNodesWithText(str(R.string.analytics_coverage_unavailable)).assertCountEquals(5)
    }
    @Test fun rateOnlyUpdateRestoresRankingsAndEveryAffectedSeriesUsingExplicitHomeCurrency() {
        var snapshot by mutableStateOf(base)
        show(listOf(sub("Known"), sub("Unknown", "XXX")), snapshot = { snapshot })
        compose.onAllNodesWithText(str(R.string.overview_partial)).assertCountEquals(2)
        compose.runOnIdle { snapshot = base.copy(rates = RatesSnapshot(2, mapOf("USD" to 1.0, "XXX" to 2.0))) }
        compose.onAllNodesWithText(str(R.string.overview_partial)).assertCountEquals(0)
        compose.onAllNodesWithText(str(R.string.analytics_coverage_unavailable)).assertCountEquals(0)
        compose.onAllNodesWithText("Known", substring = false)[0].assertIsDisplayed()
        compose.onNodeWithText("Unknown", substring = false).assertIsDisplayed()
    }
    @Test fun currentMissingDoesNotBlankCompleteForecastOrReconstructedHistory() {
        show(listOf(sub("Future", "XXX").copy(firstBillDate = "2027-09-01")))
        compose.onAllNodesWithText(str(R.string.overview_unavailable)).assertCountEquals(3)
        compose.onNodeWithText(str(R.string.billing_forecast_caption)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.forecast_average_line)).assertDoesNotExist()
        compose.onNodeWithText(str(R.string.history_caption)).assertIsDisplayed()
        compose.onAllNodesWithText(str(R.string.analytics_coverage_unavailable)).assertCountEquals(3)
    }
    @Test fun trialFutureMissingKeepsCompleteCurrentAndProGate() {
        show(listOf(sub("Trial", "XXX").copy(isTrial = true, trialEndDate = "2026-10-01", trialConverts = true, postTrialAmount = 20.0)), pro = false)
        compose.onAllNodesWithText(str(R.string.overview_partial)).assertCountEquals(0)
        compose.onNodeWithText(str(R.string.pro_locked_analytics)).assertIsDisplayed()
        compose.onAllNodesWithText(str(R.string.unlock_with_pro)).assertCountEquals(4)
        compose.onAllNodesWithText(str(R.string.analytics_coverage_unavailable)).assertCountEquals(1)
    }
}
