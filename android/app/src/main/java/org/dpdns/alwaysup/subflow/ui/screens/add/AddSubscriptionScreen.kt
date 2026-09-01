package org.dpdns.alwaysup.subflow.ui.screens.add

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dpdns.alwaysup.subflow.R
import org.dpdns.alwaysup.subflow.data.preferences.ReminderLead
import org.dpdns.alwaysup.subflow.data.preferences.SupportedCurrencies
import org.dpdns.alwaysup.subflow.domain.model.BillingCycle
import org.dpdns.alwaysup.subflow.domain.model.PresetService
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.util.DateCalculators
import org.dpdns.alwaysup.subflow.ui.components.*
import org.dpdns.alwaysup.subflow.ui.screens.dashboard.localizedCategory
import org.dpdns.alwaysup.subflow.ui.theme.*
import org.dpdns.alwaysup.subflow.ui.util.rememberHaptics
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

private val CATEGORIES = listOf(
    "Streaming", "Productivity", "Cloud", "Utilities", "Health",
    "Finance", "Gaming", "Education", "Lifestyle", "News"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSubscriptionScreen(
    presets: List<PresetService>,
    primaryCurrency: String = "USD",
    isPro: Boolean = false,
    existingSubscription: Subscription? = null,
    onSaveSubscription: (Subscription) -> Unit,
    onCancel: () -> Unit,
    onUpgradeClick: () -> Unit = {}
) {
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) { configuration.locales.get(0) ?: Locale.getDefault() }
    val isEditing = existingSubscription != null

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedPresetId by rememberSaveable { mutableStateOf<String?>(null) }

    var name by rememberSaveable { mutableStateOf(existingSubscription?.name ?: "") }
    var category by rememberSaveable { mutableStateOf(existingSubscription?.category ?: "Streaming") }
    var amountText by rememberSaveable {
        mutableStateOf(existingSubscription?.let { trimAmount(it.amount) } ?: "")
    }
    var currency by rememberSaveable { mutableStateOf(existingSubscription?.currency ?: primaryCurrency) }
    var cycle by rememberSaveable { mutableStateOf(existingSubscription?.cycle ?: BillingCycle.MONTHLY) }
    var reminderDays by rememberSaveable {
        mutableIntStateOf(existingSubscription?.reminderDaysBefore ?: 1)
    }
    var selectedColorHex by rememberSaveable { mutableStateOf(existingSubscription?.colorHex ?: "#5856D6") }
    var notes by rememberSaveable { mutableStateOf(existingSubscription?.notes ?: "") }
    var firstBillDate by rememberSaveable {
        mutableStateOf(existingSubscription?.firstBillDate ?: LocalDate.now().toString())
    }

    var showCurrencySheet by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showValidation by remember { mutableStateOf(false) }

    val serviceNameLabel = stringResource(R.string.service_name)
    val priceLabel = stringResource(R.string.field_price)

    val nextRenewal = remember(firstBillDate, cycle) {
        DateCalculators.computeNextRenewalDate(firstBillDate, cycle)
    }
    val previewAmount = amountText.parseAmount()
    val nameValid = name.isNotBlank()
    val amountValid = previewAmount > 0.0
    val isValid = nameValid && amountValid

    val hasChanges = remember(
        name, category, amountText, currency, cycle, reminderDays, selectedColorHex, notes, firstBillDate
    ) {
        if (existingSubscription == null) {
            name.isNotBlank() || amountText.isNotBlank() || notes.isNotBlank()
        } else {
            name != existingSubscription.name ||
                category != existingSubscription.category ||
                previewAmount != existingSubscription.amount ||
                currency != existingSubscription.currency ||
                cycle != existingSubscription.cycle ||
                reminderDays != existingSubscription.reminderDaysBefore ||
                selectedColorHex != existingSubscription.colorHex ||
                notes != existingSubscription.notes ||
                firstBillDate != existingSubscription.firstBillDate
        }
    }

    fun attemptCancel() {
        if (hasChanges) showDiscardDialog = true else onCancel()
    }

    BackHandler { attemptCancel() }

    val filteredPresets = remember(presets, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) presets
        else presets.filter {
            it.name.contains(q, ignoreCase = true) || it.category.contains(q, ignoreCase = true)
        }
    }

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
                TextButton(onClick = { attemptCancel() }) {
                    Text(
                        text = stringResource(R.string.cancel),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = stringResource(
                        if (isEditing) R.string.title_edit_subscription else R.string.title_new_subscription
                    ),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1
                )

                TextButton(
                    onClick = {
                        showValidation = true
                        if (!isValid) return@TextButton
                        haptics.confirm()
                        onSaveSubscription(
                            Subscription(
                                id = existingSubscription?.id ?: ("sub_" + UUID.randomUUID().toString().take(10)),
                                name = name.trim(),
                                category = category,
                                amount = previewAmount,
                                currency = currency,
                                cycle = cycle,
                                firstBillDate = firstBillDate,
                                nextBillDate = nextRenewal,
                                reminderDaysBefore = reminderDays,
                                colorHex = selectedColorHex,
                                iconUrl = existingSubscription?.iconUrl.orEmpty(),
                                notes = notes.trim()
                            )
                        )
                    }
                ) {
                    Text(
                        text = stringResource(R.string.save),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                        color = if (isValid) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        }
                    )
                }
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "preview") {
                Column {
                    SectionHeader(text = stringResource(R.string.live_preview))
                    AppleCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BrandIconBadge(
                                name = name.ifBlank { "?" },
                                brandColorHex = selectedColorHex,
                                size = 48.dp,
                                cornerRadius = 14.dp
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name.ifBlank { stringResource(R.string.service_name) },
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (name.isBlank()) {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${localizedCategory(category)} · " +
                                        stringResource(
                                            R.string.renews_next_on,
                                            DateCalculators.formatMedium(nextRenewal, locale)
                                        ),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                TabularCurrencyText(
                                    amount = previewAmount,
                                    currencyCode = currency,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = cycleShortLabel(cycle),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (!isEditing) {
                item(key = "presets") {
                    Column {
                        SectionHeader(text = stringResource(R.string.popular_presets))

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp)),
                            placeholder = {
                                Text(stringResource(R.string.search_presets), fontSize = 13.sp)
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(R.string.clear_search),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Three-up grid, laid out manually because a nested
                        // LazyVerticalGrid inside a LazyColumn cannot measure.
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (rowItems in filteredPresets.chunked(3)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (preset in rowItems) {
                                        PresetTile(
                                            preset = preset,
                                            selected = selectedPresetId == preset.id,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                haptics.tick()
                                                selectedPresetId = preset.id
                                                name = preset.name
                                                category = preset.category
                                                cycle = preset.defaultCycle
                                                selectedColorHex = preset.brandColor
                                                // The catalogue only carries a
                                                // US list price, and services
                                                // price regionally - Netflix is
                                                // not 15.49 of anything outside
                                                // the US. Filling the field
                                                // only when the user is already
                                                // in USD keeps the preset
                                                // useful without asserting a
                                                // price we do not know.
                                                //
                                                // This used to also force
                                                // currency = "USD", silently
                                                // undoing the home currency: a
                                                // CNY user tapped Netflix and
                                                // watched the preview turn from
                                                // 0.00 into $15.49.
                                                if (currency == "USD") {
                                                    amountText = trimAmount(preset.defaultAmountUSD)
                                                }
                                            }
                                        )
                                    }
                                    repeat(3 - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "color") {
                Column {
                    SectionHeader(text = stringResource(R.string.brand_accent_color))
                    AppleCard(modifier = Modifier.fillMaxWidth()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(ApplePalette, key = { it }) { hex ->
                                val color = parseHexColor(hex)
                                val isSelected = selectedColorHex.equals(hex, ignoreCase = true)
                                val colourLabel = stringResource(R.string.cd_accent_colour, hex)
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (isSelected) 3.dp else 0.dp,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            haptics.tick()
                                            selectedColorHex = hex
                                        }
                                        .semantics { contentDescription = colourLabel },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "details") {
                Column {
                    SectionHeader(text = stringResource(R.string.subscription_details))
                    AppleGroupedCard(modifier = Modifier.fillMaxWidth()) {
                        // Name
                        FormFieldRow(label = stringResource(R.string.field_name)) {
                            BasicTextField(
                                value = name,
                                onValueChange = { name = it.take(60) },
                                textStyle = TextStyle(
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { contentDescription = serviceNameLabel },
                                decorationBox = { inner ->
                                    if (name.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.service_name),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                            fontSize = 16.sp
                                        )
                                    }
                                    inner()
                                }
                            )
                            if (name.isNotEmpty()) {
                                IconButton(
                                    onClick = { name = "" },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.clear_search),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                        FieldError(visible = showValidation && !nameValid, text = stringResource(R.string.name_required))
                        RowDivider()

                        // Price + currency
                        FormFieldRow(label = stringResource(R.string.field_price)) {
                            BasicTextField(
                                value = amountText,
                                onValueChange = { amountText = sanitiseAmountInput(it) },
                                textStyle = TextStyle(
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { contentDescription = priceLabel },
                                decorationBox = { inner ->
                                    if (amountText.isEmpty()) {
                                        Text(
                                            text = "0.00",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                            fontSize = 16.sp
                                        )
                                    }
                                    inner()
                                }
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                onClick = { showCurrencySheet = true }
                            ) {
                                Text(
                                    text = currency,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                        FieldError(visible = showValidation && !amountValid, text = stringResource(R.string.price_invalid))
                        RowDivider()

                        // Category
                        AppleListRow(
                            title = stringResource(R.string.field_category),
                            valueText = localizedCategory(category),
                            onClick = { showCategorySheet = true }
                        )

                        // First payment date
                        val parsedDate = DateCalculators.parseOrNull(firstBillDate) ?: LocalDate.now()
                        val datePickerDialog = remember(firstBillDate) {
                            DatePickerDialog(
                                context,
                                { _, y, m, d -> firstBillDate = LocalDate.of(y, m + 1, d).toString() },
                                parsedDate.year,
                                parsedDate.monthValue - 1,
                                parsedDate.dayOfMonth
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { datePickerDialog.show() }
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.first_bill_date),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(
                                        R.string.renews_next_on,
                                        DateCalculators.formatMedium(nextRenewal, locale)
                                    ),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = DateCalculators.formatMedium(firstBillDate, locale),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                        RowDivider()

                        // Reminder toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(R.string.renewal_alerts),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (!isPro) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        ProBadge()
                                    }
                                }
                                Text(
                                    text = stringResource(R.string.remind_before_renewal),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = reminderDays > 0,
                                onCheckedChange = {
                                    haptics.tick()
                                    reminderDays = if (it) 1 else 0
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        AnimatedVisibility(visible = reminderDays > 0) {
                            Column {
                                RowDivider()
                                ReminderLeadPicker(
                                    selectedDays = reminderDays,
                                    isPro = isPro,
                                    onSelect = {
                                        haptics.tick()
                                        reminderDays = it
                                    },
                                    onUpgrade = onUpgradeClick
                                )
                            }
                        }
                        RowDivider()

                        // Notes
                        FormFieldRow(label = stringResource(R.string.field_notes)) {
                            BasicTextField(
                                value = notes,
                                onValueChange = { notes = it.take(200) },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.weight(1f),
                                decorationBox = { inner ->
                                    if (notes.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.field_notes_hint),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                    inner()
                                }
                            )
                        }
                    }
                }
            }

            item(key = "cycle") {
                Column {
                    SectionHeader(text = stringResource(R.string.billing_cycle))
                    CupertinoSegmentedControl(
                        items = BillingCycle.entries,
                        selectedItem = cycle,
                        onItemSelected = { cycle = it },
                        itemLabel = { cycleLabel(it) }
                    )
                }
            }

            item(key = "footer") { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }

    if (showCurrencySheet) {
        SubFlowPickerSheet(
            title = stringResource(R.string.field_currency),
            items = SupportedCurrencies,
            key = { it.code },
            searchHint = stringResource(R.string.search_currency_hint),
            matches = { curr, q ->
                curr.code.contains(q, ignoreCase = true) ||
                    curr.name.contains(q, ignoreCase = true)
            },
            onDismiss = { showCurrencySheet = false }
        ) { curr ->
            SubFlowPickerRow(
                title = "${curr.name} (${curr.code})",
                subtitle = curr.symbol,
                selected = curr.code == currency,
                onClick = {
                    currency = curr.code
                    showCurrencySheet = false
                }
            )
        }
    }

    if (showCategorySheet) {
        SubFlowPickerSheet(
            title = stringResource(R.string.field_category),
            items = CATEGORIES,
            key = { it },
            onDismiss = { showCategorySheet = false }
        ) { cat ->
            SubFlowPickerRow(
                title = localizedCategory(cat),
                selected = cat.equals(category, ignoreCase = true),
                onClick = {
                    category = cat
                    showCategorySheet = false
                }
            )
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.discard_changes_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.discard_changes_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onCancel()
                }) {
                    Text(stringResource(R.string.discard), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.keep_editing))
                }
            }
        )
    }
}

// -------------------------------------------------------------------- pieces

@Composable
private fun FormFieldRow(
    label: String,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(84.dp)
        )
        content()
    }
}

@Composable
private fun FieldError(visible: Boolean, text: String) {
    AnimatedVisibility(visible = visible) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )
    }
}

@Composable
private fun RowDivider() {
    // Form rows carry no leading glyph, so the separator starts at the text.
    AppleRowSeparator(startInset = 16.dp)
}

@Composable
private fun PresetTile(
    preset: PresetService,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 12.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandIconBadge(
                name = preset.name,
                brandColorHex = preset.brandColor,
                size = 36.dp,
                cornerRadius = 10.dp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = preset.name,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReminderLeadPicker(
    selectedDays: Int,
    isPro: Boolean,
    onSelect: (Int) -> Unit,
    onUpgrade: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ReminderLead.entries.forEach { lead ->
                val locked = lead.isPro && !isPro
                val selected = selectedDays == lead.days
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = when {
                        selected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    onClick = { if (locked) onUpgrade() else onSelect(lead.days) }
                ) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (lead.days == 1) {
                                stringResource(R.string.reminder_lead_one)
                            } else {
                                stringResource(R.string.reminder_lead_value, lead.days)
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = when {
                                selected -> Color.White
                                locked -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (locked) {
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun cycleLabel(cycle: BillingCycle): String = when (cycle) {
    BillingCycle.WEEKLY -> stringResource(R.string.cycle_weekly)
    BillingCycle.MONTHLY -> stringResource(R.string.cycle_monthly)
    BillingCycle.QUARTERLY -> stringResource(R.string.cycle_quarterly)
    BillingCycle.ANNUALLY -> stringResource(R.string.cycle_yearly)
}

@Composable
private fun cycleShortLabel(cycle: BillingCycle): String = when (cycle) {
    BillingCycle.WEEKLY -> stringResource(R.string.cycle_short_weekly)
    BillingCycle.MONTHLY -> stringResource(R.string.cycle_short_monthly)
    BillingCycle.QUARTERLY -> stringResource(R.string.cycle_short_quarterly)
    BillingCycle.ANNUALLY -> stringResource(R.string.cycle_short_yearly)
}

// -------------------------------------------------------------------- input

/**
 * Keeps only digits and a single decimal separator, and accepts a comma as the
 * separator for locales whose keyboards emit one.
 */
private fun sanitiseAmountInput(raw: String): String {
    val normalised = raw.replace(',', '.')
    val filtered = buildString {
        var seenDot = false
        for (ch in normalised) {
            when {
                ch.isDigit() -> append(ch)
                ch == '.' && !seenDot -> {
                    seenDot = true
                    append(ch)
                }
            }
        }
    }
    // Cap the minor units at two digits.
    val dotIndex = filtered.indexOf('.')
    return if (dotIndex >= 0 && filtered.length - dotIndex > 3) {
        filtered.substring(0, dotIndex + 3)
    } else {
        filtered
    }
}

private fun String.parseAmount(): Double = trim().toDoubleOrNull() ?: 0.0

private fun trimAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString()
    else String.format(Locale.US, "%.2f", value)
