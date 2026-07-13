package com.example.potty

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [
        ExpenseEntity::class,
        SubscriptionEntity::class,
        CollegeFeeEntity::class,
        UserProfileEntity::class
    ],
    version = 12, // Stable build version. See comments below for Encryption.
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun collegeFeeDao(): CollegeFeeDao
    abstract fun userProfileDao(): UserProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // TO ACTIVATE DATABASE ENCRYPTION:
                // 1. Uncomment the lines below and the SupportOpenHelperFactory import.
                // 2. Ensure net.zetetic:android-database-sqlcipher is resolved in build.gradle.
                // val passphrase = DatabaseKeyManager.getOrCreateKey()
                // val factory = SupportOpenHelperFactory(passphrase)
                
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "potty_database"
                )
                // .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE googleId = :googleId ORDER BY timestamp DESC")
    fun getAllExpenses(googleId: String): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE googleId = :googleId")
    suspend fun deleteAllExpenses(googleId: String)

    @Query("SELECT SUM(amount) FROM expenses WHERE googleId = :googleId AND primaryCategory = :category")
    suspend fun getTotalByCategory(googleId: String, category: String): Double?
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions WHERE googleId = :googleId")
    fun getAllSubscriptions(googleId: String): Flow<List<SubscriptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(subscription: SubscriptionEntity)

    @Update
    suspend fun updateSubscription(subscription: SubscriptionEntity)

    @Delete
    suspend fun deleteSubscription(subscription: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE googleId = :googleId")
    suspend fun deleteAllSubscriptions(googleId: String)
}

@Dao
interface CollegeFeeDao {
    @Query("SELECT * FROM college_fees WHERE googleId = :googleId")
    fun getAllFees(googleId: String): Flow<List<CollegeFeeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFee(fee: CollegeFeeEntity)

    @Update
    suspend fun updateFee(fee: CollegeFeeEntity)

    @Delete
    suspend fun deleteFee(fee: CollegeFeeEntity)

    @Query("DELETE FROM college_fees WHERE googleId = :googleId")
    suspend fun deleteAllFees(googleId: String)
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE isLoggedIn = 1 LIMIT 1")
    fun getActiveProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE googleId = :googleId")
    suspend fun getProfileById(googleId: String): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfileEntity)

    @Query("UPDATE user_profile SET isLoggedIn = 0")
    suspend fun logoutAll()

    @Query("DELETE FROM user_profile WHERE googleId = :googleId")
    suspend fun deleteProfile(googleId: String)
}
