package com.chuckfarah.streaminghistory.data.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User-level preferences stored in a private [SharedPreferences] file.
 */
@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val PREFS_NAME = "user_prefs"
        private const val KEY_VISION_ENABLED        = "vision_enabled"
        private const val KEY_VISION_CONSENT_GRANTED = "vision_consent_granted"

        /** Default: enhanced recognition is available but off until first consent. */
        private const val DEFAULT_VISION_ENABLED = true
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** Whether the Google Cloud Vision fallback is enabled by the user. */
    var visionEnabled: Boolean
        get() = prefs.getBoolean(KEY_VISION_ENABLED, DEFAULT_VISION_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_VISION_ENABLED, value).apply()

    /** Whether the user has granted one-time consent to send images to Google. */
    var visionConsentGranted: Boolean
        get() = prefs.getBoolean(KEY_VISION_CONSENT_GRANTED, false)
        set(value) = prefs.edit().putBoolean(KEY_VISION_CONSENT_GRANTED, value).apply()
}
