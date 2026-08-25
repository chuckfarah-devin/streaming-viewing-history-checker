package com.chuckfarah.streaminghistory.ui.screen.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuckfarah.streaminghistory.data.repository.ViewingHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ViewingHistoryRepository,
) : ViewModel() {

    private val _profiles    = MutableStateFlow<List<String>>(emptyList())
    val profiles: StateFlow<List<String>> = _profiles.asStateFlow()

    val activeProfile = repository.activeProfileFlow

    /** Load the available profiles from the database. */
    fun loadProfiles() {
        viewModelScope.launch {
            _profiles.value = repository.getAvailableProfiles()
        }
    }

    /** Load with a pre-known list (e.g., passed from the import screen). */
    fun loadProfiles(knownProfiles: List<String>) {
        _profiles.value = knownProfiles
    }

    fun selectProfile(profile: String) {
        repository.setActiveProfile(profile)
    }
}
