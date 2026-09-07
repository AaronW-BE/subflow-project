package org.dpdns.alwaysup.subflow.ui.screens.add

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dpdns.alwaysup.subflow.R
import org.dpdns.alwaysup.subflow.domain.model.PresetService
import org.dpdns.alwaysup.subflow.ui.components.BrandIconBadge
import org.dpdns.alwaysup.subflow.ui.components.PrimaryButton
import org.dpdns.alwaysup.subflow.ui.screens.dashboard.localizedCategory
import org.dpdns.alwaysup.subflow.ui.util.rememberHaptics

/**
 * Step one of the staged add flow: pick the service, then continue.
 *
 * The catalogue gets a screen of its own here, which is the one place it can
 * have one. It used to sit inside the add form, where thirty-four services came
 * to twelve rows of tiles and pushed every field the form actually needed below
 * the fold. On its own screen the same list costs nothing.
 *
 * "Custom service" is a first-class choice rather than a fallback, because a
 * service the catalogue has never heard of is not an error case.
 *
 * @param onNext receives the chosen preset, or null for a custom service.
 */
@Composable
fun SelectServiceScreen(
    presets: List<PresetService>,
    onNext: (PresetService?) -> Unit,
    onCancel: () -> Unit
) {
    val haptics = rememberHaptics()
    var query by rememberSaveable { mutableStateOf("") }
    // Survives going forward to the editor and coming back, which is the whole
    // point of a step the user can return to.
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    val visible = remember(presets, query) {
        val q = query.trim()
        if (q.isEmpty()) presets
        else presets.filter {
            it.name.contains(q, ignoreCase = true) || it.category.contains(q, ignoreCase = true)
        }
    }
    val selected = presets.firstOrNull { it.id == selectedId }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(52.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) {
                    Text(
                        text = stringResource(R.string.cancel),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = stringResource(R.string.select_service_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1
                )
                // Balances the Cancel button so the title stays centred.
                Spacer(modifier = Modifier.width(64.dp))
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 16.dp)
            ) {
                PrimaryButton(
                    text = stringResource(R.string.select_service_next),
                    onClick = {
                        haptics.confirm()
                        onNext(selected)
                    }
                )
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "search") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (query.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.search_presets),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                            .copy(alpha = 0.6f),
                                        fontSize = 15.sp
                                    )
                                }
                                inner()
                            }
                        )
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.clear_search),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Always present, and unaffected by the query: a search that finds
            // nothing is exactly when this is the row that helps.
            item(key = "custom") {
                ServiceChoiceRow(
                    title = stringResource(R.string.custom_service),
                    subtitle = stringResource(R.string.custom_service_hint),
                    selected = selectedId == null,
                    badge = {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    onClick = {
                        haptics.tick()
                        selectedId = null
                    }
                )
            }

            items(visible.size, key = { visible[it].id }) { index ->
                val preset = visible[index]
                ServiceChoiceRow(
                    title = preset.name,
                    subtitle = localizedCategory(preset.category),
                    selected = selectedId == preset.id,
                    badge = {
                        BrandIconBadge(
                            name = preset.name,
                            brandColorHex = preset.brandColor,
                            size = 38.dp,
                            cornerRadius = 11.dp,
                            presetId = preset.id
                        )
                    },
                    onClick = {
                        haptics.tick()
                        selectedId = preset.id
                    }
                )
            }

            item(key = "footer") { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

/** One service in the step-one list: brand mark, name, category, tick. */
@Composable
private fun ServiceChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    badge: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = 56.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            badge()
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
