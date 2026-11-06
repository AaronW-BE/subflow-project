package org.dpdns.alwaysup.subflow.ui.components

import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.dpdns.alwaysup.subflow.R
import org.dpdns.alwaysup.subflow.data.notifications.TrialFinalDayScheduler
import org.dpdns.alwaysup.subflow.data.preferences.PreferencesManager
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.util.Trials
import java.util.Calendar

@Composable
fun TrialFinalDaySettings(preferences: PreferencesManager) {
    val context = LocalContext.current
    val enabled by preferences.trialFinalDayEnabled.collectAsState()
    val minute by preferences.trialFinalDayMinuteOfDay.collectAsState()
    val exact by preferences.trialFinalDayExactEnabled.collectAsState()
    var access by remember { mutableStateOf(TrialFinalDayScheduler.canScheduleExact(context)) }
    fun notificationsAllowed(): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 26 || manager.getNotificationChannel("subflow_trial_final_day")?.importance != NotificationManager.IMPORTANCE_NONE)
    }
    var notifications by remember { mutableStateOf(notificationsAllowed()) }
    var settingsUnavailable by remember { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                access = TrialFinalDayScheduler.canScheduleExact(context)
                notifications = notificationsAllowed()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val time = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, minute / 60); set(Calendar.MINUTE, minute % 60) }
    TrialFinalDaySettingsContent(enabled, DateFormat.getTimeFormat(context).format(time.time), exact, access,
        notifications, settingsUnavailable, preferences::setTrialFinalDayEnabled,
        onTime = {
            TimePickerDialog(context, { _, hour, min -> preferences.setTrialFinalDayMinuteOfDay(hour * 60 + min) },
                minute / 60, minute % 60, DateFormat.is24HourFormat(context)).show()
        }, onExact = { selected ->
            preferences.setTrialFinalDayExactEnabled(selected)
            if (selected && Build.VERSION.SDK_INT >= 31 && !TrialFinalDayScheduler.canScheduleExact(context)) {
                try { context.startActivity(TrialFinalDayScheduler.requestExactPermissionIntent(context)) }
                catch (_: ActivityNotFoundException) { settingsUnavailable = true }
                catch (_: SecurityException) { settingsUnavailable = true }
            }
        })
}

@Composable
internal fun TrialFinalDaySettingsContent(
    enabled: Boolean, time: String, exact: Boolean, access: Boolean, notifications: Boolean,
    settingsUnavailable: Boolean = false, onEnabled: (Boolean) -> Unit, onTime: () -> Unit,
    onExact: (Boolean) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row { Text(stringResource(R.string.trial_final_check), Modifier.weight(1f)); Switch(enabled, onEnabled) }
        Text(stringResource(R.string.trial_final_check_sub), style = MaterialTheme.typography.bodySmall)
        if (enabled) {
            TextButton(onClick = onTime) { Text(stringResource(R.string.trial_final_time, time)) }
            Row { Text(stringResource(R.string.trial_final_exact), Modifier.weight(1f)); Switch(exact, onExact) }
            Text(stringResource(if (access) R.string.trial_final_access_yes else R.string.trial_final_access_no))
            Text(stringResource(if (exact && access) R.string.trial_final_precise else R.string.trial_final_ordinary))
            if (!notifications) Text(stringResource(R.string.trial_final_notifications_blocked), color = MaterialTheme.colorScheme.error)
            if (settingsUnavailable) Text(stringResource(R.string.trial_final_settings_unavailable))
            Text(stringResource(R.string.trial_final_caution), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun TrialDecisionCard(subscription: Subscription, onUpdate: (Subscription) -> Unit) {
    var confirm by remember(subscription.id) { mutableStateOf(false) }
    AppleCard {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(if (subscription.isTrialKept) R.string.trial_kept else R.string.trial_decision_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.trial_keep_explanation))
            TextButton(onClick = { onUpdate(if (subscription.isTrialKept) Trials.undoKeep(subscription) else Trials.keep(subscription)) }) {
                Text(stringResource(if (subscription.isTrialKept) R.string.trial_undo_keep else R.string.trial_keep))
            }
            TextButton(onClick = { confirm = true }) { Text(stringResource(R.string.trial_provider_cancelled)) }
        }
    }
    if (confirm) TrialCancelDialog({ confirm = false }, { confirm = false; onUpdate(Trials.cancel(subscription)) })
}

@Composable
fun TrialCancelDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trial_cancel_confirm_title)) },
        text = { Text(stringResource(R.string.trial_cancel_confirm_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.trial_cancel_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.trial_cancel_back)) } })
}
