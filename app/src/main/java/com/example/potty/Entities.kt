package com.example.potty

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val googleId: String = "local_user", // Link to specific user
    val timestamp: String, // ISO 8601
    val amount: Double,
    val primaryCategory: String, // Food & Mess, Stationary, Travel, Misc
    val subcategory: String,
    val description: String,
    val tags: String, // comma-separated
    val isIncome: Boolean = false,
    val iconName: String = "",
    val paymentMode: String = "UPI" // UPI, Card, Cash, Netbanking
)

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val googleId: String = "local_user", // Link to specific user
    val name: String,
    val cost: Double,
    val renewalInterval: String, // MONTHLY, YEARLY, ONE_TIME
    val validUntilDate: String, // ISO date string
    val isAutoRenewing: Boolean,
    val category: String = "Entertainment",
    val isTrading: Boolean = false,
    val tradingType: String = "" // Stocks, F&O, Mutual Funds
)

@Entity(tableName = "college_fees")
data class CollegeFeeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val googleId: String = "local_user", // Link to specific user
    val feeType: String, // TUITION, HOSTEL
    val collegeYear: Int, // 1, 2, 3, 4
    val baseAmount: Double,
    val calculatedAmount: Double,
    val inflationRate: Double = 8.0,
    val isPaid: Boolean,
    val isHostelCompounded: Boolean = true
)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val googleId: String = "local_user", // Use googleId as PK
    val fullName: String = "",
    val college: String = "My University",
    val course: String = "B.Tech",
    val currentYear: Int = 1,
    val profilePicUri: String? = null,
    val isLoggedIn: Boolean = false // Track if this user is active session
)
