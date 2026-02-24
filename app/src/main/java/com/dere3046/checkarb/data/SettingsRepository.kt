package com.dere3046.checkarb.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dere3046.checkarb.WorkMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object PreferencesKeys {
        val WORK_MODE = stringPreferencesKey("work_mode")
        val EXTRACTION_TOOL = stringPreferencesKey("extraction_tool")
        val LOG_BUFFER_SIZE = intPreferencesKey("log_buffer_size")
        val LOG_ENABLED = booleanPreferencesKey("log_enabled")
    }

    val workModeFlow: Flow<WorkMode> = context.dataStore.data
        .map { preferences ->
            val modeName = preferences[PreferencesKeys.WORK_MODE] ?: WorkMode.NON_ROOT.name
            try {
                WorkMode.valueOf(modeName)
            } catch (e: IllegalArgumentException) {
                WorkMode.NON_ROOT
            }
        }

    val extractionToolFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.EXTRACTION_TOOL] ?: "cat"
        }

    val logBufferSizeFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.LOG_BUFFER_SIZE] ?: 500
        }

    val logEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.LOG_ENABLED] ?: false
        }

    suspend fun setWorkMode(mode: WorkMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WORK_MODE] = mode.name
        }
    }

    suspend fun setExtractionTool(tool: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.EXTRACTION_TOOL] = tool
        }
    }

    suspend fun setLogBufferSize(size: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LOG_BUFFER_SIZE] = size
        }
    }

    suspend fun setLogEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LOG_ENABLED] = enabled
        }
    }
}