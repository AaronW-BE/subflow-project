package org.dpdns.alwaysup.subflow.ui.screens.add

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import org.dpdns.alwaysup.subflow.domain.util.CurrencyFormatter
import org.dpdns.alwaysup.subflow.domain.util.CustomLogoStore
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

    var selectedPresetId by rememberSaveable { mutableStateOf<String?>(null) }

    var name by rememberSaveable { mutableStateOf(existingSubscription?.name ?: "") }
    var category by rememberSaveable { mutableStateOf(existingSubscription?.category ?: "Streaming") }
    var amountText by rememberSaveable {
        mutableStateOf(existingSubscription?.let { trimAmount(it.amount) } ?: "")
    }
    var currency by rememberSaveable { mutableStateOf(existingSubscription?.currency ?: primaryCurrency) }
    // Whether the figure in the price field is the catalogue's US list price
    // rather than something the user stands behind. It only matters when the
    // currency stops being USD: 15.49 is Netflix's American price, and left in
    // place under a euro sign it becomes a number this app invented.
    var amountIsPresetUSD by rememberSaveable { mutableStateOf(false) }
    var cycle by rememberSaveable { mutableStateOf(existingSubscription?.cycle ?: BillingCycle.MONTHLY) }
    var reminderDays by rememberSaveable {
        mutableIntStateOf(existingSubscription?.reminderDaysBefore ?: 1)
    }
    var selectedColorHex by rememberSaveable { mutableStateOf(existingSubscription?.colorHex ?: "#5856D6") }
    var notes by rememberSaveable { mutableStateOf(existingSubscription?.notes ?: "") }

    // The id is settled when the screen opens rather than at save time,
    // because a custom logo is written to a file named after it and the user
    // can pick one before saving.
    val subscriptionId = rememberSaveable {
        existingSubscription?.id ?: ("sub_" + UUID.randomUUID().toString().take(10))
    }
    var iconUrl by rememberSaveable { mutableStateOf(existingSubscription?.iconUrl.orEmpty()) }
    var firstBillDate by rememberSaveable {
        mutableStateOf(existingSubscription?.firstBillDate ?: LocalDate.now().toString())
    }

    // PickVisualMedia is the modern picker: no storage permission, and the app
    // only ever sees the one image the user chose.
    val logoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { picked ->
        if (picked != null) {
            CustomLogoStore.save(context, subscriptionId, picked)?.let { iconUrl = it }
        }
    }

    var showServiceSheet by remember { mutableStateOf(false) }
    var showCurrencySheet by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showValidation by remember { mutableStateOf(false) }

    val serviceNameLabel = stringResource(R.string.service_name)
    val priceLabel = stringResource(R.string.field_price)
    val chooseLogoLabel = stringResource(R.string.choose_custom_logo)
    val removeLogoLabel = stringResource(R.string.remove_custom_logo)

    val nextRenewal = remember(firstBillDate, cycle) {
        DateCalculators.computeNextRenewalDate(firstBillDate, cycle)
    }
    val previewAmount = amountText.parseAmount()
    // The same renewal wording the saved row will use, so the preview is a
    // preview. The exact date is not repeated here: it already has a line of
    // its own under the First Payment Date field, and spelling it out in full
    // is what pushed this line past the width it had and cost it the date.
    val previewDaysLeft = remember(nextRenewal) {
        DateCalculators.calculateDaysUntil(nextRenewal)
    }
    val previewRenewalText = when {
        previewDaysLeft < 0L -> stringResource(R.string.renewal_overdue)
        previewDaysLeft == 0L -> stringResource(R.string.renewal_today)
        else -> pluralStringResource(
            R.plurals.renewal_days_left,
            previewDaysLeft.toInt(),
            previewDaysLeft.toInt()
        )
    }
    val previewUrgent = previewDaysLeft in 0..3
    val previewCycleSpoken = when (cycle) {
        BillingCycle.WEEKLY -> stringResource(R.string.cycle_weekly)
        BillingCycle.MONTHLY -> stringResource(R.string.cycle_monthly)
        BillingCycle.QUARTERLY -> stringResource(R.string.cycle_quarterly)
        BillingCycle.ANNUALLY -> stringResource(R.string.cycle_yearly)
    }
    val previewCategorySpoken = localizedCategory(category)
    val previewNameSpoken = name.ifBlank { stringResource(R.string.service_name) }
    // "/mo" is a glyph, not a word: read out it becomes "slash m o". The
    // spoken summary uses the full cycle name and states the price, which
    // element-by-element reading never attaches to a service.
    val previewSummary = listOf(
        previewNameSpoken,
        previewCategorySpoken,
        previewRenewalText,
        CurrencyFormatter.format(previewAmount, currency, locale),
        previewCycleSpoken
    ).joinToString(", ")
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

    // Applying a preset touches six pieces of state, and it is reached both
    // from the picker sheet and from the row that summarises it.
    fun applyPreset(preset: PresetService) {
        selectedPresetId = preset.id
        name = preset.name
        category = preset.category
        cycle = preset.defaultCycle
        selectedColorHex = preset.brandColor
        // Otherwise a logo picked for the previous choice would hide the new
        // brand's mark.
        if (iconUrl.isNotBlank()) {
            CustomLogoStore.delete(context, subscriptionId)
            iconUrl = ""
        }
        // The catalogue only carries a US list price, and services price
        // regionally - Netflix is not 15.49 of anything outside the US.
        // Filling the field only when the user is already in USD keeps the
        // preset useful without asserting a price we do not know.
        //
        // This used to also force currency = "USD", silently undoing the home
        // currency: a CNY user tapped Netflix and watched the preview turn
        // from 0.00 into $15.49.
        if (currency == "USD") {
            amountText = trimAmount(preset.defaultAmountUSD)
            amountIsPresetUSD = true
        } else if (amountIsPresetUSD) {
            // Carrying the last brand's US price over to this one is worse
            // than an empty field: it looks entered.
            amountText = ""
            amountIsPresetUSD = false
        }
    }

    fun attemptCancel() {
        if (hasChanges) showDiscardDialog = true else onCancel()
    }

    BackHandler { attemptCancel() }

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
                                id = subscriptionId,
                                name = name.trim(),
                                category = category,
                                amount = previewAmount,
                                currency = currency,
                                cycle = cycle,
                                firstBillDate = firstBillDate,
                                nextBillDate = nextRenewal,
                                reminderDaysBefore = reminderDays,
                                colorHex = selectedColorHex,
                                iconUrl = iconUrl,
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
                            // The preview badge doubles as the logo control:
                            // it is already showing exactly what will be saved,
                            // so it is the obvious thing to tap to change it.
                            Box {
                                BrandIconBadge(
                                    name = name.ifBlank { "?" },
                                    brandColorHex = selectedColorHex,
                                    size = 48.dp,
                                    cornerRadius = 14.dp,
                                    iconUri = iconUrl,
                                    presetId = selectedPresetId,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            haptics.tick()
                                            logoPicker.launch(
                                                PickVisualMediaRequest(
                                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                                )
                                            )
                                        }
                                        .semantics {
                                            contentDescription = chooseLogoLabel
                                        }
                                )
                                if (iconUrl.isNotBlank()) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surface,
                                        shadowElevation = 2.dp,
                                        onClick = {
                                            haptics.tick()
                                            CustomLogoStore.delete(context, subscriptionId)
                                            iconUrl = ""
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 6.dp, y = (-6).dp)
                                            .size(22.dp)
                                            .semantics { contentDescription = removeLogoLabel }
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(5.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            // The badge beside this is a button; only the text
                            // half is collapsed into the summary, so the logo
                            // picker keeps its own label and action.
                            SubscriptionRowContent(
                                name = name.ifBlank { stringResource(R.string.service_name) },
                                category = localizedCategory(category),
                                renewalText = previewRenewalText,
                                urgent = previewUrgent,
                                amount = previewAmount,
                                currencyCode = currency,
                                cycleLabel = cycleShortLabel(cycle),
                                nameColor = if (name.isBlank()) {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .clearAndSetSemantics {
                                        contentDescription = previewSummary
                                    }
                            )
                        }
                    }
                }
            }

            item(key = "details") {
                Column {
                    SectionHeader(text = stringResource(R.string.subscription_details))
                    AppleGroupedCard(modifier = Modifier.fillMaxWidth()) {
                        // Service. Only on a new subscription: changing the
                        // preset rewrites the name, price, colour and cycle,
                        // which is what you want while creating one and not
                        // what you want while correcting one.
                        //
                        // AppleListRow rather than a row of its own: hand-built,
                        // it drifted to a 16sp label beside its siblings' 15sp
                        // and a left-aligned value where every other row in the
                        // card right-aligns against the chevron.
                        if (!isEditing) {
                            val selectedPreset = presets.firstOrNull { it.id == selectedPresetId }
                            // A typed name with no preset behind it is a custom
                            // service; saying "Choose a service" over the top of
                            // one claims nothing has been chosen.
                            val serviceLabel = when {
                                selectedPreset != null -> selectedPreset.name
                                name.isNotBlank() -> stringResource(R.string.custom_service)
                                else -> stringResource(R.string.choose_service)
                            }
                            AppleListRow(
                                title = stringResource(R.string.field_service),
                                valueText = serviceLabel,
                                valuePrefix = selectedPreset?.let { preset ->
                                    {
                                        BrandIconBadge(
                                            name = preset.name,
                                            brandColorHex = preset.brandColor,
                                            size = 24.dp,
                                            cornerRadius = 7.dp,
                                            presetId = preset.id
                                        )
                                    }
                                },
                                onClick = { showServiceSheet = true }
                            )
                        }

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
                                onValueChange = {
                                    amountText = sanitiseAmountInput(it)
                                    amountIsPresetUSD = false
                                },
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

            item(key = "color") {
                // The service's own colour, when there is one to know about.
                // On a fresh pick that is the preset; on an edit the preset id
                // is null, so it is recovered from the name the row was saved
                // with.
                val brandColour = remember(selectedPresetId, name, presets) {
                    (presets.firstOrNull { it.id == selectedPresetId }
                        ?: presets.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) })
                        ?.brandColor
                }
                val swatches = remember(brandColour) { accentSwatches(brandColour) }
                // The selected swatch has to be brought into view, and not
                // only for tidiness. This row is keyed, so a lazy list anchors
                // on the key it is already showing: prepending the brand
                // colour pushed it off the left edge and the row still came up
                // looking unanswered - the exact symptom being fixed. It also
                // covers the palette colours far enough along to be off-screen.
                val swatchScroll = rememberLazyListState()
                LaunchedEffect(swatches, selectedColorHex) {
                    val index = swatches.indexOfFirst {
                        it.equals(selectedColorHex, ignoreCase = true)
                    }
                    if (index >= 0) swatchScroll.scrollToItem(index)
                }
                // Whether the icon is artwork that covers the tile completely,
                // in which case this colour cannot change it.
                val hasFullColourMark = remember(selectedPresetId, name) {
                    BrandLogos.colourMarkFor(selectedPresetId, name) != null
                }

                Column {
                    SectionHeader(text = stringResource(R.string.brand_accent_color))
                    AppleCard(modifier = Modifier.fillMaxWidth()) {
                        LazyRow(
                            state = swatchScroll,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(swatches, key = { it }) { hex ->
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

                        // Said once, here, rather than left for the user to
                        // discover by tapping a swatch and seeing the preview
                        // not move.
                        if (hasFullColourMark) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.accent_colour_own_mark),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item(key = "footer") { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }

    if (showServiceSheet) {
        // null is the "none of these" row. It is carried inside the list, and
        // matches every query, so a search that finds nothing still leaves the
        // one row that helps - which is the moment the user most needs it.
        val options: List<PresetService?> = listOf(null) + presets
        SubFlowPickerSheet(
            title = stringResource(R.string.field_service),
            items = options,
            key = { it?.id ?: CUSTOM_SERVICE_KEY },
            searchHint = stringResource(R.string.search_presets),
            matches = { preset, query ->
                preset == null ||
                    preset.name.contains(query, ignoreCase = true) ||
                    preset.category.contains(query, ignoreCase = true)
            },
            onDismiss = { showServiceSheet = false }
        ) { preset ->
            if (preset == null) {
                ServiceSheetRow(
                    title = stringResource(R.string.custom_service),
                    subtitle = stringResource(R.string.custom_service_hint),
                    selected = selectedPresetId == null,
                    badge = {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
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
                        // Only clear what the preset had put there. Someone who
                        // typed their own name, opened the sheet to look, and
                        // picked this should keep what they typed.
                        if (selectedPresetId != null) {
                            selectedPresetId = null
                            name = ""
                            if (amountIsPresetUSD) {
                                amountText = ""
                                amountIsPresetUSD = false
                            }
                        }
                        showServiceSheet = false
                    }
                )
            } else {
                ServiceSheetRow(
                    title = preset.name,
                    subtitle = localizedCategory(preset.category),
                    selected = selectedPresetId == preset.id,
                    badge = {
                        BrandIconBadge(
                            name = preset.name,
                            brandColorHex = preset.brandColor,
                            size = 36.dp,
                            cornerRadius = 10.dp,
                            presetId = preset.id
                        )
                    },
                    onClick = {
                        haptics.tick()
                        applyPreset(preset)
                        showServiceSheet = false
                    }
                )
            }
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
                    // A US list price does not survive the currency changing;
                    // it would keep its digits and silently change meaning.
                    if (amountIsPresetUSD && curr.code != "USD") {
                        amountText = ""
                        amountIsPresetUSD = false
                    }
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

/** Key for the sheet's "none of these" row; no preset can collide with it. */
private const val CUSTOM_SERVICE_KEY = "__custom_service__"

/**
 * One service in the picker sheet: brand mark, name, category, tick.
 *
 * Not [SubFlowPickerRow] because that row is text only, and a list of thirty
 * four services is far quicker to scan by logo than by reading every name.
 */
@Composable
private fun ServiceSheetRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    badge: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(vertical = 8.dp, horizontal = 8.dp),
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

/**
 * The swatches to offer, with the service's own colour among them.
 *
 * 31 of the 34 presets have a brand colour that is not one of the ten in
 * [ApplePalette], so the row used to come up with nothing selected after
 * choosing a service - a field that looks unanswered, and whose every answer
 * silently replaced the right colour with a generic one that could not be
 * undone, because the right one was not on offer.
 *
 * First rather than appended: it is the value the field already holds, and a
 * user who changes their mind should not have to hunt for the way back.
 */
internal fun accentSwatches(
    brandColour: String?,
    palette: List<String> = ApplePalette
): List<String> {
    val brand = brandColour?.trim().orEmpty()
    if (brand.isEmpty()) return palette
    if (palette.any { it.equals(brand, ignoreCase = true) }) return palette
    return listOf(brand) + palette
}

private fun String.parseAmount(): Double = trim().toDoubleOrNull() ?: 0.0

private fun trimAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString()
    else String.format(Locale.US, "%.2f", value)
