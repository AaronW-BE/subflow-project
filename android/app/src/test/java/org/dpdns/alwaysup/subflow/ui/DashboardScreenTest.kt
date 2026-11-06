package org.dpdns.alwaysup.subflow.ui

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.runtime.CompositionLocalProvider
import org.dpdns.alwaysup.subflow.ui.navigation.LocalCalculationSnapshot
import org.dpdns.alwaysup.subflow.domain.util.*
import java.time.LocalDate
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import org.dpdns.alwaysup.subflow.R
import org.dpdns.alwaysup.subflow.data.repository.ActiveChange
import org.dpdns.alwaysup.subflow.data.repository.BulkActiveChange
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.util.MonthlyBudget
import org.dpdns.alwaysup.subflow.domain.util.joinSentences
import org.dpdns.alwaysup.subflow.domain.util.CurrencyFormatter
import org.dpdns.alwaysup.subflow.ui.screens.dashboard.DashboardScreen
import org.dpdns.alwaysup.subflow.ui.theme.SubFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The home screen's ways of changing a subscription: the row's own actions
 * with their undo, and multi-select (BIN-66).
 *
 * Rows are driven through their accessibility actions - the same Pause and
 * Delete a screen reader offers - rather than by synthesising a swipe, which
 * is what a swipe ends up calling anyway and does not depend on how far a
 * simulated finger travels.
 */
