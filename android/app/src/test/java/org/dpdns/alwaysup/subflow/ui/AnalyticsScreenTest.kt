package org.dpdns.alwaysup.subflow.ui

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
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
import org.robolectric.annotation.GraphicsMode
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
        compose.onAllNodesWithText(str(R.string.overview_partial)).assertCountEquals(3)
        compose.onNodeWithText("Category Known").assertIsDisplayed()
        compose.onNode(hasText("Category Unknown") and hasText(str(R.string.overview_unavailable)) and
            hasText(str(R.string.overview_partial))).assertIsDisplayed()
        compose.onNodeWithText("Known", substring = false).assertDoesNotExist()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.metric_services, 2, 2)).assertIsDisplayed()
        compose.onAllNodesWithText(str(R.string.analytics_coverage_unavailable)).assertCountEquals(5)
    }
    @Test
    @Config(qualifiers = "w360dp-h4000dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun unavailableTopMetricWrapsWithoutClippingOnNarrowScreen() {
        show(listOf(sub("Known"), sub("Unknown", "XXX")), pro = false)
        val unavailableText = str(R.string.overview_unavailable)
        val topMetric = hasParent(hasAnyChild(hasText(str(R.string.metric_top_share))))
        val node = compose.onNode(hasText(unavailableText) and topMetric, useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.runOnIdle {
            check(node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts))
        }
        org.junit.Assert.assertEquals(1, layouts.size)
        val layout = layouts.single()
        org.junit.Assert.assertEquals(unavailableText, layout.layoutInput.text.text)
        org.junit.Assert.assertFalse("Unavailable top metric must not overflow visually", layout.hasVisualOverflow)
        org.junit.Assert.assertFalse("Unavailable top metric must not clip lines", layout.didOverflowHeight)
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line, visibleEnd = true)
            val next = layout.getLineStart(line + 1)
            val splitsWord = end == next && end > 0 && next < unavailableText.length &&
                unavailableText[end - 1].isLetter() && unavailableText[next].isLetter()
            org.junit.Assert.assertFalse("Unavailable status must wrap between words", splitsWord)
        }
    }
    @Test fun mixedCategoryKeepsKnownSubtotalWithoutPercentagesOrRanking() {
        show(listOf(sub("Known").copy(category = "Mixed"), sub("Unknown", "XXX").copy(category = "Mixed")))
        compose.onNodeWithText("Mixed").assertIsDisplayed()
        val row = hasText("Mixed")
        val amount = CurrencyFormatter.format(12.0, "USD", context.resources.configuration.locales[0])
        compose.onNode(row and hasText(amount) and hasText(str(R.string.overview_partial))).assertIsDisplayed()
        compose.onAllNodes(hasText("%", substring = true) and (row or hasAnyAncestor(row))).assertCountEquals(0)
        compose.onAllNodesWithText("%", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Known", substring = false).assertDoesNotExist()
        compose.onNodeWithText("Unknown", substring = false).assertDoesNotExist()
        compose.onAllNodesWithText(str(R.string.analytics_coverage_unavailable)).assertCountEquals(5)
    }
    @Test fun unavailableCategoryAndCompleteFreeZeroRemainVisible() {
        show(listOf(sub("Missing", "XXX").copy(category = "Unavailable category"),
            sub("Free", "XXX").copy(category = "Free category", amount = 0.0)))
        compose.onNodeWithText("Unavailable category").assertIsDisplayed()
        val unavailable = hasText("Unavailable category")
        compose.onNode(unavailable and hasText(str(R.string.overview_unavailable)) and
            hasText(str(R.string.overview_partial))).assertIsDisplayed()
        val zero = CurrencyFormatter.format(0.0, "USD", context.resources.configuration.locales[0])
        compose.onNode(unavailable and hasText(zero)).assertDoesNotExist()
        val free = hasText("Free category")
        compose.onNode(free and hasText(zero)).assertIsDisplayed()
        compose.onNode(free and hasText(str(R.string.overview_partial))).assertDoesNotExist()
        compose.onAllNodesWithText("%", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Missing", substring = false).assertDoesNotExist()
    }
    @Test fun rateOnlyUpdateRestoresRankingsAndEveryAffectedSeriesUsingExplicitHomeCurrency() {
        var snapshot by mutableStateOf(base)
        show(listOf(sub("Known"), sub("Unknown", "XXX")), snapshot = { snapshot })
        compose.onAllNodesWithText(str(R.string.overview_partial)).assertCountEquals(3)
        compose.runOnIdle { snapshot = base.copy(rates = RatesSnapshot(2, mapOf("USD" to 1.0, "XXX" to 2.0))) }
        compose.onAllNodesWithText(str(R.string.overview_partial)).assertCountEquals(0)
        compose.onAllNodesWithText(str(R.string.analytics_coverage_unavailable)).assertCountEquals(0)
        compose.onAllNodesWithText("Known", substring = false)[0].assertIsDisplayed()
        compose.onNodeWithText("Unknown", substring = false).assertIsDisplayed()
    }
    @Test fun currentMissingDoesNotBlankCompleteForecastOrReconstructedHistory() {
        show(listOf(sub("Future", "XXX").copy(firstBillDate = "2027-09-01")))
        compose.onAllNodesWithText(str(R.string.overview_unavailable)).assertCountEquals(4)
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
