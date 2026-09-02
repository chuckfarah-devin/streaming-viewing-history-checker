package com.chuckfarah.streaminghistory.ui.screen.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuckfarah.streaminghistory.data.repository.ViewingHistoryRepository
import com.chuckfarah.streaminghistory.domain.model.ViewingResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ResultUiState {
    object Loading : ResultUiState()
    data class Success(val result: ViewingResult.Watched) : ResultUiState()
    data class NotWatched(
        val displayTitle: String,
        val normalizedTitle: String,
    ) : ResultUiState()
    data class Error(val message: String) : ResultUiState()
}

@HiltViewModel
class ResultViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: ViewingHistoryRepository,
) : ViewModel() {

    val normalizedTitle: String?
        get() = savedStateHandle.get<String>("normalizedTitle")

    /** Active profile name, or null if none is set. */
    val activeProfile = repository.activeProfileFlow

    private val _availableProfiles = MutableStateFlow<List<String>>(emptyList())
    val availableProfiles: StateFlow<List<String>> = _availableProfiles.asStateFlow()

    private val _resultState = MutableStateFlow<ResultUiState>(ResultUiState.Loading)
    val resultState: StateFlow<ResultUiState> = _resultState.asStateFlow()

    init {
        viewModelScope.launch {
            _availableProfiles.value = repository.getAvailableProfiles()
        }
    }

    fun loadResult() {
        val key = normalizedTitle ?: return
        viewModelScope.launch {
            _resultState.value = ResultUiState.Loading
            _resultState.value = when (val r = repository.lookupByNormalizedTitle(key)) {
                is ViewingResult.Watched    -> ResultUiState.Success(r)
                is ViewingResult.NotWatched -> ResultUiState.NotWatched(r.displayTitle, r.normalizedTitle)
                is ViewingResult.Error      -> ResultUiState.Error(r.message)
            }
        }
    }

    fun selectProfile(profile: String?) {
        repository.setActiveProfile(profile)
        loadResult()
    }
}
