package com.professoros.compose

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
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
    Window(onCloseRequest = ::exitApplication, title = "Professor OS · Compose evaluation", state = rememberWindowState(width = 1440.dp, height = 960.dp)) {
        DisposableEffect(window) {
            window.minimumSize = java.awt.Dimension(880, 660)
            val listener = object : java.awt.event.WindowFocusListener {
                override fun windowGainedFocus(e: java.awt.event.WindowEvent?) { focused = true }
                override fun windowLostFocus(e: java.awt.event.WindowEvent?) { focused = false }
            }
            window.addWindowFocusListener(listener)
            onDispose { window.removeWindowFocusListener(listener) }
        }
        ProfessorDashboard(state, loading, error, page, { page = it }, MotionPreferences.reduced(mode, systemReduced) || !focused,
            mode, { next ->
                mode = next
                scope.launch { try { withContext(Dispatchers.IO) { MotionPreferences.save(next) } } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = "Motion preference could not be saved: ${failure.message}" } }
            }, { revision++ }, { selected = it; page = "Overview" }, {
                scope.launch { try { withContext(Dispatchers.IO) { openJavaFx() } } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = "Could not open JavaFX: ${failure.message}" } }
            })
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
