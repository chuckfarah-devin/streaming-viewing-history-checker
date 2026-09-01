package com.chuckfarah.streaminghistory.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuckfarah.streaminghistory.data.repository.ViewingHistoryRepository
import com.chuckfarah.streaminghistory.domain.model.ManualSearchRow
import com.chuckfarah.streaminghistory.domain.model.TitleCandidate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SearchUiState {
    object Idle : SearchUiState()
    object Loading : SearchUiState()
    /** Flat list of every accessible row that matches the query substring. */
    data class Results(val rows: List<ManualSearchRow>) : SearchUiState()
    // Confident and Ambiguous are kept as dead definitions so that AmbiguousScreen
    // and any future OCR-based paths can still reference this sealed class without
    // a separate hierarchy.
    /** @suppress kept for future use */
    data class Confident(val normalizedTitle: String) : SearchUiState()
    /** @suppress kept for future use */
    data class Ambiguous(val query: String, val candidates: List<TitleCandidate>) : SearchUiState()
    /** No records found — shown inline. */
    object NoMatch : SearchUiState()
    /** A technical error (DB failure, etc.). */
    data class Error(val message: String) : SearchUiState()
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: ViewingHistoryRepository,
) : ViewModel() {

    private val _searchState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    /**
     * Perform a manual substring search against the viewing-history database.
     *
     * Returns every accessible row whose normalized_title or
     * normalized_series_name contains the normalized query.  Results are
     * ordered by viewing date descending.  No TitleMatcher, confidence
     * threshold, series aggregation, or single-best-match selection is used.
     */
    fun search(query: String) {
        if (query.isBlank()) { _searchState.value = SearchUiState.Idle; return }
        viewModelScope.launch {
            _searchState.value = SearchUiState.Loading
            _searchState.value = try {
                val rows = repository.manualSearch(query)
                if (rows.isEmpty()) SearchUiState.NoMatch else SearchUiState.Results(rows)
            } catch (e: Exception) {
                SearchUiState.Error("Search failed: ${e.message}")
            }
        }
    }

    fun resetSearch() { _searchState.value = SearchUiState.Idle }
}
