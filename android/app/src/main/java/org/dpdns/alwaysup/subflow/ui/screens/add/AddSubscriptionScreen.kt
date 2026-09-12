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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dpdns.alwaysup.subflow.R
import org.dpdns.alwaysup.subflow.data.notifications.RenewalNotificationWorker
import org.dpdns.alwaysup.subflow.data.preferences.PreferencesManager
import org.dpdns.alwaysup.subflow.data.preferences.ReminderLead
import org.dpdns.alwaysup.subflow.domain.model.BillingCycle
import org.dpdns.alwaysup.subflow.domain.model.PresetService
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.model.TrialOutcome
import org.dpdns.alwaysup.subflow.domain.util.CurrencyFormatter
import org.dpdns.alwaysup.subflow.domain.util.CustomLogoStore
import org.dpdns.alwaysup.subflow.domain.util.DateCalculators
import org.dpdns.alwaysup.subflow.domain.util.localizedCurrencies
import org.dpdns.alwaysup.subflow.ui.components.*
import org.dpdns.alwaysup.subflow.ui.screens.dashboard.localizedCategory
import org.dpdns.alwaysup.subflow.ui.theme.*
import org.dpdns.alwaysup.subflow.ui.util.rememberHaptics
import java.time.LocalDate
import java.time.ZoneId
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
    val currencies = remember(locale) { localizedCurrencies(locale) }
    val isEditing = existingSubscription != null

    var selectedPresetId by rememberSaveable { mutableStateOf<String?>(null) }

    var name by rememberSaveable { mutableStateOf(existingSubscription?.name ?: "") }
    var category by rememberSaveable { mutableStateOf(existingSubscription?.category ?: "Streaming") }
    // On a trial the price field holds what it will cost *after* the trial;
    // the stored amount is zero, and showing that would look like the number
    // had been lost.
    var amountText by rememberSaveable {
        mutableStateOf(
            existingSubscription?.let {
                trimAmount(if (it.isTrial) it.postTrialAmount else it.amount)
            } ?: ""
        )
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
    var isTrial by rememberSaveable { mutableStateOf(existingSubscription?.isTrial ?: false) }
    // A month is what most trials are, and it is a date the user can see is
    // wrong - unlike today, which validates and quietly means "already over".
    var trialEndDate by rememberSaveable {
        mutableStateOf(
            existingSubscription?.trialEndDate?.takeIf { it.isNotBlank() }
                ?: LocalDate.now().plusMonths(1).toString()
        )
    }
    var trialConverts by rememberSaveable {
        mutableStateOf(existingSubscription?.trialConverts ?: true)
    }
    val trialLeads = remember(context) { PreferencesManager.readTrialLeadsStatic(context) }

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

    // For a trial the next dated event is its ending, which is also what gets
    // stored, so the preview and the saved row agree.
    val nextRenewal = remember(firstBillDate, cycle, isTrial, trialEndDate) {
        if (isTrial) trialEndDate else DateCalculators.computeNextRenewalDate(firstBillDate, cycle)
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
        isTrial && previewDaysLeft < 0L -> stringResource(R.string.trial_ended)
        isTrial && previewDaysLeft == 0L -> stringResource(R.string.trial_ends_today)
        isTrial -> pluralStringResource(
            R.plurals.trial_days_left,
            previewDaysLeft.toInt(),
            previewDaysLeft.toInt()
        )
        previewDaysLeft < 0L -> stringResource(R.string.renewal_overdue)
        previewDaysLeft == 0L -> stringResource(R.string.renewal_today)
        else -> pluralStringResource(
            R.plurals.renewal_days_left,
            previewDaysLeft.toInt(),
            previewDaysLeft.toInt()
        )
    }
    val previewUrgent = if (isTrial) previewDaysLeft <= 3L else previewDaysLeft in 0..3
    val previewCycleSpoken = when (cycle) {
        BillingCycle.WEEKLY -> stringResource(R.string.cycle_weekly)
        BillingCycle.MONTHLY -> stringResource(R.string.cycle_monthly)
        BillingCycle.QUARTERLY -> stringResource(R.string.cycle_quarterly)
        BillingCycle.ANNUALLY -> stringResource(R.string.cycle_yearly)
    }
    val previewCategorySpoken = localizedCategory(category)
    val previewNameSpoken = name.ifBlank { stringResource(R.string.service_name) }
    val previewFreeLabel = stringResource(R.string.trial_free_amount)
    // What the row's right-hand column says. On a trial it is what the
    // subscription costs *after* the trial, and reading the figure out without
    // that word turns a warning into a bill the user believes they are paying.
    val previewTrailing = when {
        isTrial && !trialConverts -> stringResource(R.string.trial_no_charge)
        isTrial -> stringResource(
            R.string.trial_then_amount,
            CurrencyFormatter.format(previewAmount, currency, locale)
        )
        else -> previewCycleSpoken
    }
    // "/mo" is a glyph, not a word: read out it becomes "slash m o". The
    // spoken summary uses the full cycle name and states the price, which
    // element-by-element reading never attaches to a service.
    val previewSummary = listOf(
        previewNameSpoken,
        previewCategorySpoken,
        previewRenewalText,
        if (isTrial) previewFreeLabel else CurrencyFormatter.format(previewAmount, currency, locale),
        previewTrailing
    ).joinToString(", ")
    val nameValid = name.isNotBlank()
    // A trial that simply stops never has a price to state, so requiring one
    // would be demanding a number the user does not have.
    val amountValid = (isTrial && !trialConverts) || previewAmount > 0.0
    val trialDatesValid = !isTrial || run {
        val end = DateCalculators.parseOrNull(trialEndDate)
        val start = DateCalculators.parseOrNull(firstBillDate)
        end != null && start != null && end.isAfter(start)
    }
    val isValid = nameValid && amountValid && trialDatesValid

    val hasChanges = remember(
        name, category, amountText, currency, cycle, reminderDays, selectedColorHex, notes,
        firstBillDate, isTrial, trialEndDate, trialConverts
    ) {
        if (existingSubscription == null) {
            name.isNotBlank() || amountText.isNotBlank() || notes.isNotBlank() || isTrial
        } else {
            name != existingSubscription.name ||
                category != existingSubscription.category ||
                isTrial != existingSubscription.isTrial ||
                (isTrial && trialEndDate != existingSubscription.trialEndDate) ||
                (isTrial && trialConverts != existingSubscription.trialConverts) ||
                previewAmount != (
                    if (existingSubscription.isTrial) {
                        existingSubscription.postTrialAmount
                    } else {
                        existingSubscription.amount
                    }
                ) ||
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
                                // A trial costs nothing today. The repository
                                // enforces this as well; stating it here keeps
                                // the object handed over honest rather than
                                // relying on being corrected downstream.
                                amount = if (isTrial) 0.0 else previewAmount,
                                currency = currency,
                                cycle = cycle,
                                firstBillDate = firstBillDate,
                                nextBillDate = nextRenewal,
                                reminderDaysBefore = reminderDays,
                                colorHex = selectedColorHex,
                                iconUrl = iconUrl,
                                notes = notes.trim(),
                                isTrial = isTrial,
                                trialEndDate = if (isTrial) trialEndDate else "",
                                trialConverts = trialConverts,
                                postTrialAmount = if (isTrial) previewAmount else 0.0,
                                postTrialCycle = cycle,
                                // Turning a resolved trial back into a running
                                // one starts its story over; keeping the old
                                // outcome would hide the prompt at its new end.
                                trialOutcome = TrialOutcome.PENDING
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
                                cycleLabel = if (isTrial) {
                                    previewTrailing
                                } else {
                                    cycleShortLabel(cycle)
                                },
                                freeLabel = if (isTrial) previewFreeLabel else null,
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
                        FormFieldRow(
                            label = if (isTrial) {
                                stringResource(R.string.field_price_after_trial)
                            } else {
                                stringResource(R.string.field_price)
                            }
                        ) {
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
                                // Suppressed on a trial: the date below is an
                                // ending, and calling it a renewal here while
                                // the row underneath calls it the trial's end
                                // gives the same day two different meanings.
                                if (!isTrial) {
                                    Text(
                                        text = stringResource(
                                            R.string.renews_next_on,
                                            DateCalculators.formatMedium(nextRenewal, locale)
                                        ),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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

                        // Free trial
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.trial_toggle),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.trial_toggle_sub),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isTrial,
                                onCheckedChange = {
                                    haptics.tick()
                                    isTrial = it
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        AnimatedVisibility(visible = isTrial) {
                            Column {
                                RowDivider()

                                // Trial end date. The picker refuses anything
                                // on or before the start date, so the invalid
                                // state is unreachable rather than merely
                                // reported; the error below it is for a record
                                // that arrived in that state some other way.
                                val trialStart = DateCalculators.parseOrNull(firstBillDate)
                                    ?: LocalDate.now()
                                val parsedTrialEnd = DateCalculators.parseOrNull(trialEndDate)
                                    ?: trialStart.plusMonths(1)
                                val trialPickerDialog = remember(trialEndDate, firstBillDate) {
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d -> trialEndDate = LocalDate.of(y, m + 1, d).toString() },
                                        parsedTrialEnd.year,
                                        parsedTrialEnd.monthValue - 1,
                                        parsedTrialEnd.dayOfMonth
                                    ).apply {
                                        datePicker.minDate = trialStart
                                            .plusDays(1)
                                            .atStartOfDay(ZoneId.systemDefault())
                                            .toInstant()
                                            .toEpochMilli()
                                    }
                                }

                                val trialEndLabel = stringResource(R.string.trial_end_date)
                                val trialEndValue = DateCalculators.formatMedium(trialEndDate, locale)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { trialPickerDialog.show() }
                                        .heightIn(min = 48.dp)
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                        .semantics {
                                            contentDescription = "$trialEndLabel, $trialEndValue"
                                            role = Role.Button
                                        },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = trialEndLabel,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = trialEndValue,
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
                                FieldError(
                                    visible = showValidation && !trialDatesValid,
                                    text = stringResource(R.string.trial_end_date_invalid)
                                )
                                RowDivider()

                                // Whether it turns into a bill, which decides
                                // whether the price above is required at all.
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.trial_converts),
                                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = stringResource(R.string.trial_converts_sub),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = trialConverts,
                                        onCheckedChange = {
                                            haptics.tick()
                                            trialConverts = it
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }

                                // The lead picker further down belongs to
                                // renewals and is gated on Pro; a trial uses
                                // its own free set, so what will actually
                                // happen is stated here instead of leaving the
                                // user to read the wrong control.
                                Text(
                                    text = if (trialLeads.isEmpty() || reminderDays <= 0) {
                                        stringResource(R.string.trial_alert_off)
                                    } else {
                                        stringResource(
                                            R.string.trial_alert_summary,
                                            trialLeads.sortedDescending().joinToString(", ")
                                        )
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(
                                        start = 16.dp,
                                        end = 16.dp,
                                        bottom = 10.dp
                                    )
                                )

                                // Said here rather than left to be discovered
                                // when the warning never arrives. The trial is
                                // still tracked either way, and saying so is
                                // the difference between a limitation and a
                                // silent failure.
                                if (!RenewalNotificationWorker.hasNotificationPermission(context)) {
                                    Text(
                                        text = stringResource(R.string.trial_reminders_unavailable),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(
                                            start = 16.dp,
                                            end = 16.dp,
                                            bottom = 10.dp
                                        )
                                    )
                                }
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
                // covers the palette colours far enough along to be off-screen,
                // which was true before any of this.
                val swatchScroll = rememberLazyListState()
                LaunchedEffect(swatches, selectedColorHex) {
                    val index = swatches.indexOfFirst {
                        it.equals(selectedColorHex, ignoreCase = true)
                    }
                    if (index < 0) return@LaunchedEffect
                    // Only when it is not already on screen. Scrolling on every
                    // pick meant tapping a swatch that was sitting right there
                    // yanked the row half a screen sideways under the finger -
                    // a worse fault than the one being fixed, and one this
                    // effect introduced rather than found.
                    val info = swatchScroll.layoutInfo
                    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                    val fullyVisible = item != null &&
                        item.offset >= info.viewportStartOffset &&
                        item.offset + item.size <= info.viewportEndOffset
                    if (!fullyVisible) swatchScroll.animateScrollToItem(index)
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
                                // The ring sits outside the swatch with a gap,
                                // rather than on its edge. Drawn on the edge in
                                // onSurface it vanished into any swatch of a
                                // similar tone - Apple TV+ is #1C1C1E and the
                                // ring is near-black in light mode, so the one
                                // selected swatch was the one with no visible
                                // selection. Out here it only ever has to
                                // contrast with the card, which it always does.
                                //
                                // The swatch stays 36dp whether or not it is
                                // selected, so nothing resizes as the selection
                                // moves.
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .then(
                                            if (isSelected) {
                                                Modifier.border(
                                                    width = 2.dp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    shape = CircleShape
                                                )
                                            } else Modifier
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
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(color),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                // Brand colours joined this row
                                                // and some of them are bright:
                                                // Hulu's green is luminance
                                                // 0.59, and a white tick on it
                                                // is barely there.
                                                tint = if (color.luminance() > 0.5f) {
                                                    Color.Black.copy(alpha = 0.8f)
                                                } else Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
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
            items = currencies,
            key = { it.code },
            searchHint = stringResource(R.string.search_currency_hint),
            matches = { curr, q -> curr.matches(q) },
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
