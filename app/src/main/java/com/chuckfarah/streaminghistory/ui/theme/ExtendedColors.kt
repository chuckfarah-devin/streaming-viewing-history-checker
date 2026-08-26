package com.chuckfarah.streaminghistory.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colors beyond the Material 3 [androidx.compose.material3.ColorScheme].
 *
 * Material 3 does not define `success` or `warning` color roles, but Version 1.1
 * requires distinct semantic colors for success and warning states. This small
 * Compose-local system provides those roles without adding fake members to the
 * standard [ColorScheme].
 */
data class ExtendedColorScheme(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

val lightExtendedColorScheme = ExtendedColorScheme(
    success = success_light,
    onSuccess = onSuccess_light,
    successContainer = successContainer_light,
    onSuccessContainer = onSuccessContainer_light,
    warning = warning_light,
    onWarning = onWarning_light,
    warningContainer = warningContainer_light,
    onWarningContainer = onWarningContainer_light,
)

val darkExtendedColorScheme = ExtendedColorScheme(
    success = success_dark,
    onSuccess = onSuccess_dark,
    successContainer = successContainer_dark,
    onSuccessContainer = onSuccessContainer_dark,
    warning = warning_dark,
    onWarning = onWarning_dark,
    warningContainer = warningContainer_dark,
    onWarningContainer = onWarningContainer_dark,
)

val LocalExtendedColorScheme = staticCompositionLocalOf {
    error("No ExtendedColorScheme provided. Wrap your UI with StreamingHistoryTheme.")
}

/**
 * Convenience wrapper to provide [LocalExtendedColorScheme] alongside the
 * normal Material 3 theme. This is used from [StreamingHistoryTheme].
 */
@Composable
fun ProvideExtendedColorScheme(
    extendedColorScheme: ExtendedColorScheme,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalExtendedColorScheme provides extendedColorScheme,
        content = content,
    )
}
