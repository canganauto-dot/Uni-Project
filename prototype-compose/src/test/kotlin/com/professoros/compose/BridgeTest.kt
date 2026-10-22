package com.professoros.compose

import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.*
import java.nio.file.Files
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

class BridgeTest {
    @get:Rule val folder=TemporaryFolder()
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
    @Test fun motionPolicyHonorsReducedAndSystem() {
        assertTrue(MotionPreferences.reduced(MotionMode.REDUCED,false));assertTrue(MotionPreferences.reduced(MotionMode.SYSTEM,true))
        assertFalse(MotionPreferences.reduced(MotionMode.SYSTEM,false));assertFalse(MotionPreferences.reduced(MotionMode.FULL,true))
    }
    @Test fun bundledLicensesAndFontsExist() {
        for(name in listOf("Manrope.ttf","IBMPlexSansArabic-Regular.ttf","IBMPlexSansArabic-SemiBold.ttf","Manrope-OFL.txt","IBM-Plex-Sans-Arabic-OFL.txt")) {
            val bytes=javaClass.getResourceAsStream("/fonts/$name")!!.use { it.readAllBytes() };assertTrue(bytes.size>1000)
            if(name.endsWith("txt")) assertTrue(bytes.toString(Charsets.UTF_8).contains("SIL OPEN FONT LICENSE"))
        }
    }
    @Test fun primaryTextColorsHaveReadableDarkContrast() {
        for (background in listOf(Ink.background,Ink.panel,Ink.raised,Ink.accentSurface,Ink.selected)) {
            for (foreground in listOf(Ink.text,Ink.muted,Ink.positive,Ink.violet)) {
                val ratio=(foreground.luminance()+0.05f)/(background.luminance()+0.05f)
                assertTrue(ratio>=4.5f,"Text contrast was $ratio for $foreground on $background")
            }
        }
    }
}
