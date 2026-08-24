package com.chuckfarah.streaminghistory.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chuckfarah.streaminghistory.ui.screen.home.HomeScreen
import com.chuckfarah.streaminghistory.ui.screen.import_.ImportScreen
import com.chuckfarah.streaminghistory.ui.screen.search.AmbiguousScreen
import com.chuckfarah.streaminghistory.ui.screen.search.ResultScreen
import com.chuckfarah.streaminghistory.ui.screen.search.SearchScreen

object Routes {
    const val HOME      = "home"
    const val IMPORT    = "import"
    const val SEARCH    = "search"
    /** normalizedTitle is URL-encoded when passed as arg */
    const val RESULT    = "result/{normalizedTitle}"
    const val AMBIGUOUS = "ambiguous/{query}"

    fun result(normalizedTitle: String)    = "result/$normalizedTitle"
    fun ambiguous(query: String)           = "ambiguous/$query"
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToImport = { navController.navigate(Routes.IMPORT) },
                onNavigateToSearch = { navController.navigate(Routes.SEARCH) },
            )
        }

        composable(Routes.IMPORT) {
            ImportScreen(
                onBack = { navController.popBackStack() },
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
                originalQuery   = query,
                onSelected      = { normTitle -> navController.navigate(Routes.result(normTitle)) },
                onBack          = { navController.popBackStack() },
            )
        }
    }
}
