package com.dere3046.checkarb.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dere3046.checkarb.WorkMode
import com.dere3046.checkarb.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository
) : ViewModel() {

    private val _workMode = MutableStateFlow(WorkMode.NON_ROOT)
    val workMode: StateFlow<WorkMode> = _workMode.asStateFlow()

    init {
        viewModelScope.launch {
            repository.workModeFlow.collect { mode ->
                _workMode.value = mode
            }
        }
    }

    fun setWorkMode(mode: WorkMode) {
        viewModelScope.launch {
            repository.setWorkMode(mode)
        }
    }
}