package com.chuckfarah.streaminghistory.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuckfarah.streaminghistory.data.repository.ViewingHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DeleteHistoryState {
    object Idle : DeleteHistoryState()
    object Confirming : DeleteHistoryState()
    object Deleting : DeleteHistoryState()
    object Deleted : DeleteHistoryState()
    data class Error(val message: String) : DeleteHistoryState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ViewingHistoryRepository,
) : ViewModel() {

    /** Active profile name, or null if none is set. */
    val activeProfile: StateFlow<String?> = repository.activeProfileFlow

    private val _availableProfiles = MutableStateFlow<List<String>>(emptyList())
    val availableProfiles: StateFlow<List<String>> = _availableProfiles.asStateFlow()

    private val _deleteState = MutableStateFlow<DeleteHistoryState>(DeleteHistoryState.Idle)
    val deleteState: StateFlow<DeleteHistoryState> = _deleteState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _availableProfiles.value = repository.getAvailableProfiles()
        }
    }

    fun requestDelete() {
        _deleteState.value = DeleteHistoryState.Confirming
    }

    fun confirmDelete() {
        viewModelScope.launch {
            _deleteState.value = DeleteHistoryState.Deleting
            try {
                repository.clearAllHistory()
                _availableProfiles.value = emptyList()
                _deleteState.value = DeleteHistoryState.Deleted
            } catch (e: Exception) {
                _deleteState.value = DeleteHistoryState.Error(e.message ?: "Delete failed")
            }
        }
    }

    fun dismissDelete() {
        if (_deleteState.value !is DeleteHistoryState.Deleting) {
            _deleteState.value = DeleteHistoryState.Idle
        }
    }
}
