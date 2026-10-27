package com.professoros.compose

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import androidx.compose.ui.res.painterResource
import com.professoros.app.AppPaths
import androidx.compose.foundation.isSystemInDarkTheme

fun main() = application {
    var page by remember { mutableStateOf("Overview") }
    val scope = rememberCoroutineScope()
    val preferences = remember { AppearanceSettings(AppPaths.databaseFile(),scope) }
    val workspace = remember { WorkspaceController(WorkspaceServices(AppPaths.databaseFile()),scope) }
    LaunchedEffect(Unit) { workspace.refresh() }
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
        ProfessorDashboard(workspace.snapshot, workspace.busy, workspace.loadError, page, { next ->workspace.guard {page=next} },
            { workspace.guard {workspace.refresh()} }, { id ->workspace.select(id) {page="Teaching context"} },featureContent={ destination ->
                CompositionLocalProvider(LocalControlsEnabled provides !workspace.busy) {
                when(destination) {
                    "Settings" -> SettingsScreen(preferences) { workspace.guard {page="Terms"} }
                    "Teaching context" -> RosterScreen(workspace)
                    "Lectures" -> LectureScreen(workspace)
                    "Question Bank" -> QuestionScreen(workspace)
                    "Exam Builder" -> ExamScreen(workspace)
                    "Gradebook" -> GradebookScreen(workspace)
                    "Attendance" -> AttendanceScreen(workspace)
                    else -> CatalogScreen(destination,workspace) {id->workspace.select(id) {page="Teaching context"}}
                }
                }
            })
        WorkspaceOverlays(workspace)
        }
    }
}
