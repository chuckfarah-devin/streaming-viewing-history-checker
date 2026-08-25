package com.chuckfarah.streaminghistory.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores and exposes the active Netflix profile selection (TS §7.2).
 *
 * The selected profile is persisted in SharedPreferences so it survives
 * app restarts.  A [StateFlow] allows the UI to react to changes immediately.
 *
 * Design:
 *  - null activeProfile → no Tier 2 data imported, or user wants "all profiles"
 *  - non-null activeProfile → filter all viewing-history queries to that profile
 *    (plus records with null profile_name, i.e., unreconciled Tier 1 rows)
 */
@Singleton
class ProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val PREFS_NAME          = "profile_prefs"
        private const val KEY_ACTIVE_PROFILE  = "active_profile"
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Initialise from persisted value so the StateFlow is correct on first collect
    private val _activeProfile = MutableStateFlow(
        prefs.getString(KEY_ACTIVE_PROFILE, null)
    )

    /** The currently selected profile name, or null if none is set. */
    val activeProfileFlow: StateFlow<String?> = _activeProfile.asStateFlow()

    val activeProfile: String? get() = _activeProfile.value

    /**
     * Set (or clear) the active profile and persist the value.
     * Emits on [activeProfileFlow] immediately.
     */
    fun setActiveProfile(profile: String?) {
        prefs.edit().apply {
            if (profile == null) remove(KEY_ACTIVE_PROFILE)
            else putString(KEY_ACTIVE_PROFILE, profile)
            apply()
        }
        _activeProfile.value = profile
    }

    /** Clear the active profile (e.g., when all history is cleared). */
    fun clearActiveProfile() = setActiveProfile(null)
}
