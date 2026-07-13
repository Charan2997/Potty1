package com.example.potty

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class PreferenceManager(private val context: Context) {

    companion object {
        val DAILY_SUMMARY = booleanPreferencesKey("daily_summary")
        val WEEKLY_REPORT = booleanPreferencesKey("weekly_report")
        val SUBSCRIPTION_ALERTS = booleanPreferencesKey("subscription_alerts")
        val FEE_REMINDERS = booleanPreferencesKey("fee_reminders")
        val BUDGET_THRESHOLDS = booleanPreferencesKey("budget_thresholds")
        val GENERAL_REMINDERS = booleanPreferencesKey("general_reminders")
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val SECURITY_ENABLED = booleanPreferencesKey("security_enabled")
    }

    fun isNotificationEnabled(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[key] ?: if (key == SECURITY_ENABLED) false else true // Security off by default
        }
    }

    suspend fun setNotificationEnabled(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[key] = enabled
        }
    }
}
