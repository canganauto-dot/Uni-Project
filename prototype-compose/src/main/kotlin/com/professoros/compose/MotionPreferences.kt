package com.professoros.compose

import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import java.util.prefs.Preferences

enum class MotionMode { SYSTEM, REDUCED, FULL }
object MotionPreferences {
    private val preferences = Preferences.userRoot().node("com/professoros/compose-prototype")
    fun mode(): MotionMode = runCatching { MotionMode.valueOf(preferences.get("motion", "SYSTEM")) }.getOrDefault(MotionMode.SYSTEM)
    fun save(mode: MotionMode) { preferences.put("motion", mode.name); preferences.flush() }
    fun reduced(mode: MotionMode, systemReduced: Boolean) = mode == MotionMode.REDUCED || (mode == MotionMode.SYSTEM && systemReduced)
    /** Read-only Windows accessibility probe, off the UI thread; unknown platforms conservatively reduce motion. */
    fun systemReduced(): Boolean {
        System.getenv("PROFESSOR_OS_REDUCED_MOTION")?.let { return it != "0" }
        if (!System.getProperty("os.name").startsWith("Windows")) return true
        return try {
            val process = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command",
                "Add-Type -AssemblyName PresentationFramework; [System.Windows.SystemParameters]::ClientAreaAnimation").redirectErrorStream(true).start()
            if (!process.waitFor(5, TimeUnit.SECONDS)) { process.destroyForcibly(); true }
            else process.inputStream.bufferedReader().readText().trim() != "True"
        } catch (failure: Exception) { Logger.getLogger("MotionPreferences").warning("Cannot read OS motion preference: ${failure.message}"); true }
    }
}
