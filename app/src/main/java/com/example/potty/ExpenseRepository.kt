package com.example.potty

import kotlinx.coroutines.flow.Flow

class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val subscriptionDao: SubscriptionDao,
    private val collegeFeeDao: CollegeFeeDao,
    private val userProfileDao: UserProfileDao
) {
    fun getAllExpenses(googleId: String): Flow<List<ExpenseEntity>> = expenseDao.getAllExpenses(googleId)

    suspend fun insertExpense(expense: ExpenseEntity) {
        expenseDao.insertExpense(expense)
    }

    suspend fun deleteExpense(expense: ExpenseEntity) {
        expenseDao.deleteExpense(expense)
    }

    fun getAllSubscriptions(googleId: String): Flow<List<SubscriptionEntity>> = subscriptionDao.getAllSubscriptions(googleId)

    suspend fun insertSubscription(subscription: SubscriptionEntity) {
        subscriptionDao.insertSubscription(subscription)
    }

    suspend fun deleteSubscription(subscription: SubscriptionEntity) {
        subscriptionDao.deleteSubscription(subscription)
    }

    fun getAllFees(googleId: String): Flow<List<CollegeFeeEntity>> = collegeFeeDao.getAllFees(googleId)

    suspend fun insertFee(fee: CollegeFeeEntity) {
        collegeFeeDao.insertFee(fee)
    }

    suspend fun deleteFee(fee: CollegeFeeEntity) {
        collegeFeeDao.deleteFee(fee)
    }

    suspend fun deleteAllFees(googleId: String) {
        collegeFeeDao.deleteAllFees(googleId)
    }

    val activeProfile: Flow<UserProfileEntity?> = userProfileDao.getActiveProfile()

    suspend fun updateProfile(profile: UserProfileEntity) {
        // First log out everyone else to ensure single session
        userProfileDao.logoutAll()
        userProfileDao.insertProfile(profile.copy(isLoggedIn = true))
    }

    suspend fun logout() {
        userProfileDao.logoutAll()
    }

    suspend fun clearAllData(googleId: String) {
        expenseDao.deleteAllExpenses(googleId)
        subscriptionDao.deleteAllSubscriptions(googleId)
        collegeFeeDao.deleteAllFees(googleId)
        // Profile stays intact but logged out if you want
    }
}
