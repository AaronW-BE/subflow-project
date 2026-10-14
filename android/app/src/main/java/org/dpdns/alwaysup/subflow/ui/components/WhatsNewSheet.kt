package org.dpdns.alwaysup.subflow.ui.components

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dpdns.alwaysup.subflow.BuildConfig
import org.dpdns.alwaysup.subflow.R

/**
 * What changed in this update, shown once after the app updates.
 *
 * The app had no way of telling anyone that a new build had arrived, and no
 * reply address outside Settings' Contact support. Both matter while the app
 * is still in closed testing: a tester who never notices a new version has
 * nothing new to say about it.
 *
 * The three lines are strings rather than anything computed, and are rewritten
 * for each release alongside the Play release notes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(
    onFeedback: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = contentGutter(20.dp))
                .navigationBarsPadding()
        ) {
            Text(
                text = stringResource(R.string.whats_new_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.whats_new_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // The lines scroll, the buttons do not. A plain Column inside a
            // bottom sheet cannot scroll, so at a large font in a wordy
            // language the "Got it" button would sit below the sheet with no
            // way to reach it - the same trap the picker sheet fell into.
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                listOf(
                    R.string.whats_new_item_1,
                    R.string.whats_new_item_2,
                    R.string.whats_new_item_3
                ).forEach { item ->
                    WhatsNewLine(text = stringResource(item))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            PrimaryButton(
                text = stringResource(R.string.whats_new_done),
                onClick = onDismiss
            )

            // Secondary on purpose: the sheet is there to tell them something,
            // and asking for a reply is the smaller of the two invitations.
            TextButton(
                onClick = onFeedback,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.whats_new_feedback),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun WhatsNewLine(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
