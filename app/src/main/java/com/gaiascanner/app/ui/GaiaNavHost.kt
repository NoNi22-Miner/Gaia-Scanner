package com.gaiascanner.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gaiascanner.app.ui.document.DocumentScreen
import com.gaiascanner.app.ui.jobs.JobsScreen
import com.gaiascanner.app.ui.library.LibraryScreen
import com.gaiascanner.app.ui.settings.SettingsScreen

private object Routes {
    const val LIBRARY = "library"
    const val DOCUMENT = "document/{id}"
    const val JOBS = "jobs"
    const val SETTINGS = "settings"
    fun document(id: Long) = "document/$id"
}

@Composable
fun GaiaNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onOpenDocument = { nav.navigate(Routes.document(it)) },
                onOpenJobs = { nav.navigate(Routes.JOBS) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.DOCUMENT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            DocumentScreen(
                documentId = entry.arguments?.getLong("id") ?: -1L,
                onBack = { nav.popBackStack() },
                onOpenJobs = { nav.navigate(Routes.JOBS) },
            )
        }
        composable(Routes.JOBS) {
            JobsScreen(
                onBack = { nav.popBackStack() },
                onOpenDocument = { nav.navigate(Routes.document(it)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
