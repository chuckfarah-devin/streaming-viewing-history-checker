package com.chuckfarah.streaminghistory.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuckfarah.streaminghistory.data.repository.ViewingHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecentEntry(val displayTitle: String, val viewDate: String)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ViewingHistoryRepository,
) : ViewModel() {

    private val _totalRecords = MutableStateFlow(0)
    val totalRecords: StateFlow<Int> = _totalRecords.asStateFlow()

    private val _recentEntries = MutableStateFlow<List<RecentEntry>>(emptyList())
    val recentEntries: StateFlow<List<RecentEntry>> = _recentEntries.asStateFlow()

    /** The currently-active Netflix profile name, or null if none is set. */
    val activeProfile: StateFlow<String?> = repository.activeProfileFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.activeProfileFlow.value)

    /** Whether any Tier 2 profiles exist in the database. */
    private val _availableProfiles = MutableStateFlow<List<String>>(emptyList())
    val availableProfiles: StateFlow<List<String>> = _availableProfiles.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _totalRecords.value    = repository.getTotalRecordCount()
            _recentEntries.value   = repository.getRecentViewings(10)
                .map { (title, date) -> RecentEntry(title, date) }
            _availableProfiles.value = repository.getAvailableProfiles()
        }
    }
}
