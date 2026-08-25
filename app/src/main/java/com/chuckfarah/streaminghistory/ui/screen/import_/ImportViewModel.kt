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

sealed class ImportUiState {
    object Idle : ImportUiState()
    object Loading : ImportUiState()
    data class Success(
        val recordsImported: Int,
        val rowsSkipped: Int,
    ) : ImportUiState()
    object AlreadyImported : ImportUiState()
    data class Failure(val message: String) : ImportUiState()
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: ViewingHistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            _state.value = ImportUiState.Loading
            _state.value = when (val result = repository.importTier1Csv(uri)) {
                is ImportResult.Success         -> ImportUiState.Success(
                    recordsImported = result.recordsImported,
                    rowsSkipped     = result.rowsSkipped,
                )
                is ImportResult.AlreadyImported -> ImportUiState.AlreadyImported
                is ImportResult.Failure         -> ImportUiState.Failure(result.message)
                // Tier2Success is never returned by importTier1Csv
                else                            -> ImportUiState.Failure("Unexpected result type")
            }
        }
    }

    fun reset() { _state.value = ImportUiState.Idle }
}
