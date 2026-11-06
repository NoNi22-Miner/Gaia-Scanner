package com.gaiascanner.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gaiascanner.app.data.db.parseIds
import com.gaiascanner.app.ui.camera.CameraScreen
import com.gaiascanner.app.ui.document.DocumentScreen
import com.gaiascanner.app.ui.editor.PageEditorScreen
import com.gaiascanner.app.ui.editor.PageEditorViewModel
import com.gaiascanner.app.ui.info.AboutScreen
import com.gaiascanner.app.ui.info.TutorialScreen
import com.gaiascanner.app.ui.jobs.JobsScreen
import com.gaiascanner.app.ui.library.LibraryScreen
import com.gaiascanner.app.ui.settings.SettingsScreen
import com.gaiascanner.app.ui.tools.ReduceSizeScreen
import com.gaiascanner.app.ui.tools.WatermarkScreen
import com.gaiascanner.app.ui.trash.TrashScreen

private object Routes {
    const val LIBRARY = "library"
    const val DOCUMENT = "document/{id}"
    const val JOBS = "jobs"
    const val SETTINGS = "settings"
    const val TRASH = "trash"
    const val TUTORIAL = "tutorial"
    const val ABOUT = "about"
    const val REDUCE = "reduce/{id}"
    const val WATERMARK = "watermark/{id}"

    fun reduce(id: Long) = "reduce/$id"
    fun watermark(id: Long) = "watermark/$id"

    /** doc = -1 starts a new image document. */
    const val CAMERA = "camera?doc={doc}"
    const val EDITOR = "edit/{doc}?pages={pages}&mode={mode}&auto={auto}"

    fun document(id: Long) = "document/$id"
    fun camera(documentId: Long?) = "camera?doc=${documentId ?: -1}"
    fun editor(documentId: Long, pageIds: List<Long>, mode: PageEditorViewModel.Mode, auto: Boolean) =
        "edit/$documentId?pages=${pageIds.joinToString(",")}&mode=${mode.name}&auto=$auto"
}

/** After the camera or editor: show the document (or the library if it was deleted). */
private fun NavHostController.showDocument(id: Long, deleted: Boolean = false) {
    if (deleted) {
        navigate(Routes.LIBRARY) { popUpTo(Routes.LIBRARY) { inclusive = true } }
    } else {
        navigate(Routes.document(id)) { popUpTo(Routes.LIBRARY) }
    }
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
                onOpenCamera = { nav.navigate(Routes.camera(null)) },
                onOpenTrash = { nav.navigate(Routes.TRASH) },
                onOpenTutorial = { nav.navigate(Routes.TUTORIAL) { launchSingleTop = true } },
                onOpenAbout = { nav.navigate(Routes.ABOUT) },
            )
        }
        composable(Routes.DOCUMENT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("id") ?: -1L
            DocumentScreen(
                documentId = id,
                onBack = { nav.popBackStack() },
                onOpenJobs = { nav.navigate(Routes.JOBS) },
                onOpenCamera = { nav.navigate(Routes.camera(it)) },
                onAdjustPage = { pageId -> nav.navigate(Routes.editor(id, listOf(pageId), PageEditorViewModel.Mode.ENHANCE, auto = false)) },
                onReduceSize = { nav.navigate(Routes.reduce(id)) },
                onWatermark = { nav.navigate(Routes.watermark(id)) },
            )
        }
        composable(
            Routes.CAMERA,
            arguments = listOf(navArgument("doc") { type = NavType.LongType; defaultValue = -1L }),
        ) { entry ->
            val doc = entry.arguments?.getLong("doc")?.takeIf { it >= 0 }
            CameraScreen(
                documentId = doc,
                // Photos open in the editor right away: crop first (corners found automatically), then enhance.
                onSaved = { docId, pageIds ->
                    if (pageIds.isEmpty()) {
                        nav.showDocument(docId)
                    } else {
                        nav.navigate(Routes.editor(docId, pageIds, PageEditorViewModel.Mode.CROP, auto = true)) {
                            popUpTo(Routes.CAMERA) { inclusive = true }
                        }
                    }
                },
                onClose = { nav.popBackStack() },
            )
        }
        composable(
            Routes.EDITOR,
            arguments = listOf(
                navArgument("doc") { type = NavType.LongType },
                navArgument("pages") { type = NavType.StringType; defaultValue = "" },
                navArgument("mode") { type = NavType.StringType; defaultValue = PageEditorViewModel.Mode.ENHANCE.name },
                navArgument("auto") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            val doc = entry.arguments?.getLong("doc") ?: -1L
            val pages = parseIds(entry.arguments?.getString("pages"))
            val mode = PageEditorViewModel.Mode.entries.firstOrNull { it.name == entry.arguments?.getString("mode") }
                ?: PageEditorViewModel.Mode.ENHANCE
            PageEditorScreen(
                documentId = doc,
                pageIds = pages,
                startMode = mode,
                autoDetect = entry.arguments?.getBoolean("auto") ?: false,
                onFinished = { deleted -> nav.showDocument(doc, deleted) },
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
        composable(Routes.TRASH) {
            TrashScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.TUTORIAL) {
            TutorialScreen(onDone = { nav.popBackStack() })
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.REDUCE, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            ReduceSizeScreen(documentId = entry.arguments?.getLong("id") ?: -1L, onBack = { nav.popBackStack() })
        }
        composable(Routes.WATERMARK, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            WatermarkScreen(documentId = entry.arguments?.getLong("id") ?: -1L, onBack = { nav.popBackStack() })
        }
    }
}
