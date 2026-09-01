package org.dpdns.alwaysup.subflow.data.local

import android.content.Context
import androidx.room.*
import org.dpdns.alwaysup.subflow.domain.model.BillingCycle
import org.dpdns.alwaysup.subflow.domain.model.Subscription
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
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean
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
        isDeleted = isDeleted
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
            isDeleted = d.isDeleted
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

@Database(entities = [SubscriptionEntity::class], version = 1, exportSchema = false)
abstract class SubFlowDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao

    companion object {
        @Volatile
        private var INSTANCE: SubFlowDatabase? = null

        fun getDatabase(context: Context): SubFlowDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SubFlowDatabase::class.java,
                    "subflow_local.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
