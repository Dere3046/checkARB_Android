package com.dere3046.checkarb

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.URL

class ArbDatabaseViewModel(application: Application) : AndroidViewModel(application) {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _database = MutableStateFlow<Map<String, ArbDatabase>>(emptyMap())
    val database: StateFlow<Map<String, ArbDatabase>> = _database.asStateFlow()

    private val _matchResult = MutableStateFlow<DeviceMatchResult?>(null)
    val matchResult: StateFlow<DeviceMatchResult?> = _matchResult.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun fetchDatabase() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val url = URL("https://oparb.pages.dev/database.json")
                val jsonStr = withContext(Dispatchers.IO) {
                    url.readText()
                }
                val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
                val data = json.decodeFromString<Map<String, ArbDatabase>>(jsonStr)
                _database.value = data
            } catch (e: Exception) {
                _error.value = "Failed to fetch database: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun matchCurrentDevice() {
        val db = _database.value
        if (db.isEmpty()) {
            fetchDatabase()
            viewModelScope.launch {
                kotlinx.coroutines.delay(1000)
                _matchResult.value = ArbDatabaseParser.matchDeviceInDatabase(_database.value)
            }
        } else {
            _matchResult.value = ArbDatabaseParser.matchDeviceInDatabase(db)
        }
    }

    fun clearMatchResult() {
        _matchResult.value = null
    }
}
