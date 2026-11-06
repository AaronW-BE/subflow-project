package org.dpdns.alwaysup.subflow

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.runBlocking
import org.dpdns.alwaysup.subflow.data.local.SubFlowDatabase
import org.dpdns.alwaysup.subflow.data.local.SubscriptionEntity
import org.dpdns.alwaysup.subflow.data.notifications.TrialFinalDayReceiver
import org.dpdns.alwaysup.subflow.data.notifications.TrialFinalDayScheduler as Scheduler
import org.dpdns.alwaysup.subflow.data.preferences.PreferencesManager
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrialFinalDaySchedulerTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val alarm get() = context.getSystemService(AlarmManager::class.java)
    private val prefs get() = PreferencesManager(context)
    private fun trial(date: LocalDate) = Subscription(id = "final-test", name = "Test", amount = 0.0,
        firstBillDate = date.toString(), nextBillDate = date.toString(),
        isTrial = true, trialEndDate = date.toString())
    @Before fun clear() = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        SubFlowDatabase.getDatabase(context).clearAllTables()
        context.getSharedPreferences("subflow_trial_final_state", Context.MODE_PRIVATE).edit().clear().commit()
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        prefs.setTrialFinalDayEnabled(true)
        prefs.setTrialFinalDayMinuteOfDay(0)
        prefs.setTrialFinalDayExactEnabled(false)
    }
    private suspend fun put(sub: Subscription) {
        SubFlowDatabase.getDatabase(context).subscriptionDao().insertAll(listOf(SubscriptionEntity.fromDomain(sub)))
    }
    @Test fun catchupDedupAndKeepDoNotResetSentState() = runBlocking {
        val sub = trial(LocalDate.now())
        put(sub)
        Scheduler.reconcile(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals(1, manager.activeNotifications.size)
        Scheduler.reconcile(context)
        assertEquals(1, manager.activeNotifications.size)
        put(sub.copy(trialKeepEndDate = sub.trialEndDate))
        Scheduler.reconcile(context)
        assertEquals(0, manager.activeNotifications.size)
        put(sub)
        Scheduler.reconcile(context)
        assertEquals(0, manager.activeNotifications.size)
    }
    @Test fun deniedExactStillSchedulesAndDisabledCancels() = runBlocking {
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false)
        prefs.setTrialFinalDayExactEnabled(true)
        assertFalse(Scheduler.canScheduleExact(context))
        put(trial(LocalDate.now().plusDays(1)))
        Scheduler.reconcile(context)
        assertEquals(1, shadowOf(alarm).scheduledAlarms.size)
        assertNotEquals(0L, shadowOf(alarm).scheduledAlarms.single().windowLengthMs)
        assertTrue(shadowOf(alarm).scheduledAlarms.single().operation!!.isImmutable)
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertTrue(Scheduler.canScheduleExact(context))
        Scheduler.reconcile(context)
        assertEquals(1, shadowOf(alarm).scheduledAlarms.size)
        assertEquals(0L, shadowOf(alarm).scheduledAlarms.single().windowLengthMs)
        prefs.setTrialFinalDayEnabled(false)
        Scheduler.reconcile(context)
        assertTrue(shadowOf(alarm).scheduledAlarms.isEmpty())
    }
    @Test fun blockedPermissionDoesNotConsumeAndSameTimeBatchIsProcessed() = runBlocking {
        put(trial(LocalDate.now()))
        put(trial(LocalDate.now()).copy(id = "second"))
        val manager = context.getSystemService(NotificationManager::class.java)
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        Scheduler.reconcile(context)
        assertEquals(0, manager.activeNotifications.size)
        assertTrue(context.getSharedPreferences("subflow_trial_final_state", Context.MODE_PRIVATE).all.isEmpty())
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        Scheduler.reconcile(context)
        assertEquals(2, manager.activeNotifications.size)
    }
    @Test fun permissionIntentAndReceiverBoundary() {
        val intent = Scheduler.requestExactPermissionIntent(context)
        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, intent.action)
        assertEquals("package:${context.packageName}", intent.data.toString())
        assertFalse(TrialFinalDayReceiver.acceptsAction(Intent.ACTION_VIEW))
        assertFalse(TrialFinalDayReceiver.acceptsAction(null))
        assertTrue(TrialFinalDayReceiver.acceptsAction(Intent.ACTION_TIME_CHANGED))
        val info = context.packageManager.getReceiverInfo(
            android.content.ComponentName(context, TrialFinalDayReceiver::class.java), 0)
        assertFalse(info.exported)
    }
}
