package com.dere3046.checkarb

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val WORK_MODE = stringPreferencesKey("work_mode")
        val EXTRACTION_TOOL = stringPreferencesKey("extraction_tool")
        val SLOT_A_DEVICE_PATH = stringPreferencesKey("slot_a_device_path")
        val SLOT_B_DEVICE_PATH = stringPreferencesKey("slot_b_device_path")
    }

    val workModeFlow: Flow<String> = context.dataStore.data
        .map { it[Keys.WORK_MODE] ?: "NON_ROOT" }

    suspend fun setWorkMode(mode: String) {
        context.dataStore.edit { it[Keys.WORK_MODE] = mode }
    }

    val extractionToolFlow: Flow<String> = context.dataStore.data
        .map { it[Keys.EXTRACTION_TOOL] ?: "cat" }

    suspend fun setExtractionTool(tool: String) {
        context.dataStore.edit { it[Keys.EXTRACTION_TOOL] = tool }
    }

    val slotADevicePathFlow: Flow<String?> = context.dataStore.data
        .map { it[Keys.SLOT_A_DEVICE_PATH] }

    suspend fun setSlotADevicePath(path: String?) {
        context.dataStore.edit { it[Keys.SLOT_A_DEVICE_PATH] = path ?: "" }
    }

    val slotBDevicePathFlow: Flow<String?> = context.dataStore.data
        .map { it[Keys.SLOT_B_DEVICE_PATH] }

    suspend fun setSlotBDevicePath(path: String?) {
        context.dataStore.edit { it[Keys.SLOT_B_DEVICE_PATH] = path ?: "" }
    }
}