package com.chuckfarah.streaminghistory.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chuckfarah.streaminghistory.ui.screen.camera.CameraScreen
import com.chuckfarah.streaminghistory.ui.screen.home.HomeScreen
import com.chuckfarah.streaminghistory.ui.screen.import_.ImportHubScreen
import com.chuckfarah.streaminghistory.ui.screen.import_.ImportScreen
import com.chuckfarah.streaminghistory.ui.screen.import_.Tier2ImportScreen
import com.chuckfarah.streaminghistory.ui.screen.profile.ProfileSelectionScreen
import com.chuckfarah.streaminghistory.ui.screen.search.AmbiguousScreen
import com.chuckfarah.streaminghistory.ui.screen.search.ResultScreen
import com.chuckfarah.streaminghistory.ui.screen.search.SearchScreen
import com.chuckfarah.streaminghistory.ui.screen.settings.SettingsScreen

object Routes {
    const val HOME            = "home"
    const val IMPORT_HUB      = "import_hub"
    const val IMPORT_TIER1    = "import_tier1"
    const val IMPORT_TIER2    = "import_tier2"
    const val CAMERA          = "camera"
    /** profiles is a comma-separated list passed from the Tier2 import screen */
    const val PROFILE_SELECT  = "profile_select?profiles={profiles}"
    /** normalizedTitle is URL-encoded when passed as arg */
    const val RESULT          = "result/{normalizedTitle}"
    const val AMBIGUOUS       = "ambiguous/{query}"
    const val SEARCH          = "search"
    const val SETTINGS        = "settings"

    fun result(normalizedTitle: String)         = "result/$normalizedTitle"
    fun ambiguous(query: String)                = "ambiguous/$query"
    fun profileSelect(profiles: List<String> = emptyList()) =
        if (profiles.isEmpty()) "profile_select"
        else "profile_select?profiles=${profiles.joinToString(",")}"
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToImportHub    = { navController.navigate(Routes.IMPORT_HUB) },
                onNavigateToSearch       = { navController.navigate(Routes.SEARCH) },
                onNavigateToProfileSelect= { navController.navigate(Routes.profileSelect()) },
                onNavigateToCamera       = { navController.navigate(Routes.CAMERA) },
                onNavigateToSettings     = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.IMPORT_HUB) {
            ImportHubScreen(
                onBack            = { navController.popBackStack() },
                onNavigateToTier1 = { navController.navigate(Routes.IMPORT_TIER1) },
                onNavigateToTier2 = { navController.navigate(Routes.IMPORT_TIER2) },
            )
        }

        composable(Routes.IMPORT_TIER1) {
            ImportScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.IMPORT_TIER2) {
            Tier2ImportScreen(
                onBack = { navController.popBackStack() },
                onProfileSelectionNeeded = { profiles ->
                    navController.navigate(Routes.profileSelect(profiles)) {
                        // pop the Tier2 import screen so Back from profile select goes to Import hub
                        popUpTo(Routes.IMPORT_TIER2) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route     = Routes.PROFILE_SELECT,
            arguments = listOf(
                navArgument("profiles") {
                    type         = NavType.StringType
                    nullable     = true
                    defaultValue = null
                }
            ),
        ) { backStack ->
            val raw      = backStack.arguments?.getString("profiles")
            val profiles = raw?.split(",")?.filter { it.isNotBlank() }
            ProfileSelectionScreen(
                preloadedProfiles = profiles?.ifEmpty { null },
                onDone            = { navController.popBackStack() },
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                onResult    = { normTitle -> navController.navigate(Routes.result(normTitle)) },
                onAmbiguous = { query    -> navController.navigate(Routes.ambiguous(query)) },
                onBack      = { navController.popBackStack() },
            )
        }

        composable(
            route     = Routes.RESULT,
            arguments = listOf(navArgument("normalizedTitle") { type = NavType.StringType }),
        ) { backStack ->
            val normTitle = backStack.arguments?.getString("normalizedTitle") ?: ""
            ResultScreen(
                normalizedTitle = normTitle,
                onBack          = { navController.popBackStack() },
            )
        }

        composable(
            route     = Routes.AMBIGUOUS,
            arguments = listOf(navArgument("query") { type = NavType.StringType }),
        ) { backStack ->
            val query = backStack.arguments?.getString("query") ?: ""
            AmbiguousScreen(
                originalQuery = query,
                onSelected    = { normTitle -> navController.navigate(Routes.result(normTitle)) },
                onBack        = { navController.popBackStack() },
            )
        }

        composable(Routes.CAMERA) {
            CameraScreen(
                onBack = { navController.popBackStack() },
                onResult = { normalizedTitle ->
                    navController.popBackStack()
                    navController.navigate(Routes.result(normalizedTitle))
                },
                onSearchManual = {
                    navController.popBackStack()
                    navController.navigate(Routes.SEARCH)
                },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onImportHub = { navController.navigate(Routes.IMPORT_HUB) },
                onDeletedToHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}
