package org.dpdns.alwaysup.subflow.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import org.dpdns.alwaysup.subflow.MainActivity
import org.dpdns.alwaysup.subflow.R
import org.dpdns.alwaysup.subflow.data.local.SubFlowDatabase
import org.dpdns.alwaysup.subflow.data.preferences.PreferencesManager
import org.dpdns.alwaysup.subflow.domain.util.CurrencyFormatter
import org.dpdns.alwaysup.subflow.domain.util.DateCalculators
import org.dpdns.alwaysup.subflow.domain.util.withAppLocale
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Scans once a day and raises an alert for every subscription whose renewal is
 * one of the user's configured lead times away.
 *
 * Free tier gets the single 1-day alert; Pro unlocks the 3- and 7-day tiers,
 * which is the reminder feature advertised on the paywall.
 */
class RenewalNotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val localized = context.withAppLocale()
        val dao = SubFlowDatabase.getDatabase(context).subscriptionDao()
        val subs = dao.getActiveSubscriptions().filter { it.isActive }

        createChannel(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            // Nothing to do, and retrying will not help until the user opts in.
            return Result.success()
        }

        val isPro = context
            .getSharedPreferences("subflow_auth_prefs", Context.MODE_PRIVATE)
            .getBoolean("is_pro", false)
        val leads = PreferencesManager.readLeadsStatic(context, isPro)
        val today = LocalDate.now().toString()
        val sentPrefs = context.getSharedPreferences(SENT_PREFS, Context.MODE_PRIVATE)

        for (sub in subs) {
            if (sub.reminderDaysBefore <= 0) continue
            val daysUntil = DateCalculators.calculateDaysUntil(sub.nextBillDate)
            if (daysUntil < 0) continue

            val matchedLead = leads.firstOrNull { it.toLong() == daysUntil } ?: continue

            // One alert per subscription per lead per day, even if the worker is
            // re-run after a reboot or a constraint retry.
            val sentKey = "${sub.id}_${matchedLead}"
            if (sentPrefs.getString(sentKey, null) == today) continue

            val renewalDate = runCatching {
                LocalDate.parse(sub.nextBillDate)
                    .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            }.getOrDefault(sub.nextBillDate)

            val title = if (daysUntil == 0L) {
                localized.getString(R.string.notif_renewal_title_today, sub.name)
            } else {
                localized.getString(R.string.notif_renewal_title, sub.name, daysUntil.toInt())
            }
            val body = localized.getString(
                R.string.notif_renewal_body,
                CurrencyFormatter.format(sub.amount, sub.currency),
                renewalDate
            )

            sendNotification(
                context = context,
                id = (sub.id + matchedLead).hashCode(),
                title = title,
                content = body,
                subscriptionId = sub.id
            )
            sentPrefs.edit().putString(sentKey, today).apply()
        }

        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "subflow_renewal_alerts"
        const val WORK_NAME = "subflow_daily_renewal_check"
        private const val SENT_PREFS = "subflow_notification_state"

        /**
         * Runs daily, targeting roughly 09:00 local time on the first run so the
         * alert lands during waking hours rather than whenever the app happened
         * to be installed.
         */
        fun scheduleDailyRenewalCheck(context: Context) {
            val request = PeriodicWorkRequestBuilder<RenewalNotificationWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(minutesUntilNextMorning(), TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        private fun minutesUntilNextMorning(): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
            return ((target.timeInMillis - now.timeInMillis) / 60_000L).coerceAtLeast(1L)
        }

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val localized = context.withAppLocale()
            val channel = NotificationChannel(
                CHANNEL_ID,
                localized.getString(R.string.notif_channel_renewal),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = localized.getString(R.string.notif_channel_renewal_desc)
                setShowBadge(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        fun sendNotification(
            context: Context,
            id: Int,
            title: String,
            content: String,
            subscriptionId: String? = null
        ) {
            createChannel(context)

            // Deep link straight to the subscription so the alert is actionable.
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (subscriptionId != null) {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse("subflow://subscription/$subscriptionId")
                }
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0xFF5856D6.toInt())
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            if (!hasNotificationPermission(context)) return
            runCatching {
                NotificationManagerCompat.from(context).notify(id, notification)
            }
        }

        fun hasNotificationPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

        fun sendTestNotification(context: Context) {
            val localized = context.withAppLocale()
            sendNotification(
                context = context,
                id = 9999,
                title = localized.getString(R.string.notif_test_title),
                content = localized.getString(R.string.notif_test_body)
            )
        }
    }
}
