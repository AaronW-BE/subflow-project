package org.dpdns.alwaysup.subflow.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.dpdns.alwaysup.subflow.domain.model.BillingCycle
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.model.TrialOutcome
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "amount") val amount: Double,
    @ColumnInfo(name = "currency") val currency: String,
    @ColumnInfo(name = "cycle") val cycle: String,
    @ColumnInfo(name = "first_bill_date") val firstBillDate: String,
    @ColumnInfo(name = "next_bill_date") val nextBillDate: String,
    @ColumnInfo(name = "reminder_days_before") val reminderDaysBefore: Int,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    @ColumnInfo(name = "color_hex") val colorHex: String,
    @ColumnInfo(name = "icon_url") val iconUrl: String,
    @ColumnInfo(name = "notes") val notes: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean,
    // Added in schema v2 (BIN-15). Every default here has to match the DEFAULT
    // in MIGRATION_1_2 exactly, or Room rejects the migrated database at open.
    @ColumnInfo(name = "is_trial", defaultValue = "0") val isTrial: Boolean = false,
    @ColumnInfo(name = "trial_end_date", defaultValue = "''") val trialEndDate: String = "",
    @ColumnInfo(name = "trial_converts", defaultValue = "1") val trialConverts: Boolean = true,
    @ColumnInfo(name = "post_trial_amount", defaultValue = "0") val postTrialAmount: Double = 0.0,
    @ColumnInfo(name = "post_trial_cycle", defaultValue = "'monthly'") val postTrialCycle: String = "monthly",
    @ColumnInfo(name = "trial_outcome", defaultValue = "''") val trialOutcome: String = ""
) {
    fun toDomain(): Subscription = Subscription(
        id = id,
        name = name,
        category = category,
        amount = amount,
        currency = currency,
        cycle = BillingCycle.fromKey(cycle),
        firstBillDate = firstBillDate,
        nextBillDate = nextBillDate,
        reminderDaysBefore = reminderDaysBefore,
        isActive = isActive,
        colorHex = colorHex,
        iconUrl = iconUrl,
        notes = notes,
        updatedAt = updatedAt,
        isDeleted = isDeleted,
        isTrial = isTrial,
        trialEndDate = trialEndDate,
        trialConverts = trialConverts,
        postTrialAmount = postTrialAmount,
        postTrialCycle = BillingCycle.fromKey(postTrialCycle),
        trialOutcome = TrialOutcome.fromKey(trialOutcome)
    )

    companion object {
        fun fromDomain(d: Subscription): SubscriptionEntity = SubscriptionEntity(
            id = d.id,
            name = d.name,
            category = d.category,
            amount = d.amount,
            currency = d.currency,
            cycle = d.cycle.key,
            firstBillDate = d.firstBillDate,
            nextBillDate = d.nextBillDate,
            reminderDaysBefore = d.reminderDaysBefore,
            isActive = d.isActive,
            colorHex = d.colorHex,
            iconUrl = d.iconUrl,
            notes = d.notes,
            updatedAt = d.updatedAt,
            isDeleted = d.isDeleted,
            isTrial = d.isTrial,
            trialEndDate = d.trialEndDate,
            trialConverts = d.trialConverts,
            postTrialAmount = d.postTrialAmount,
            postTrialCycle = d.postTrialCycle.key,
            trialOutcome = d.trialOutcome.key
        )
    }
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions WHERE is_deleted = 0 ORDER BY next_bill_date ASC")
    fun observeActiveSubscriptions(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE is_deleted = 0 ORDER BY next_bill_date ASC")
    suspend fun getActiveSubscriptions(): List<SubscriptionEntity>

    @Query("SELECT COUNT(*) FROM subscriptions WHERE is_deleted = 0 AND is_active = 1")
    suspend fun getActiveCount(): Int

    @Query("SELECT * FROM subscriptions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SubscriptionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(subscription: SubscriptionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subscriptions: List<SubscriptionEntity>)

    @Query("UPDATE subscriptions SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun markDeleted(id: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE subscriptions SET is_deleted = 0, updated_at = :now WHERE id = :id")
    suspend fun restoreDeleted(id: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM subscriptions")
    suspend fun clearAll()

    @Query("SELECT * FROM subscriptions WHERE updated_at > :sinceTimestamp")
    suspend fun getModifiedSince(sinceTimestamp: Long): List<SubscriptionEntity>
}

@Database(entities = [SubscriptionEntity::class], version = 2, exportSchema = false)
abstract class SubFlowDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao

    companion object {
        @Volatile
        private var INSTANCE: SubFlowDatabase? = null

        /**
         * Trial tracking (BIN-15). Six added columns, every one of them with a
         * default, so no existing row is read or rewritten.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN is_trial INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN trial_end_date TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN trial_converts INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN post_trial_amount REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN post_trial_cycle TEXT NOT NULL DEFAULT 'monthly'")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN trial_outcome TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): SubFlowDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SubFlowDatabase::class.java,
                    "subflow_local.db"
                )
                    // Deliberately no fallbackToDestructiveMigration(): this
                    // database is the user's own record of what they pay for
                    // and there is no copy of it anywhere else. A migration
                    // bug has to fail loudly, not quietly empty the app.
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
