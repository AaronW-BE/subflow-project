package org.dpdns.alwaysup.subflow.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dpdns.alwaysup.subflow.R
import org.dpdns.alwaysup.subflow.domain.util.CsvImport
import org.dpdns.alwaysup.subflow.ui.components.PrimaryButton
import org.dpdns.alwaysup.subflow.ui.components.contentGutter
import java.util.Locale

/** What the import sheet is showing. */
sealed interface ImportSheetState {
    /** The file was read; [pausedIfImported] of [preview]'s new rows would come in paused. */
    data class Ready(val preview: CsvImport.Preview, val pausedIfImported: Int) : ImportSheetState

    /** The header lacks a column every row needs, so nothing could be read. */
    data class MissingColumns(val missing: Set<CsvImport.Field>) : ImportSheetState
}

/**
 * What a CSV import would do, before anything is written.
 *
 * A spreadsheet is someone's own work, and the worst thing an import can do
 * with it is half-succeed quietly. So the counts come first - new, already
 * here, unreadable - each unreadable row is named with its line and why, and
 * nothing is written until the user says so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPreviewSheet(
    state: ImportSheetState,
    freeLimit: Int,
    onImport: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = contentGutter(20.dp))
                .navigationBarsPadding()
        ) {
            Text(
                text = stringResource(R.string.import_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            when (state) {
                is ImportSheetState.Ready -> ReadyContent(state, freeLimit, onImport, onDismiss)
                is ImportSheetState.MissingColumns -> MissingColumnsContent(state, onDismiss)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ReadyContent(
    state: ImportSheetState.Ready,
    freeLimit: Int,
    onImport: () -> Unit,
    onDismiss: () -> Unit
) {
    val preview = state.preview
    val newCount = preview.toAdd.size

    // The counts and the unreadable rows scroll; the buttons do not. A long
    // list of bad rows must never push "Import" off the bottom of the sheet.
    Column(
        modifier = Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
    ) {
        if (newCount > 0) {
            CountLine(
                icon = Icons.Default.CheckCircle,
                tint = MaterialTheme.colorScheme.primary,
                text = pluralStringResource(R.plurals.import_new, newCount, newCount)
            )
        } else {
            CountLine(
                icon = Icons.Default.CheckCircle,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                text = stringResource(R.string.import_nothing_new)
            )
        }
        if (preview.duplicates.isNotEmpty()) {
            CountLine(
                icon = Icons.Default.SkipNext,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                text = pluralStringResource(
                    R.plurals.import_duplicates,
                    preview.duplicates.size,
                    preview.duplicates.size
                )
            )
        }
        if (preview.invalid.isNotEmpty()) {
            CountLine(
                icon = Icons.Default.ErrorOutline,
                tint = MaterialTheme.colorScheme.error,
                text = pluralStringResource(
                    R.plurals.import_invalid,
                    preview.invalid.size,
                    preview.invalid.size
                )
            )
            preview.invalid.forEach { row ->
                Text(
                    text = stringResource(R.string.import_line, row.line, problemText(row)),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 34.dp, bottom = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (state.pausedIfImported > 0) {
            CountLine(
                icon = Icons.Default.PauseCircle,
                tint = MaterialTheme.colorScheme.tertiary,
                text = stringResource(R.string.import_paused_note, state.pausedIfImported, freeLimit)
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    if (newCount > 0) {
        PrimaryButton(
            text = pluralStringResource(R.plurals.import_confirm, newCount, newCount),
            onClick = onImport
        )
        TextButton(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            Text(
                text = stringResource(R.string.cancel),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        PrimaryButton(text = stringResource(R.string.close), onClick = onDismiss)
    }
}

@Composable
private fun MissingColumnsContent(
    state: ImportSheetState.MissingColumns,
    onDismiss: () -> Unit
) {
    val missing = CsvImport.Field.entries
        .filter { it in state.missing }
        .map { fieldLabel(it) }
        .joinToString(", ")
    val template = listOf(
        CsvImport.Field.NAME,
        CsvImport.Field.AMOUNT,
        CsvImport.Field.CURRENCY,
        CsvImport.Field.CYCLE,
        CsvImport.Field.FIRST_BILL_DATE
    ).map { fieldLabel(it) }.joinToString(", ")

    CountLine(
        icon = Icons.Default.ErrorOutline,
        tint = MaterialTheme.colorScheme.error,
        text = stringResource(R.string.import_missing_columns, missing)
    )
    Text(
        text = stringResource(R.string.import_template_hint, template),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 34.dp)
    )
    Spacer(modifier = Modifier.height(20.dp))
    PrimaryButton(text = stringResource(R.string.close), onClick = onDismiss)
}

@Composable
private fun CountLine(icon: ImageVector, tint: Color, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun problemText(row: CsvImport.Invalid): String = when (row.problem) {
    CsvImport.Problem.MISSING_NAME -> stringResource(R.string.import_problem_missing_name)
    CsvImport.Problem.BAD_AMOUNT -> stringResource(R.string.import_problem_bad_amount, row.value)
    CsvImport.Problem.BAD_CURRENCY -> stringResource(R.string.import_problem_bad_currency, row.value)
    CsvImport.Problem.BAD_CYCLE -> stringResource(R.string.import_problem_bad_cycle, row.value)
    CsvImport.Problem.BAD_DATE -> stringResource(R.string.import_problem_bad_date, row.value)
}

/**
 * A column's name as the add form labels the same field. Those labels are
 * also accepted as headers, so what the sheet asks for is what will be read.
 */
@Composable
private fun fieldLabel(field: CsvImport.Field): String {
    val locale = LocalConfiguration.current.locales.get(0) ?: Locale.getDefault()
    return when (field) {
        CsvImport.Field.NAME -> stringResource(R.string.field_name)
        CsvImport.Field.AMOUNT -> stringResource(R.string.field_price)
        CsvImport.Field.CURRENCY -> stringResource(R.string.field_currency)
        // A section header elsewhere, so it is upper case; here it is a name.
        CsvImport.Field.CYCLE -> stringResource(R.string.billing_cycle)
            .lowercase(locale)
            .replaceFirstChar { it.titlecase(locale) }
        CsvImport.Field.FIRST_BILL_DATE -> stringResource(R.string.first_bill_date)
        CsvImport.Field.CATEGORY -> stringResource(R.string.field_category)
        CsvImport.Field.NOTES -> stringResource(R.string.field_notes)
        CsvImport.Field.REMINDER -> "ReminderDaysBefore"
    }
}
