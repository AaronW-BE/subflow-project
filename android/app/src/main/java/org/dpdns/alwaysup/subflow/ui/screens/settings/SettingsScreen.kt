package org.dpdns.alwaysup.subflow.ui.screens.settings

import android.content.Context
import android.text.format.DateUtils
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.dpdns.alwaysup.subflow.BuildConfig
import org.dpdns.alwaysup.subflow.R
import org.dpdns.alwaysup.subflow.data.preferences.PreferencesManager
import org.dpdns.alwaysup.subflow.data.preferences.ReminderLead
import org.dpdns.alwaysup.subflow.data.preferences.SupportedCurrencies
import org.dpdns.alwaysup.subflow.data.preferences.SupportedLanguages
import org.dpdns.alwaysup.subflow.data.preferences.ThemeMode
import org.dpdns.alwaysup.subflow.domain.model.ProTier
import org.dpdns.alwaysup.subflow.domain.model.UserProfile
import org.dpdns.alwaysup.subflow.ui.components.AppleCard
import org.dpdns.alwaysup.subflow.ui.components.AppleGroupedCard
import org.dpdns.alwaysup.subflow.ui.components.AppleListRow
import org.dpdns.alwaysup.subflow.ui.components.AppleRowSeparator
import org.dpdns.alwaysup.subflow.ui.components.ProBadge
import org.dpdns.alwaysup.subflow.ui.components.SectionHeader
import org.dpdns.alwaysup.subflow.domain.util.CurrencyConverter
import org.dpdns.alwaysup.subflow.ui.components.SubFlowPickerRow
import org.dpdns.alwaysup.subflow.ui.components.SubFlowPickerSheet
import org.dpdns.alwaysup.subflow.ui.theme.SubFlowAccents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    user: UserProfile?,
    isPro: Boolean,
    proTier: ProTier,
    preferencesManager: PreferencesManager,
    isGoogleSignInAvailable: Boolean,
    isPrivacyOptionsRequired: Boolean,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onSyncClick: () -> Unit,
    onPaywallClick: () -> Unit,
    onManageSubscription: () -> Unit,
    onRestorePurchases: () -> Unit,
    onPrivacyOptionsClick: () -> Unit,
    onBackupClick: () -> Unit,
    onRestoreFromFile: (Uri) -> Unit,
    onClearAllData: () -> Unit,
    onTestNotification: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onShareApp: () -> Unit,
    onRateApp: () -> Unit
) {
    val context = LocalContext.current
    val currentCurrency by preferencesManager.currency.collectAsState()
    val currentThemeMode by preferencesManager.themeMode.collectAsState()
    val currentLang by preferencesManager.language.collectAsState()
    val hapticsEnabled by preferencesManager.hapticsEnabled.collectAsState()
    val reminderLeads by preferencesManager.reminderLeads.collectAsState()

    var showCurrencySheet by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    // Export writes a real file through FileProvider, so import has to accept
    // one too. Asking the user to paste raw JSON into a text box was a
    // developer-grade workaround, not something anyone can actually do with a
    // file sitting in their Downloads folder.
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onRestoreFromFile) }

    // Re-read whenever the screen comes back to the foreground: the user can
    // toggle this in system settings and return, and a stale banner would then
    // tell them the opposite of the truth.
    var notificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "title") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 16.dp, bottom = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    )
                )
            }
        }

        item(key = "account") {
            AccountCard(
                user = user,
                isPro = isPro,
                isGoogleSignInAvailable = isGoogleSignInAvailable,
                onPaywallClick = onPaywallClick,
                onSignInClick = onSignInClick,
                onSignOutClick = onSignOutClick
            )
        }

        // Subscription management is only meaningful once something is owned.
        if (isPro) {
            item(key = "membership") {
                AppleGroupedCard(modifier = Modifier.fillMaxWidth()) {
                    if (proTier == ProTier.MONTHLY || proTier == ProTier.ANNUAL) {
                        AppleListRow(
                            title = stringResource(R.string.manage_subscription),
                            icon = Icons.Default.CreditCard,
                            iconTint = MaterialTheme.colorScheme.primary,
                            onClick = onManageSubscription
                        )
                    }
                    AppleListRow(
                        title = stringResource(R.string.btn_restore),
                        icon = Icons.Default.Restore,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        showDivider = false,
                        onClick = onRestorePurchases
                    )
                }
            }
        }

        if (user?.authProvider == "google") {
            item(key = "sync") {
                AppleCard(modifier = Modifier.fillMaxWidth(), onClick = onSyncClick) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Sync,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.cloud_backup),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.cloud_backup_sub),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }

        item(key = "preferences") {
            Column {
                SectionHeader(text = stringResource(R.string.section_preferences))
                AppleGroupedCard(modifier = Modifier.fillMaxWidth()) {
                    AppleListRow(
                        title = stringResource(R.string.primary_currency),
                        valueText = currentCurrency,
                        icon = Icons.Default.AttachMoney,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        onClick = { showCurrencySheet = true }
                    )

                    AppleListRow(
                        title = stringResource(R.string.appearance),
                        valueText = stringResource(currentThemeMode.labelRes),
                        icon = Icons.Default.Palette,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = { showThemeSheet = true }
                    )
                    AppleListRow(
                        title = stringResource(R.string.language),
                        valueText = SupportedLanguages.find { it.code == currentLang }?.nativeName ?: currentLang,
                        icon = Icons.Default.Public,
                        iconTint = SubFlowAccents.blue,
                        onClick = { showLanguageSheet = true }
                    )
                    AppleListRow(
                        title = stringResource(R.string.haptics),
                        subtitle = stringResource(R.string.haptics_sub),
                        icon = Icons.Default.Vibration,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        showDivider = false,
                        trailingContent = {
                            Switch(
                                checked = hapticsEnabled,
                                onCheckedChange = { preferencesManager.setHapticsEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    )
                }
            }
        }

        item(key = "notifications") {
            Column {
                SectionHeader(text = stringResource(R.string.section_notifications))

                AnimatedVisibility(visible = !notificationsEnabled) {
                    AppleCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        cornerRadius = 14.dp,
                        contentPadding = PaddingValues(14.dp),
                        onClick = { openNotificationSettings(context) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.NotificationsOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.notifications_blocked),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = stringResource(R.string.open_settings),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                AppleGroupedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Matches AppleListRow's 32dp glyph + 14dp gap so the
                            // title sits on the same baseline as the rows above.
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.reminder_lead_title),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                    fontWeight = FontWeight.Medium
                                )
                                if (!isPro) {
                                    Text(
                                        text = stringResource(R.string.reminder_lead_sub_free),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (!isPro) ProBadge()
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReminderLead.entries.forEach { lead ->
                                val locked = lead.isPro && !isPro
                                val checked = if (isPro) lead.days in reminderLeads else lead.days == 1
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (checked && !locked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    onClick = {
                                        if (locked) onPaywallClick()
                                        else preferencesManager.toggleReminderLead(lead.days, !checked)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .heightIn(min = 40.dp)
                                            .padding(horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (locked) {
                                            Icon(
                                                Icons.Default.Lock,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                        }
                                        Text(
                                            text = if (lead.days == 1) {
                                                stringResource(R.string.reminder_lead_one)
                                            } else {
                                                stringResource(R.string.reminder_lead_value, lead.days)
                                            },
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            fontWeight = if (checked && !locked) FontWeight.Bold else FontWeight.Medium,
                                            color = when {
                                                checked && !locked -> Color.White
                                                locked -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                else -> MaterialTheme.colorScheme.onSurface
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 62dp so it lines up with the separators of the icon rows
                    // above and below, not 16dp.
                    AppleRowSeparator(startInset = 62.dp)

                    AppleListRow(
                        title = stringResource(R.string.test_notification),
                        subtitle = stringResource(R.string.test_notification_sub),
                        icon = Icons.Default.NotificationsActive,
                        iconTint = MaterialTheme.colorScheme.error,
                        showDivider = false,
                        onClick = onTestNotification
                    )
                }
            }
        }

        item(key = "data") {
            Column {
                SectionHeader(text = stringResource(R.string.data_portability))
                AppleGroupedCard(modifier = Modifier.fillMaxWidth()) {
                    AppleListRow(
                        title = stringResource(R.string.backup_json),
                        subtitle = stringResource(R.string.backup_json_sub),
                        icon = Icons.Default.FileDownload,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        onClick = onBackupClick
                    )
                    AppleListRow(
                        title = stringResource(R.string.restore_json),
                        subtitle = stringResource(R.string.restore_json_sub),
                        icon = Icons.Default.FileUpload,
                        iconTint = SubFlowAccents.blue,
                        showDivider = false,
                        onClick = {
                            // Some providers report a backup as octet-stream, so
                            // accept a couple of types rather than only JSON.
                            runCatching {
                                restoreLauncher.launch(
                                    arrayOf("application/json", "text/plain", "application/octet-stream")
                                )
                            }
                        }
                    )
                }
            }
        }

        item(key = "about") {
            Column {
                SectionHeader(text = stringResource(R.string.legal_and_support))
                AppleGroupedCard(modifier = Modifier.fillMaxWidth()) {
                    AppleListRow(
                        title = stringResource(R.string.rate_app),
                        subtitle = stringResource(R.string.rate_app_sub),
                        icon = Icons.Default.StarRate,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        onClick = onRateApp
                    )
                    AppleListRow(
                        title = stringResource(R.string.share_app),
                        icon = Icons.Default.Share,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = onShareApp
                    )
                    AppleListRow(
                        title = stringResource(R.string.privacy_policy),
                        icon = Icons.Default.Lock,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = { onOpenUrl(PRIVACY_URL) }
                    )
                    AppleListRow(
                        title = stringResource(R.string.terms_of_service),
                        icon = Icons.Default.Description,
                        iconTint = SubFlowAccents.blue,
                        onClick = { onOpenUrl(TERMS_URL) }
                    )
                    // Required by the TCF policy whenever a consent form was shown.
                    if (isPrivacyOptionsRequired && !isPro) {
                        AppleListRow(
                            title = stringResource(R.string.ad_privacy_options),
                            icon = Icons.Default.PrivacyTip,
                            iconTint = SubFlowAccents.purple,
                            onClick = onPrivacyOptionsClick
                        )
                    }
                    AppleListRow(
                        title = stringResource(R.string.contact_support),
                        subtitle = stringResource(R.string.contact_support_sub),
                        icon = Icons.Default.Mail,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        showDivider = false,
                        onClick = { openSupportEmail(context) }
                    )
                }
            }
        }

        // Irreversible, so it gets its own section rather than sitting one row
        // below "Contact support" where a mis-tap is cheap.
        item(key = "danger") {
            Column {
                SectionHeader(text = stringResource(R.string.section_danger))
                AppleGroupedCard(modifier = Modifier.fillMaxWidth()) {
                    AppleListRow(
                        title = stringResource(R.string.clear_all_data),
                        subtitle = stringResource(R.string.clear_all_data_sub),
                        icon = Icons.Default.DeleteForever,
                        iconTint = MaterialTheme.colorScheme.error,
                        showDivider = false,
                        onClick = { showResetDialog = true }
                    )
                }
            }
        }

        item(key = "rate_attribution") {
            // The rate feed's terms require visible attribution wherever the
            // rates are used, and every converted total in the app derives from
            // them. It sits at the foot of Settings with the version, which is
            // where this kind of credit is conventionally looked for.
            RateAttributionRow(onOpenUrl = onOpenUrl)
        }

        item(key = "version") {
            Text(
                // Version name only. The build number is an artefact of the
                // Play upload process and means nothing to the person reading
                // it; it is still attached to support emails, where it does.
                text = stringResource(
                    R.string.app_version_full,
                    stringResource(R.string.app_version, BuildConfig.VERSION_NAME)
                ),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )
        }

        item(key = "footer") { Spacer(modifier = Modifier.height(28.dp)) }
    }

    if (showCurrencySheet) {
        SubFlowPickerSheet(
            title = stringResource(R.string.primary_currency),
            items = SupportedCurrencies,
            key = { it.code },
            searchHint = stringResource(R.string.search_currency_hint),
            // Match on code as well as name: someone looking for the won knows
            // "KRW" long before they know it is filed under "South Korean".
            matches = { curr, q ->
                curr.code.contains(q, ignoreCase = true) ||
                    curr.name.contains(q, ignoreCase = true)
            },
            onDismiss = { showCurrencySheet = false }
        ) { curr ->
            SubFlowPickerRow(
                title = "${curr.name} (${curr.code})",
                subtitle = curr.symbol,
                selected = curr.code == currentCurrency,
                onClick = {
                    preferencesManager.setCurrency(curr.code)
                    showCurrencySheet = false
                }
            )
        }
    }

    if (showThemeSheet) {
        SubFlowPickerSheet(
            title = stringResource(R.string.appearance),
            items = ThemeMode.entries,
            key = { it.key },
            onDismiss = { showThemeSheet = false }
        ) { mode ->
            SubFlowPickerRow(
                title = stringResource(mode.labelRes),
                selected = mode == currentThemeMode,
                onClick = {
                    preferencesManager.setThemeMode(mode)
                    showThemeSheet = false
                }
            )
        }
    }

    if (showLanguageSheet) {
        SubFlowPickerSheet(
            title = stringResource(R.string.language),
            items = SupportedLanguages,
            key = { it.code },
            onDismiss = { showLanguageSheet = false }
        ) { lang ->
            SubFlowPickerRow(
                title = lang.nativeName,
                subtitle = lang.displayName,
                selected = lang.code == currentLang,
                onClick = {
                    preferencesManager.setLanguage(lang.code)
                    showLanguageSheet = false
                }
            )
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.reset_dialog_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.reset_dialog_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        onClearAllData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        stringResource(R.string.reset_confirm),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

// -------------------------------------------------------------------- pieces

@Composable
private fun AccountCard(
    user: UserProfile?,
    isPro: Boolean,
    isGoogleSignInAvailable: Boolean,
    onPaywallClick: () -> Unit,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit
) {
    val isSignedIn = user?.authProvider == "google"
    val displayName = when {
        isSignedIn && user?.name?.isNotBlank() == true -> user.name
        isSignedIn -> user?.email.orEmpty()
        else -> stringResource(R.string.quota_label)
    }

    AppleCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSignedIn) Icons.Default.Person else Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    // The PRO pill on the right already states the plan; replacing
                    // a signed-in user's name with "SubFlow Pro" just lost information.
                    text = if (isSignedIn) displayName else if (isPro) "SubFlow Pro" else displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isSignedIn) {
                        user?.email.orEmpty()
                    } else {
                        stringResource(R.string.guest_vault)
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isPro) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)) {
                    Text(
                        text = "PRO",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = onPaywallClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        stringResource(R.string.upgrade_to_pro),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        // Sign-in is optional: the app is local-first, so it is only offered
        // when a web client id was actually compiled in.
        if (isGoogleSignInAvailable) {
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(
                onClick = if (isSignedIn) onSignOutClick else onSignInClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    if (isSignedIn) Icons.AutoMirrored.Filled.Logout else Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(if (isSignedIn) R.string.sign_out else R.string.sign_in_google),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun RateAttributionRow(onOpenUrl: (String) -> Unit) {
    val quotedAt = CurrencyConverter.quotedAtEpochMillis
    val subtitle = if (quotedAt > 0L) {
        stringResource(
            R.string.rates_updated_on,
            DateUtils.formatDateTime(
                LocalContext.current,
                quotedAt,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
            )
        )
    } else {
        stringResource(R.string.rates_offline_snapshot)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenUrl(RATE_PROVIDER_URL) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.rates_attribution),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

// -------------------------------------------------------------------- intents

/** Required by the exchange rate feed's terms of use. */
const val RATE_PROVIDER_URL = "https://www.exchangerate-api.com"

const val PRIVACY_URL = "https://subflow.alwaysup.dpdns.org/privacy.html"
const val TERMS_URL = "https://subflow.alwaysup.dpdns.org/terms.html"

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching { context.startActivity(intent) }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.fromParts("package", context.packageName, null))
            )
        }
    }
}

private fun openSupportEmail(context: Context) {
    val body = "\n\n---\nSubFlow ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
        "Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = android.net.Uri.parse("mailto:zmtzwb@gmail.com")
        putExtra(Intent.EXTRA_SUBJECT, "SubFlow Android support")
        putExtra(Intent.EXTRA_TEXT, body)
    }
    runCatching { context.startActivity(intent) }
}
