package com.professoros.compose

import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.*
import java.nio.file.Files

class BridgeTest {
    @get:Rule val folder=TemporaryFolder()
    @Test fun desktopRuntimeHasNoLegacyToolkitOrLauncher() {
        for(name in listOf("javafx.application.Application", "javafx.scene.control.Control",
            "com.sun.javafx.application.PlatformImpl", "com.professoros.app.ProfessorOsApplication",
            "com.professoros.compose.LegacyLauncher", "com.professoros.view.MainView",
            "com.professoros.controller.MainController", "com.professoros.theme.ThemeManager")) {
            assertFailsWith<ClassNotFoundException>("Obsolete runtime class: $name") {
                Class.forName(name,false,javaClass.classLoader)
            }
        }
        assertNull(javaClass.getResource("/css/theme.css"))
        assertNull(javaClass.getResource("/css/palette.css"))
        for(entry in System.getProperty("java.class.path").split(java.io.File.pathSeparator)) {
            assertFalse(java.io.File(entry).name.startsWith("javafx-"),"Obsolete runtime JAR: $entry")
        }
    }
    @Test fun savedJavaRecordsAndSelectionSurviveReconstruction() {
        val path=folder.root.toPath().resolve("records.db")
        val bridge=fixture(path);val snapshot=bridge.load(null)
        assertEquals(3,snapshot.courses);assertEquals(12,snapshot.overview.students());assertEquals(60.0,snapshot.overview.progress().completionPercent())
        val before=Files.readAllBytes(path)
        val arabic=bridge.load(snapshot.offerings[1].offering().id());assertTrue(arabic.selected!!.courseName().contains("هندسة"))
        assertContentEquals(before,Files.readAllBytes(path),"Loading the dashboard must not change academic data")
        val reopened=DashboardBridge(path);reopened.initialize();assertEquals(snapshot,reopened.load(null))
    }
    @Test fun emptyDatabaseHasRealEmptyState() {
        val bridge=DashboardBridge(folder.root.toPath().resolve("empty.db"));bridge.initialize()
        val value=bridge.load(null);assertEquals(0,value.courses);assertNull(value.selected);assertTrue(value.overview.upcoming().isEmpty())
    }
    @Test fun bundledLicensesAndFontsExist() {
        for(name in listOf("Manrope.ttf","IBMPlexSansArabic-Regular.ttf","IBMPlexSansArabic-SemiBold.ttf","Manrope-OFL.txt","IBM-Plex-Sans-Arabic-OFL.txt")) {
            val bytes=javaClass.getResourceAsStream("/fonts/$name")!!.use { it.readAllBytes() };assertTrue(bytes.size>1000)
            if(name.endsWith("txt")) assertTrue(bytes.toString(Charsets.UTF_8).contains("SIL OPEN FONT LICENSE"))
        }
    }
}