@RunWith(RobolectricTestRunner::class)
// Tall enough that every row is laid out: a lazy list only composes what is
// on screen, and the default test screen ends before the second row.
@Config(qualifiers = "w400dp-h2400dp")
class DashboardScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)
    private fun plural(id: Int, n: Int) = context.resources.getQuantityString(id, n, n)

    private val netflix = sub("netflix", "Netflix", 15.49)
    private val spotify = sub("spotify", "Spotify", 11.99)
    private val icloud = sub("icloud", "iCloud+", 2.99)

    private val deleted = mutableListOf<String>()
    private val restored = mutableListOf<String>()
    private val setActive = mutableListOf<Pair<String, Boolean>>()
    private val reverted = mutableListOf<Subscription>()
    private val bulkActive = mutableListOf<Pair<List<String>, Boolean>>()

    private fun sub(id: String, name: String, amount: Double) = Subscription(
        id = id,
        name = name,
        category = "Streaming",
        amount = amount,
        currency = "USD",
        firstBillDate = "2026-01-10",
        nextBillDate = "2099-01-10"
    )

    private fun show(
        subscriptions: List<Subscription> = listOf(netflix, spotify, icloud),
        budget: MonthlyBudget? = null,
        snapshot: () -> CalculationSnapshot = { CalculationSnapshot("USD", LocalDate.of(2026, 2, 1), RatesSnapshot(1, mapOf("USD" to 1.0))) }
    ) =
        compose.setContent {
            CompositionLocalProvider(LocalCalculationSnapshot provides snapshot()) {
            SubFlowTheme {
                DashboardScreen(
                    subscriptions = subscriptions,
                    monthlyBudget = budget,
                    // Pro, so no ad banner: the tests are about the list.
                    isPro = true,
                    isLoading = false,
                    onAddClick = {},
                    onSubscriptionClick = {},
                    onDeleteSubscription = { deleted += it },
                    onRestoreSubscription = { restored += it },
                    onSetActive = { id, active ->
                        setActive += id to active
                        val before = subscriptions.first { it.id == id }
                        ActiveChange(before, before.copy(isActive = active))
                    },
                    onRevertSubscription = { reverted += it },
                    onSetActiveMany = { ids, active ->
                        bulkActive += ids to active
                        BulkActiveChange(
                            subscriptions.filter { it.id in ids }
                                .map { ActiveChange(it, it.copy(isActive = active)) },
                            0
                        )
                    },
                    onPaywallClick = {}
                )
            }
            }
        }

    @Test
    fun `count navigation clears filters and reset returns collapsed ordinary home`() {
        show(listOf(netflix, spotify.copy(isActive = false)))
        compose.onNode(hasSetTextAction()).performTextInput("nothing matches")
        row("Netflix").assertDoesNotExist()
        compose.onNodeWithText("1\n" + str(R.string.overview_paused)).performClick()
        row("Spotify").assertIsDisplayed()
        row("Netflix").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput("missing")
        row("Spotify").assertDoesNotExist()
        compose.onNodeWithText(str(R.string.overview_reset)).performClick()
        row("Netflix").assertIsDisplayed()
        row("Spotify").assertDoesNotExist()
        compose.onNodeWithText("2\n" + str(R.string.overview_all)).performClick()
        row("Spotify").assertIsDisplayed()
    }

    @Test
    fun `all and paused subsets expand initially and keep the paused toggle usable`() {
        show(listOf(netflix, spotify.copy(isActive = false)))
        compose.onNodeWithText("2\n" + str(R.string.overview_all)).performClick()
        row("Spotify").assertIsDisplayed()
        compose.onNodeWithText(str(R.string.dashboard_paused_header, 1)).performClick()
        row("Spotify").assertDoesNotExist()
        compose.onNodeWithText(str(R.string.dashboard_paused_header, 1)).performClick()
        row("Spotify").assertIsDisplayed()
        compose.onNodeWithText("1\n" + str(R.string.overview_paused)).performClick()
        row("Spotify").assertIsDisplayed()
        compose.onNodeWithText(str(R.string.dashboard_paused_header, 1)).performClick()
        row("Spotify").assertDoesNotExist()
        compose.onNodeWithText(str(R.string.overview_reset)).assertIsDisplayed()
    }

    @Test
    fun `paused only retains search and empty active subset has exit`() {
        show(listOf(spotify.copy(isActive = false)))
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        compose.onNodeWithText("0\n" + str(R.string.overview_active)).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("missing")
        compose.onNodeWithText(str(R.string.overview_reset)).assertIsDisplayed().performClick()
        compose.onNodeWithText("1\n" + str(R.string.overview_paused)).performClick()
        row("Spotify").assertIsDisplayed()
    }

    @Test
    fun `positive missing rate is unknown not zero and suspends budget`() {
        show(listOf(netflix.copy(currency = "ZZZ")), MonthlyBudget(100.0, "USD"))
        compose.onNodeWithText(str(R.string.overview_unavailable)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.overview_partial)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.overview_budget_unavailable)).assertIsDisplayed()
    }

    @Test
    fun `rate only snapshot publication replaces unknown estimate without changing inventory`() {
        val state = androidx.compose.runtime.mutableStateOf(CalculationSnapshot("USD", LocalDate.of(2026, 2, 1), RatesSnapshot(1, mapOf("USD" to 1.0))))
        show(listOf(netflix.copy(currency = "EUR")), snapshot = { state.value })
        compose.onNodeWithText(str(R.string.overview_unavailable)).assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(rates = RatesSnapshot(2, mapOf("USD" to 1.0, "EUR" to 2.0))) }
        compose.onNodeWithText(str(R.string.overview_unavailable)).assertDoesNotExist()
        compose.onNodeWithText(str(R.string.overview_normalized)).assertIsDisplayed()
        compose.onNodeWithText("1" + 10.toChar() + str(R.string.overview_all)).assertIsDisplayed()
    }

    @Test
    fun `pending subset includes kept expired trials but excludes paused trials and preserves totals through search`() {
        val trial = netflix.copy(amount = 0.0, isTrial = true, trialEndDate = "2026-01-01", trialKeepEndDate = "2026-01-01")
        show(listOf(trial, spotify.copy(isTrial = true, isActive = false), icloud))
        compose.onNodeWithText(str(R.string.overview_pending, 1)).performClick()
        row("Netflix").assertIsDisplayed()
        row("Spotify").assertDoesNotExist()
        row("iCloud+").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput("missing")
        row("Netflix").assertDoesNotExist()
        compose.onNodeWithText("3" + 10.toChar() + str(R.string.overview_all)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.overview_reset)).performClick()
        row("iCloud+").assertIsDisplayed()
    }

    @Test
    fun `count clears category and later category narrows within paused subset`() {
        show(listOf(netflix, spotify.copy(isActive = false, category = "Productivity")))
        compose.onAllNodesWithText(str(R.string.filter_streaming))[0].performClick()
        compose.onNodeWithText("1" + 10.toChar() + str(R.string.overview_paused)).performClick()
        row("Spotify").assertIsDisplayed()
        compose.onAllNodesWithText(str(R.string.filter_streaming))[0].performClick()
        row("Spotify").assertDoesNotExist()
        compose.onNodeWithText(str(R.string.overview_reset)).assertIsDisplayed()
    }

    @Test
    fun `known partial amount remains global through zero match search`() {
        show(listOf(netflix.copy(amount = 5.0), spotify.copy(currency = "ZZZ")))
        compose.onNodeWithText(str(R.string.overview_partial)).assertIsDisplayed()
        compose.onAllNodesWithText(usd(5.0))[0].assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("missing")
        compose.onAllNodesWithText(usd(5.0))[0].assertIsDisplayed()
        compose.onNodeWithText("2" + 10.toChar() + str(R.string.overview_active)).assertIsDisplayed()
    }

    @Test
    fun `complete free cost remains explicit zero`() {
        show(listOf(netflix.copy(amount = 0.0, currency = "ZZZ")))
        compose.onNodeWithText(str(R.string.overview_normalized)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.overview_unavailable)).assertDoesNotExist()
    }

    /** The card for [name]: the clickable node whose description starts with it. */
    private fun row(name: String): SemanticsNodeInteraction =
        compose.onNode(hasContentDescription(name, substring = true) and hasClickAction())

    private fun SemanticsNodeInteraction.customAction(label: String) {
        val actions = fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        val action = actions.firstOrNull { it.label == label }
            ?: throw AssertionError("No action '$label' among ${actions.map { it.label }}")
        compose.runOnIdle { action.action() }
        compose.waitForIdle()
    }

    private fun clickUndo() {
        compose.onNodeWithText(str(R.string.undo)).performClick()
        compose.waitForIdle()
    }

    // ------------------------------------------------------------ one row

    @Test
    fun `deleting a row says so and undo brings it back`() {
        show()
        row("Netflix").customAction(str(R.string.delete_action))

        assertEquals(listOf("netflix"), deleted)
        compose.onNodeWithText(str(R.string.deleted_item, "Netflix")).assertIsDisplayed()

        clickUndo()
        assertEquals(listOf("netflix"), restored)
    }

    @Test
    fun `pausing a row says so and undo writes the old record back`() {
        show()
        row("Spotify").customAction(str(R.string.pause_action))

        assertEquals(listOf("spotify" to false), setActive)
        compose.onNodeWithText(str(R.string.paused_item, "Spotify")).assertIsDisplayed()

        clickUndo()
        // The whole record as it was, not the flag flipped back.
        assertEquals(listOf(spotify), reverted)
    }

    // ------------------------------------------------------- multi-select

    @Test
    fun `a long press starts multi-select and taps add to it`() {
        show()
        row("Netflix").performTouchInput { longClick() }
        compose.onNodeWithText(plural(R.plurals.selection_count, 1)).assertIsDisplayed()

        row("Spotify").performClick()
        compose.onNodeWithText(plural(R.plurals.selection_count, 2)).assertIsDisplayed()

        // A second tap on a chosen row lets it go again.
        row("Spotify").performClick()
        compose.onNodeWithText(plural(R.plurals.selection_count, 1)).assertIsDisplayed()
    }

    @Test
    fun `the row's own swipe actions are gone while choosing`() {
        show()
        row("Netflix").performTouchInput { longClick() }
        val actions = row("iCloud+").fetchSemanticsNode().config
            .getOrNull(SemanticsActions.CustomActions).orEmpty()
        assertTrue("expected no row actions, got ${actions.map { it.label }}", actions.isEmpty())
    }

    @Test
    fun `pausing the chosen rows pauses them in the order they were chosen`() {
        show()
        row("iCloud+").performTouchInput { longClick() }
        row("Netflix").performClick()
        compose.onNodeWithContentDescription(str(R.string.pause_action)).performClick()
        compose.waitForIdle()

        assertEquals(listOf(listOf("icloud", "netflix") to false), bulkActive)
        compose.onNodeWithText(plural(R.plurals.bulk_paused, 2)).assertIsDisplayed()

        clickUndo()
        assertEquals(setOf(icloud, netflix), reverted.toSet())
    }

    @Test
    fun `deleting the chosen rows has one undo for all of them`() {
        show()
        row("Netflix").performTouchInput { longClick() }
        row("Spotify").performClick()
        compose.onNodeWithContentDescription(str(R.string.delete_action)).performClick()
        compose.waitForIdle()

        assertEquals(listOf("netflix", "spotify"), deleted)
        compose.onNodeWithText(plural(R.plurals.bulk_deleted, 2)).assertIsDisplayed()

        clickUndo()
        assertEquals(listOf("netflix", "spotify"), restored)
    }

    @Test
    fun `the close button leaves multi-select without changing anything`() {
        show()
        row("Netflix").performTouchInput { longClick() }
        compose.onNodeWithContentDescription(str(R.string.exit_selection)).performClick()
        compose.waitForIdle()

        compose.onNode(
            SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.Text,
                listOf(androidx.compose.ui.text.AnnotatedString(plural(R.plurals.selection_count, 1)))
            )
        ).assertDoesNotExist()
        assertTrue(deleted.isEmpty() && bulkActive.isEmpty())
    }

    // --------------------------------------------------------------- budget

    private fun usd(amount: Double) = CurrencyFormatter.format(amount, "USD", java.util.Locale.getDefault())

    @Test
    fun `a budget shows what is left of it`() {
        // 15.49 + 11.99 + 2.99 = 30.47 a month against 100.
        show(budget = MonthlyBudget(100.0, "USD"))
        compose.onNodeWithText(str(R.string.budget_left, usd(69.53), usd(100.0))).assertIsDisplayed()
    }

    @Test
    fun `over budget says by how much`() {
        show(budget = MonthlyBudget(20.0, "USD"))
        compose.onNodeWithText(str(R.string.budget_over, usd(10.47), usd(20.0))).assertIsDisplayed()
    }

    @Test
    fun `resuming over the budget says so alongside the undo`() {
        // Running: 15.49 + 11.99 = 27.48 against 30; resuming iCloud+ adds 2.99.
        show(
            subscriptions = listOf(netflix, spotify, icloud.copy(isActive = false)),
            budget = MonthlyBudget(30.0, "USD")
        )
        compose.onNodeWithText(context.resources.getString(R.string.dashboard_paused_header, 1)).performClick()
        compose.waitForIdle()
        row("iCloud+").customAction(str(R.string.resume_action))

        val expected = joinSentences(
            str(R.string.resumed_item, "iCloud+"),
            str(R.string.budget_now_over, usd(0.47), usd(30.0)),
            java.util.Locale.getDefault()
        )
        compose.onNodeWithText(expected).assertIsDisplayed()
    }
}
