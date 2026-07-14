package com.example.potty

import android.app.Application

class PottyApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { 
        ExpenseRepository(
            database.expenseDao(), 
            database.subscriptionDao(), 
            database.collegeFeeDao(),
            database.userProfileDao()
        ) 
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize SQLCipher native library for modern SDK
        System.loadLibrary("sqlcipher")
    }
}
