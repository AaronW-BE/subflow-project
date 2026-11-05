package org.dpdns.alwaysup.subflow.data.notifications

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.dpdns.alwaysup.subflow.MainActivity
import org.dpdns.alwaysup.subflow.R
import org.dpdns.alwaysup.subflow.data.local.SubFlowDatabase
import org.dpdns.alwaysup.subflow.data.preferences.PreferencesManager
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.util.FinalDayTrialChecks
import org.dpdns.alwaysup.subflow.domain.util.withAppLocale
import java.time.ZonedDateTime

object TrialFinalDayScheduler {
    internal const val ACTION_CHECK = "org.dpdns.alwaysup.subflow.TRIAL_FINAL_DAY"
    internal const val CHANNEL_ID = "subflow_trial_final_day"
    private const val NOTIFICATION_ID = 1
    private val mutex = Mutex()

    fun canScheduleExact(context: Context): Boolean = Build.VERSION.SDK_INT < 31 ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun requestExactPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))

    suspend fun reconcile(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val app = context.applicationContext
            val now = ZonedDateTime.now()
            val enabled = PreferencesManager.readTrialFinalDayEnabled(app)
            val minute = PreferencesManager.readTrialFinalDayMinuteOfDay(app)
            val subs = SubFlowDatabase.getDatabase(app).subscriptionDao().getActiveSubscriptions().map { it.toDomain() }
            val prefs = app.getSharedPreferences("subflow_trial_final_state", Context.MODE_PRIVATE)
            val manager = app.getSystemService(NotificationManager::class.java)
            val localized = app.withAppLocale()
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, localized.getString(R.string.notif_channel_trial_final), NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = localized.getString(R.string.notif_channel_trial_final_desc) })

            val validTags = subs.filter { FinalDayTrialChecks.target(it, enabled, minute, now) != null }
                .map { FinalDayTrialChecks.key(it) }.toSet()
            manager.activeNotifications.filter { it.tag?.startsWith("trial-final:") == true && it.tag !in validTags }
                .forEach { manager.cancel(it.tag, it.id) }

            var next: Long? = null
            for (sub in subs) {
                val target = FinalDayTrialChecks.target(sub, enabled, minute, now) ?: continue
                val key = FinalDayTrialChecks.key(sub)
                if (prefs.getBoolean(key, false)) continue
                if (FinalDayTrialChecks.isDue(sub, enabled, minute, now)) {
                    if (notify(app, sub)) prefs.edit().putBoolean(key, true).commit()
                } else {
                    val millis = target.toInstant().toEpochMilli()
                    next = next?.let { minOf(it, millis) } ?: millis
                }
            }
            val alarm = app.getSystemService(AlarmManager::class.java)
            val pending = PendingIntent.getBroadcast(app, 0,
                Intent(app, TrialFinalDayReceiver::class.java).setAction(ACTION_CHECK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarm.cancel(pending)
            next?.let { at ->
                if (PreferencesManager.readTrialFinalDayExactEnabled(app) && canScheduleExact(app)) {
                    try {
                        alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                    } catch (_: SecurityException) {
                        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                    }
                } else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun notify(context: Context, sub: Subscription): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!RenewalNotificationWorker.hasNotificationPermission(context) || !manager.areNotificationsEnabled() ||
            manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        val localized = context.withAppLocale()
        val key = FinalDayTrialChecks.key(sub)
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.Builder().scheme("subflow").authority("subscription").appendPath(sub.id).build()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val content = PendingIntent.getActivity(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val body = localized.getString(R.string.notif_trial_final_body)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(localized.getString(R.string.notif_trial_final_title, sub.name))
            .setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER).setContentIntent(content)
            .setOnlyAlertOnce(true).setAutoCancel(true).build()
        return try { manager.notify(key, NOTIFICATION_ID, notification); true } catch (_: SecurityException) { false }
    }
}
