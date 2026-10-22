package com.professoros.compose

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import androidx.compose.ui.res.painterResource
import com.professoros.app.AppPaths
import kotlinx.coroutines.*
import java.nio.file.Path
import java.util.logging.Level
import java.util.logging.Logger

fun main() = application {
    val bridge = remember { DashboardBridge(AppPaths.databaseFile()) }
    var state by remember { mutableStateOf<DashboardSnapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<Long?>(null) }
    var revision by remember { mutableStateOf(0) }
    var mode by remember { mutableStateOf(MotionPreferences.mode()) }
    var systemReduced by remember { mutableStateOf(true) }
    var focused by remember { mutableStateOf(true) }
    var page by remember { mutableStateOf("Overview") }
    val scope = rememberCoroutineScope()
    val workspace = remember { WorkspaceController(WorkspaceServices(AppPaths.databaseFile()),scope) { revision++ } }
    LaunchedEffect(state?.selected?.offering()?.id()) { if(state!=null)workspace.select(state?.selected?.offering()?.id()) }
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
    LaunchedEffect(mode, focused) { if (focused) systemReduced = withContext(Dispatchers.IO) { MotionPreferences.systemReduced() } }
    // Optional development capture sizes; normal launches retain the same responsive window.
    val initialWidth = System.getProperty("professoros.preview.width")?.toIntOrNull()?.coerceIn(880, 3840) ?: 1360
    val initialHeight = System.getProperty("professoros.preview.height")?.toIntOrNull()?.coerceIn(660, 2160) ?: 860
    Window(onCloseRequest = {workspace.guard {exitApplication()}}, title = "Professor OS", icon = painterResource("professor-os.svg"),
        state = rememberWindowState(width = initialWidth.dp, height = initialHeight.dp)) {
        DisposableEffect(window) {
            window.minimumSize = java.awt.Dimension(880, 660)
            val listener = object : java.awt.event.WindowFocusListener {
                override fun windowGainedFocus(e: java.awt.event.WindowEvent?) { focused = true }
                override fun windowLostFocus(e: java.awt.event.WindowEvent?) { focused = false }
            }
            window.addWindowFocusListener(listener)
            onDispose { window.removeWindowFocusListener(listener) }
        }
        ProfessorDashboard(state, loading || workspace.busy, error, page, { next ->workspace.guard {page=next} }, MotionPreferences.reduced(mode, systemReduced) || !focused,
            mode, { next ->
                mode = next
                scope.launch { try { withContext(Dispatchers.IO) { MotionPreferences.save(next) } } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = "Motion preference could not be saved: ${failure.message}" } }
            }, { workspace.guard {revision++;workspace.refresh()} }, { id ->workspace.guard {selected=id;page="Teaching context"} }, {
                scope.launch { try { withContext(Dispatchers.IO) { openJavaFx() } } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = "Could not open JavaFX: ${failure.message}" } }
            },featureContent={ destination ->
                CompositionLocalProvider(LocalControlsEnabled provides !workspace.busy) {
                when(destination) {
                    "Teaching context" -> RosterScreen(workspace)
                    "Lectures" -> LectureScreen(workspace)
                    else -> CatalogScreen(destination,workspace) {id->workspace.guard {selected=id;page="Teaching context"}}
                }
                }
            })
        WorkspaceOverlays(workspace,MotionPreferences.reduced(mode,systemReduced)||!focused)
    }
}
private fun openJavaFx() {
    val java = Path.of(System.getProperty("java.home"), "bin", if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java").toString()
    ProcessBuilder(java, "--enable-native-access=ALL-UNNAMED", "-cp", System.getProperty("java.class.path"), "com.professoros.compose.LegacyLauncher")
        .inheritIO().start()
}
/** A separate process keeps JavaFX and Compose lifecycle/toolkit ownership independent. */
object LegacyLauncher {
    @JvmStatic fun main(args: Array<String>) {
        Class.forName("com.professoros.app.ProfessorOsApplication").getMethod("main", Array<String>::class.java).invoke(null, args)
    }
}
