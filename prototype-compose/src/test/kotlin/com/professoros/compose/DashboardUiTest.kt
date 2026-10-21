@file:OptIn(androidx.compose.ui.test.InternalTestApi::class, androidx.compose.ui.test.ExperimentalTestApi::class)

package com.professoros.compose

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*

import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*

class DashboardUiTest {

    @get:Rule val folder=TemporaryFolder()
    @Test fun navigationArabicResponsivenessAndReducedMotion() = runSkikoComposeUiTest(size = Size(1700f, 1050f)) {
        val bridge=fixture(folder.root.toPath().resolve("ui.db"))
        var data by mutableStateOf(bridge.load(null));var page by mutableStateOf("Overview")
        var width by mutableStateOf(1360);var height by mutableStateOf(960)
        var mode by mutableStateOf(MotionMode.REDUCED);var refreshes=0;var opens=0
        val compose = this
        compose.setContent {
            Box(Modifier.fillMaxSize()) { Box(Modifier.requiredSize(width.dp,height.dp)) {
                ProfessorDashboard(data,false,null,page,{page=it},mode==MotionMode.REDUCED,mode,{mode=it},{refreshes++},{data=bridge.load(it);page="Overview"},{opens++})
            } }
        }
        compose.onNodeWithTag("arabic-welcome").assertExists()
        snapshot("overview-1360")
        compose.onNodeWithTag("refresh").performClick();assertEquals(1,refreshes)
        compose.onNodeWithTag("refresh").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("refresh").performKeyInput { pressKey(Key.Enter) }
        assertEquals(2, refreshes, "Keyboard activation must reach the same action")
        compose.onNodeWithTag("nav-Teaching context").performClick()
        compose.onNodeWithText("هندسة البرمجيات").assertExists();snapshot("contexts-arabic")
        compose.onNodeWithTag("offering-${data.offerings[1].offering().id()}").performScrollTo().performClick()
        compose.onNodeWithTag("arabic-welcome").assertExists();assertTrue(data.selected!!.courseName().contains("هندسة"))
        compose.onNodeWithTag("nav-Activity").performClick();compose.onNodeWithText("The shape of your week").assertExists()
        compose.onNodeWithTag("nav-Overview").performClick()
        compose.runOnIdle {data=bridge.load(null);width=900;height=720};snapshot("overview-900")
        compose.onNodeWithTag("open-javafx").performClick();assertEquals(1,opens)
        compose.runOnIdle { width=864; height=621 };snapshot("overview-minimum-content")
        compose.onNodeWithTag("refresh").assertIsDisplayed()
        compose.onNodeWithTag("open-javafx").assertIsDisplayed()
        compose.runOnIdle {width=1700;height=1050};snapshot("overview-1700")
        // Reduced mode produces identical pixels after the idle clock advances: no ambient animation remains.
        val first=compose.onNodeWithTag("dashboard-root").captureToImage().toAwtImage();compose.mainClock.advanceTimeBy(2000)
        val second=compose.onNodeWithTag("dashboard-root").captureToImage().toAwtImage()
        assertContentEquals(first.getRGB(0,0,first.width,first.height,null,0,first.width),second.getRGB(0,0,second.width,second.height,null,0,second.width))
        val started=System.nanoTime();repeat(10){compose.onNodeWithTag("dashboard-root").captureToImage()}
        File(output(),"render-observation.txt").writeText("10 warmed screenshot captures: ${(System.nanoTime()-started)/1_000_000} ms. Includes readback and test synchronization; not a frame-rate benchmark.\n")
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { mode = MotionMode.FULL }
        compose.mainClock.advanceTimeBy(500)
        val animated = compose.onNodeWithTag("dashboard-root").captureToImage().toAwtImage()
        compose.mainClock.advanceTimeBy(4000)
        val later = compose.onNodeWithTag("dashboard-root").captureToImage().toAwtImage()
        assertFalse(animated.getRGB(0,0,animated.width,animated.height,null,0,animated.width)
            .contentEquals(later.getRGB(0,0,later.width,later.height,null,0,later.width)), "Full motion should animate the ambient layer")
        compose.runOnIdle { mode = MotionMode.REDUCED }
        compose.mainClock.advanceTimeBy(500)
        assertEquals("Overview", page)
        assertEquals(bridge.load(null).selected, data.selected, "Motion changes must preserve the selected offering")
    }
    @Test fun emptyWorkspaceRendersHonestGuidance() = runSkikoComposeUiTest(size = Size(900f,720f)) {
        val bridge=DashboardBridge(folder.root.toPath().resolve("empty-ui.db"));bridge.initialize()
        setContent { ProfessorDashboard(bridge.load(null),false,null,"Teaching context",{},true,MotionMode.REDUCED,{},{},{},{}) }
        onNodeWithText("No offerings yet").assertExists()
        snapshot("empty-workspace")
    }
    private fun output()=File(System.getProperty("compose.test.output")).also {it.mkdirs()}
    private fun ComposeUiTest.snapshot(name:String){waitForIdle();ImageIO.write(onNodeWithTag("dashboard-root").captureToImage().toAwtImage(),"png",File(output(),"$name.png"))}
}
