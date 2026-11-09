package org.dpdns.alwaysup.subflow

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.dpdns.alwaysup.subflow.data.local.SubFlowDatabase
import org.dpdns.alwaysup.subflow.data.local.SubscriptionEntity
import org.dpdns.alwaysup.subflow.data.preferences.PreferencesManager
import org.dpdns.alwaysup.subflow.data.repository.SubscriptionRepository
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.model.TrialOutcome
import org.dpdns.alwaysup.subflow.domain.util.Trials
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class TrialKeepPersistenceTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private fun trial() = Subscription(
        id = "trial", name = "Trial", amount = 0.0,
        firstBillDate = "2026-09-01", nextBillDate = "2026-09-12",
        isTrial = true, trialEndDate = "2026-09-12", postTrialAmount = 19.99,
        updatedAt = 123L
    )

    @Test fun keepOnlyChangesDecisionAndLeavesFreeTrialAndRemindersAlone() {
        val original = trial()
        val kept = Trials.keep(original)
        assertTrue(kept.isTrialKept)
        assertEquals(original, kept.copy(trialKeepEndDate = ""))
        assertEquals(0.0, kept.monthlyAmount, 0.0)
        assertEquals(0.0, kept.yearlyAmount, 0.0)
        assertEquals(kept, SubscriptionRepository.normaliseForWrite(kept))
        val today = LocalDate.of(2026, 9, 11)
        assertEquals(Trials.decide(original, today = today), Trials.decide(kept, today = today))
        assertEquals(original, Trials.undoKeep(kept))
        assertEquals(kept, Trials.keep(kept))
    }

    @Test fun keepIsBoundToPendingNonblankEndDateAndExtensionResetsIt() {
        val kept = Trials.keep(trial())
        assertFalse(kept.copy(trialEndDate = "2026-09-20").isTrialKept)
        assertFalse(kept.copy(isTrial = false).isTrialKept)
        for (outcome in listOf(TrialOutcome.CANCELLED, TrialOutcome.CONVERTED)) {
            assertFalse(kept.copy(trialOutcome = outcome).isTrialKept)
        }
        assertFalse(Trials.keep(trial().copy(trialEndDate = " ")).isTrialKept)
        val extended = Trials.extendTo(kept, "2026-09-20")
        assertEquals("", extended.trialKeepEndDate)
        assertEquals(Trials.extendTo(trial(), "2026-09-20"), extended)
        assertFalse(extended.copy(trialEndDate = kept.trialEndDate).isTrialKept)
    }

    @Test fun preferencesDefaultPersistAndClampWithoutChangingLeadSettings() {
        context.getSharedPreferences(PreferencesManager.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = PreferencesManager(context)
        assertFalse(prefs.trialFinalDayEnabled.value)
        assertEquals(540, prefs.trialFinalDayMinuteOfDay.value)
        assertFalse(prefs.trialFinalDayExactEnabled.value)
        assertFalse(PreferencesManager.readTrialFinalDayEnabled(context))
        assertEquals(540, PreferencesManager.readTrialFinalDayMinuteOfDay(context))
        assertFalse(PreferencesManager.readTrialFinalDayExactEnabled(context))
        val leads = prefs.trialReminderLeads.value
        prefs.setTrialFinalDayEnabled(true)
        prefs.setTrialFinalDayExactEnabled(true)
        for ((input, expected) in listOf(-1 to 0, 0 to 0, 540 to 540, 1439 to 1439, Int.MAX_VALUE to 1439)) {
            prefs.setTrialFinalDayMinuteOfDay(input)
            assertEquals(expected, prefs.trialFinalDayMinuteOfDay.value)
            assertEquals(expected, PreferencesManager.readTrialFinalDayMinuteOfDay(context))
            assertEquals(expected, PreferencesManager(context).trialFinalDayMinuteOfDay.value)
        }
        assertTrue(PreferencesManager(context).trialFinalDayEnabled.value)
        assertTrue(PreferencesManager(context).trialFinalDayExactEnabled.value)
        assertTrue(PreferencesManager.readTrialFinalDayEnabled(context))
        assertTrue(PreferencesManager.readTrialFinalDayExactEnabled(context))
        assertEquals(leads, prefs.trialReminderLeads.value)
        prefs.setTrialFinalDayEnabled(false)
        prefs.setTrialFinalDayExactEnabled(false)
        assertFalse(PreferencesManager(context).trialFinalDayEnabled.value)
        assertFalse(PreferencesManager(context).trialFinalDayExactEnabled.value)
    }

    @Test fun entityRoundTripAndRepositoryUpsertBackupPreserveKeep() = runBlocking {
        val kept = Trials.keep(trial())
        assertEquals(kept, SubscriptionEntity.fromDomain(kept).toDomain())
        val repository = SubscriptionRepository(context)
        repository.clearAllData()
        assertTrue(repository.saveSubscription(kept, isPro = false).isSuccess)
        assertTrue(repository.getById(kept.id)!!.isTrialKept)
        val json = repository.exportBackupJson()
        assertTrue(json.contains("\"trialKeepEndDate\": \"2026-09-12\""))
        repository.clearAllData()
        assertTrue(repository.restoreBackupJson(json, isPro = false).isSuccess)
        assertTrue(repository.getById(kept.id)!!.isTrialKept)
        assertTrue(repository.saveSubscription(Trials.undoKeep(repository.getById(kept.id)!!), false).isSuccess)
        assertFalse(repository.getById(kept.id)!!.isTrialKept)
    }

    @Test fun legacyJsonWithoutKeepOrTrialFieldsRestoresAsUndecided() = runBlocking {
        val repository = SubscriptionRepository(context)
        for (version in listOf(1, 2)) {
            val tree = Gson().toJsonTree(trial()).asJsonObject
            tree.remove("trialKeepEndDate")
            if (version == 1) {
                listOf("isTrial", "trialEndDate", "trialConverts", "postTrialAmount", "postTrialCycle", "trialOutcome")
                    .forEach { tree.remove(it) }
            }
            val json = "{\"version\":$version,\"subscriptions\":[$tree]}"
            repository.clearAllData()
            assertTrue(repository.restoreBackupJson(json, isPro = false).isSuccess)
            val restored = repository.getById("trial")!!
            assertEquals("", restored.trialKeepEndDate)
            assertFalse(restored.isTrialKept)
        }
    }

    @Test fun actualV1AndV2SqliteMigrateToRoomV3WithoutLosingRows() = runBlocking {
        for (version in listOf(1, 2)) {
            val name = "trial-migration-$version.db"
            context.deleteDatabase(name)
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
                db.execSQL("""CREATE TABLE subscriptions (
                    id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, category TEXT NOT NULL,
                    amount REAL NOT NULL, currency TEXT NOT NULL, cycle TEXT NOT NULL,
                    first_bill_date TEXT NOT NULL, next_bill_date TEXT NOT NULL,
                    reminder_days_before INTEGER NOT NULL, is_active INTEGER NOT NULL,
                    color_hex TEXT NOT NULL, icon_url TEXT NOT NULL, notes TEXT NOT NULL,
                    updated_at INTEGER NOT NULL, is_deleted INTEGER NOT NULL)""")
                db.execSQL("""INSERT INTO subscriptions VALUES
                    ('old', 'Preserved', 'Streaming', 12.5, 'USD', 'monthly',
                    '2026-09-01', '2026-10-01', 3, 1, '#123456', '', 'notes', 123, 0)""")
                if (version == 2) {
                    db.execSQL("ALTER TABLE subscriptions ADD COLUMN is_trial INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE subscriptions ADD COLUMN trial_end_date TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE subscriptions ADD COLUMN trial_converts INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("ALTER TABLE subscriptions ADD COLUMN post_trial_amount REAL NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE subscriptions ADD COLUMN post_trial_cycle TEXT NOT NULL DEFAULT 'monthly'")
                    db.execSQL("ALTER TABLE subscriptions ADD COLUMN trial_outcome TEXT NOT NULL DEFAULT ''")
                    db.execSQL("UPDATE subscriptions SET is_trial=1, trial_end_date='2026-10-01', post_trial_amount=12.5")
                }
                db.version = version
            }
            val room = Room.databaseBuilder(context, SubFlowDatabase::class.java, name)
                .addMigrations(SubFlowDatabase.MIGRATION_1_2, SubFlowDatabase.MIGRATION_2_3)
                .build()
            try {
                val row = room.subscriptionDao().getById("old")!!
                assertEquals("Preserved", row.name)
                assertEquals("notes", row.notes)
                assertEquals(12.5, row.amount, 0.0)
                assertEquals(123L, row.updatedAt)
                assertEquals(version == 2, row.isTrial)
                assertEquals("", row.trialKeepEndDate)
                val kept = Trials.keep(trial()).copy(id = "old")
                room.subscriptionDao().insertOrUpdate(SubscriptionEntity.fromDomain(kept))
                assertEquals(kept, room.subscriptionDao().getById("old")!!.toDomain())
            } finally {
                room.close()
                context.deleteDatabase(name)
            }
        }
    }
}
