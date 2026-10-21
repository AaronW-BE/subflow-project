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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import org.dpdns.alwaysup.subflow.R
import org.dpdns.alwaysup.subflow.data.repository.ActiveChange
import org.dpdns.alwaysup.subflow.data.repository.BulkActiveChange
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.util.MonthlyBudget
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
        budget: MonthlyBudget? = null
    ) =
        compose.setContent {
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
}
