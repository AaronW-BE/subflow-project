package org.dpdns.alwaysup.subflow.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.dpdns.alwaysup.subflow.R
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.util.CsvImport
import org.dpdns.alwaysup.subflow.ui.screens.settings.ImportPreviewSheet
import org.dpdns.alwaysup.subflow.ui.screens.settings.ImportSheetState
import org.dpdns.alwaysup.subflow.ui.theme.SubFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The sheet a CSV import shows before it writes anything (BIN-65).
 *
 * Its whole job is to say plainly what will happen, so the tests read it the
 * way a person would: the counts, the reasons, the button.
 */
@RunWith(RobolectricTestRunner::class)
class ImportPreviewSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun plural(id: Int, n: Int) = context.resources.getQuantityString(id, n, n)

    private var imported = 0
    private var dismissed = 0

    private fun sub(name: String) = Subscription(
        id = name, name = name, amount = 9.99, firstBillDate = "2026-01-01", nextBillDate = "2026-11-01"
    )

    private fun show(state: ImportSheetState) = compose.setContent {
        SubFlowTheme {
            ImportPreviewSheet(
                state = state,
                freeLimit = 5,
                onImport = { imported++ },
                onDismiss = { dismissed++ }
            )
        }
    }

    @Test
    fun `it counts what is new, what is already here and what could not be read`() {
        show(
            ImportSheetState.Ready(
                CsvImport.Preview(
                    toAdd = listOf(sub("Disney+"), sub("Duolingo")),
                    duplicates = listOf(CsvImport.Parsed(2, sub("Netflix"))),
                    invalid = listOf(CsvImport.Invalid(7, CsvImport.Problem.BAD_AMOUNT, "abc"))
                ),
                pausedIfImported = 1
            )
        )
        compose.onNodeWithText(plural(R.plurals.import_new, 2)).assertIsDisplayed()
        compose.onNodeWithText(plural(R.plurals.import_duplicates, 1)).assertIsDisplayed()
        compose.onNodeWithText(plural(R.plurals.import_invalid, 1)).assertIsDisplayed()
        compose.onNodeWithText(
            context.getString(
                R.string.import_line,
                7,
                context.getString(R.string.import_problem_bad_amount, "abc")
            )
        ).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.import_paused_note, 1, 5)).assertIsDisplayed()
    }

    @Test
    fun `import writes only when asked`() {
        show(
            ImportSheetState.Ready(
                CsvImport.Preview(listOf(sub("Disney+")), emptyList(), emptyList()),
                pausedIfImported = 0
            )
        )
        assertEquals(0, imported)
        compose.onNodeWithText(plural(R.plurals.import_confirm, 1)).performClick()
        assertEquals(1, imported)
    }

    @Test
    fun `with nothing new there is nothing to import, only a way out`() {
        show(
            ImportSheetState.Ready(
                CsvImport.Preview(emptyList(), listOf(CsvImport.Parsed(2, sub("Netflix"))), emptyList()),
                pausedIfImported = 0
            )
        )
        compose.onNodeWithText(context.getString(R.string.import_nothing_new)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.close)).performClick()
        assertEquals(0, imported)
        assertEquals(1, dismissed)
    }

    @Test
    fun `a file without the columns it needs is told which ones`() {
        show(ImportSheetState.MissingColumns(setOf(CsvImport.Field.FIRST_BILL_DATE)))
        compose.onNodeWithText(
            context.getString(R.string.import_missing_columns, context.getString(R.string.first_bill_date))
        ).assertIsDisplayed()
    }
}
