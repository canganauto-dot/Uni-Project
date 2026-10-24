package com.professoros.compose

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import androidx.compose.ui.res.painterResource
import com.professoros.app.AppPaths
import kotlinx.coroutines.*
import androidx.compose.foundation.isSystemInDarkTheme
import java.util.logging.Level
import java.util.logging.Logger

fun main() = application {
    val bridge = remember { DashboardBridge(AppPaths.databaseFile()) }
    var state by remember { mutableStateOf<DashboardSnapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<Long?>(null) }
    var revision by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf("Overview") }
    val scope = rememberCoroutineScope()
    val preferences = remember { AppearanceSettings(AppPaths.databaseFile(),scope) }
    val workspace = remember { WorkspaceController(WorkspaceServices(AppPaths.databaseFile()),scope) { revision++ } }
    LaunchedEffect(state) { if(state!=null)workspace.select(state?.selected?.offering()?.id()) }
    LaunchedEffect(revision, selected) {
        loading = true; error = null
        try {
            state = withContext(Dispatchers.IO) { bridge.initialize(); bridge.load(selected) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Logger.getLogger("ComposeDashboard").log(Level.SEVERE, "Dashboard load failed", failure)
            error = "Could not load the local workspace. Check the data folder and launch log, then retry."
        } finally { loading = false }
    }
    // Optional development capture sizes; normal launches retain the same responsive window.
    val initialWidth = System.getProperty("professoros.preview.width")?.toIntOrNull()?.coerceIn(880, 3840) ?: 1360
    val initialHeight = System.getProperty("professoros.preview.height")?.toIntOrNull()?.coerceIn(660, 2160) ?: 860
    Window(onCloseRequest = {if(!preferences.saving)workspace.guard {exitApplication()}}, title = "Professor OS", icon = painterResource("professor-os.svg"),
        state = rememberWindowState(width = initialWidth.dp, height = initialHeight.dp)) {
        DisposableEffect(window) {
            window.minimumSize = java.awt.Dimension(880, 660)
            onDispose {}
        }
        CompositionLocalProvider(LocalPalette provides if(preferences.mode.dark(isSystemInDarkTheme())) DarkPalette else LightPalette) {
        ProfessorDashboard(state, loading || workspace.busy, error, page, { next ->workspace.guard {page=next} },
            { workspace.guard {revision++;workspace.refresh()} }, { id ->workspace.guard {selected=id;page="Teaching context"} },featureContent={ destination ->
                CompositionLocalProvider(LocalControlsEnabled provides !workspace.busy) {
                when(destination) {
                    "Settings" -> SettingsScreen(preferences) { workspace.guard {page="Terms"} }
                    "Teaching context" -> RosterScreen(workspace)
                    "Lectures" -> LectureScreen(workspace)
                    "Question Bank" -> QuestionScreen(workspace)
                    "Exam Builder" -> ExamScreen(workspace)
                    "Gradebook" -> GradebookScreen(workspace)
                    "Attendance" -> AttendanceScreen(workspace)
                    else -> CatalogScreen(destination,workspace) {id->workspace.guard {selected=id;page="Teaching context"}}
                }
                }
            })
        WorkspaceOverlays(workspace)
        }
    }
}
