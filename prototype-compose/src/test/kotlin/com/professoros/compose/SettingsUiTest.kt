@file:OptIn(androidx.compose.ui.test.InternalTestApi::class, androidx.compose.ui.test.ExperimentalTestApi::class)
package com.professoros.compose

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.professoros.model.ThemeMode
import com.professoros.service.ThemePreferences
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.*

class SettingsUiTest {
    @get:Rule val folder=TemporaryFolder()

    @Test fun appearancePersistsWithoutResettingWorkAndSettingsRemainAccessible()=runSkikoComposeUiTest(size=Size(1360f,860f)) {
        val path=folder.root.toPath().resolve("settings.db");val data=fixture(path).load(null)
        val before=Files.readAllBytes(path)
        lateinit var settings:AppearanceSettings
        lateinit var c:WorkspaceController
        var page by mutableStateOf("Settings");var width by mutableStateOf(1360);var height by mutableStateOf(860)
        var systemDark by mutableStateOf(true)
        setContent {
            val scope=rememberCoroutineScope()
            settings=remember {AppearanceSettings(path,scope)}
            c=remember {WorkspaceController(WorkspaceServices(path),scope)}
            CompositionLocalProvider(LocalPalette provides if(settings.mode.dark(systemDark))DarkPalette else LightPalette) {
                Box(Modifier.requiredSize(width.dp,height.dp)) {
                    ProfessorDashboard(data,false,null,page,{page=it},{},{},featureContent={
                        if(it=="Settings")SettingsScreen(settings){page="Terms"} else CatalogScreen(it,c){}
                    })
                }
            }
        }
        runOnIdle {c.editCourse();c.form!!.values["name"]="Unsaved course"}
        val draft=c.form
        for(mode in listOf(ThemeMode.LIGHT,ThemeMode.DARK,ThemeMode.SYSTEM)) {
            onNodeWithTag("theme-${mode.name}").performClick()
            waitUntil(timeoutMillis=10000){!settings.saving}
            assertEquals(mode,settings.mode)
            assertEquals(mode,ThemePreferences(path.resolveSibling("appearance.properties")).load())
            assertSame(draft,c.form);assertEquals("Unsaved course",c.form!!["name"])
            assertEquals("Settings",page)
            snapshot("settings-${mode.name.lowercase()}")
        }
        runOnIdle {systemDark=false;width=864;height=621}
        snapshot("settings-system-light-minimum")
        onNodeWithTag("storage-path").performScrollTo().assertTextEquals(path.toString())
        onNodeWithTag("settings-terms").performScrollTo().performClick()
        assertEquals("Terms",page)
        onNodeWithTag("nav-Settings").performScrollTo().performClick()
        onNodeWithTag("settings-page").assertExists()
        onNodeWithTag("motion").assertDoesNotExist();onNodeWithTag("open-javafx").assertDoesNotExist()
        assertContentEquals(before,Files.readAllBytes(path))
    }

    @Test fun failedPreferenceWriteRetainsSelection()=runSkikoComposeUiTest {
        val path=folder.root.toPath().resolve("blocked/settings.db")
        Files.writeString(path.parent,"Not a directory")
        lateinit var settings:AppearanceSettings
        setContent {val scope=rememberCoroutineScope();settings=remember {AppearanceSettings(path,scope)};SettingsScreen(settings){}}
        onNodeWithTag("theme-DARK").performClick();waitUntil(timeoutMillis=10000){!settings.saving}
        assertEquals(ThemeMode.SYSTEM,settings.mode)
        onNodeWithTag("settings-error").assertExists()
        assertEquals("Not a directory",Files.readString(path.parent))
    }

    @Test fun lightAndDarkTokensHaveReadableContrastAndSystemResolves() {
        for(p in listOf(LightPalette,DarkPalette))for(bg in listOf(p.background,p.panel,p.raised,p.selected,p.accentSurface))
            for(fg in listOf(p.text,p.muted,p.violet,p.positive)) {
                val a=fg.luminance();val b=bg.luminance()
                assertTrue((maxOf(a,b)+.05f)/(minOf(a,b)+.05f)>=4.5f,"Insufficient contrast: $fg on $bg")
            }
        assertTrue(ThemeMode.SYSTEM.dark(true));assertFalse(ThemeMode.SYSTEM.dark(false))
        assertTrue(ThemeMode.DARK.dark(false));assertFalse(ThemeMode.LIGHT.dark(true))
    }
    private fun ComposeUiTest.snapshot(name:String) {
        waitForIdle()
        val file=File(System.getProperty("compose.test.output"),"$name.png")
        ImageIO.write(onNodeWithTag("dashboard-root").captureToImage().toAwtImage(),"png",file)
    }
}
