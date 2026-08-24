package com.chuckfarah.streaminghistory.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuckfarah.streaminghistory.data.repository.ViewingHistoryRepository
import com.chuckfarah.streaminghistory.domain.model.MatchResult
import com.chuckfarah.streaminghistory.domain.model.TitleCandidate
import com.chuckfarah.streaminghistory.domain.model.ViewingResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Search screen state ───────────────────────────────────────────────────────

sealed class SearchUiState {
    object Idle : SearchUiState()
    object Loading : SearchUiState()
    /** Single confident match; navigate to result screen. */
    data class Confident(val normalizedTitle: String) : SearchUiState()
    /** Multiple plausible candidates; navigate to ambiguous screen. */
    data class Ambiguous(val query: String, val candidates: List<TitleCandidate>) : SearchUiState()
    /** No match found — shown inline, NOT as a lookup error. */
    object NoMatch : SearchUiState()
    /** A technical error (DB failure etc.). Never displayed as "not watched." */
    data class Error(val message: String) : SearchUiState()
}

// ── Result screen state ───────────────────────────────────────────────────────

sealed class ResultUiState {
    object Loading : ResultUiState()
    data class Success(val result: ViewingResult.Watched) : ResultUiState()
    object NotWatched : ResultUiState()
    data class Error(val message: String) : ResultUiState()
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: ViewingHistoryRepository,
) : ViewModel() {

    private val _searchState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private val _resultState = MutableStateFlow<ResultUiState>(ResultUiState.Loading)
    val resultState: StateFlow<ResultUiState> = _resultState.asStateFlow()

    // ── Search ────────────────────────────────────────────────────────────────

    fun search(query: String) {
        if (query.isBlank()) { _searchState.value = SearchUiState.Idle; return }
        viewModelScope.launch {
            _searchState.value = SearchUiState.Loading
            _searchState.value = try {
                when (val match = repository.getMatchResult(query)) {
                    is MatchResult.None       -> SearchUiState.NoMatch
                    is MatchResult.Confident  -> SearchUiState.Confident(match.normalizedTitle)
                    is MatchResult.Ambiguous  -> SearchUiState.Ambiguous(query, match.candidates)
                }
            } catch (e: Exception) {
                SearchUiState.Error("Search failed: ${e.message}")
            }
        }
    }

    fun resetSearch() { _searchState.value = SearchUiState.Idle }

    // ── Result lookup ─────────────────────────────────────────────────────────

    fun loadResult(normalizedTitle: String) {
        viewModelScope.launch {
            _resultState.value = ResultUiState.Loading
            _resultState.value = when (val r = repository.lookupByNormalizedTitle(normalizedTitle)) {
                is ViewingResult.Watched  -> ResultUiState.Success(r)
                is ViewingResult.NotWatched -> ResultUiState.NotWatched
                is ViewingResult.Error    -> ResultUiState.Error(r.message)
            }
        }
    }
}
