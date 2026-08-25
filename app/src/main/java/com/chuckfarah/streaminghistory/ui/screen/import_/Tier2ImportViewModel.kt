package com.chuckfarah.streaminghistory.ui.screen.import_

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuckfarah.streaminghistory.data.repository.ViewingHistoryRepository
import com.chuckfarah.streaminghistory.domain.import_.ImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class Tier2ImportUiState {
    object Idle : Tier2ImportUiState()
    object Loading : Tier2ImportUiState()
    data class Success(
        val recordsUpgraded: Int,
        val recordsInserted: Int,
        val rowsSkipped: Int,
        /** Non-null when the import revealed multiple profiles (TS §7.2). */
        val profiles: List<String>,
    ) : Tier2ImportUiState()
    object AlreadyImported : Tier2ImportUiState()
    data class Failure(val message: String) : Tier2ImportUiState()
}

@HiltViewModel
class Tier2ImportViewModel @Inject constructor(
    private val repository: ViewingHistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<Tier2ImportUiState>(Tier2ImportUiState.Idle)
    val state: StateFlow<Tier2ImportUiState> = _state.asStateFlow()

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            _state.value = Tier2ImportUiState.Loading
            _state.value = when (val result = repository.importTier2(uri)) {
                is ImportResult.Tier2Success    -> Tier2ImportUiState.Success(
                    recordsUpgraded = result.recordsUpgraded,
                    recordsInserted = result.recordsInserted,
                    rowsSkipped     = result.rowsSkipped,
                    profiles        = result.profiles,
                )
                is ImportResult.AlreadyImported -> Tier2ImportUiState.AlreadyImported
                is ImportResult.Failure         -> Tier2ImportUiState.Failure(result.message)
                // Tier 1 Success should never be returned from importTier2
                else                            -> Tier2ImportUiState.Failure("Unexpected result")
            }
        }
    }

    fun reset() { _state.value = Tier2ImportUiState.Idle }
}
