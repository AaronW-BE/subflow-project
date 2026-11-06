package org.dpdns.alwaysup.subflow.data.notifications

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.*

class TrialFinalDayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!acceptsAction(intent.action)) return
        val result = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                withTimeout(8_000) { TrialFinalDayScheduler.reconcile(context.applicationContext) }
            } catch (e: Exception) {
                Log.w("TrialFinalDayReceiver", "Check deferred to next scan", e)
            } finally {
                result.finish()
                scope.cancel()
            }
        }
    }
    companion object {
        internal fun acceptsAction(action: String?): Boolean = action in setOf(
            TrialFinalDayScheduler.ACTION_CHECK, Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
    }
}
