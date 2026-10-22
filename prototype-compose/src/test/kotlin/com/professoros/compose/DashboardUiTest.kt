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

    @Test fun navigationPickerKeyboardAndResponsiveLayouts() = runSkikoComposeUiTest(size=Size(1700f,1050f)) {
        val bridge=fixture(folder.root.toPath().resolve("ui.db"))
        var data by mutableStateOf(bridge.load(null))
        var page by mutableStateOf("Overview")
        var width by mutableStateOf(1360);var height by mutableStateOf(860)
        var refreshes=0;var opens=0
        setContent {
            Box(Modifier.fillMaxSize()) { Box(Modifier.requiredSize(width.dp,height.dp)) {
                ProfessorDashboard(data,false,null,page,{page=it},true,MotionMode.REDUCED,{},{refreshes++},
                    {data=bridge.load(it);page="Overview"},{opens++})
            }}
        }
        onNodeWithTag("arabic-welcome").assertExists()
        onNodeWithText("60%").assertExists()
        snapshot("refined-overview-1360")
        onNodeWithTag("refresh").performSemanticsAction(SemanticsActions.RequestFocus){it()}
        onNodeWithTag("refresh").performKeyInput{pressKey(Key.Enter)}
        assertEquals(1,refreshes)
        onNodeWithTag("choose-context").performClick()
        onNodeWithTag("offering-search").performTextInput("هندسة")
        onNodeWithText("هندسة البرمجيات").assertExists()
        onNodeWithTag("offering-search").performKeyInput {pressKey(Key.Enter)}
        onNodeWithTag("context-popup").assertDoesNotExist()
        assertTrue(data.selected!!.courseName().contains("هندسة"))
        onNodeWithText("Ready for your first lecture.").assertExists()
        // Quick switching is available from the keyboard without navigating away.
        onNodeWithTag("refresh").performSemanticsAction(SemanticsActions.RequestFocus){it()}
        onNodeWithTag("refresh").performKeyInput{keyDown(Key.CtrlLeft);pressKey(Key.K);keyUp(Key.CtrlLeft)}
        onNodeWithTag("context-popup").assertExists()
        onNodeWithTag("offering-search").performTextInput("does not exist")
        onNodeWithText("No matching offerings").assertExists()
        onNodeWithTag("offering-search").performKeyInput{pressKey(Key.Escape)}
        onNodeWithTag("context-popup").assertDoesNotExist()
        onNodeWithTag("nav-Teaching context").performClick()
        onNodeWithText("هندسة البرمجيات").assertExists()
        snapshot("refined-contexts-arabic")
        val thirdOffering=data.offerings.single {it.courseCode()=="CS303"}.offering().id()
        onNodeWithTag("offering-"+thirdOffering).performSemanticsAction(SemanticsActions.RequestFocus){it()}
        onNodeWithTag("offering-"+thirdOffering).performKeyInput {pressKey(Key.Enter)}
        assertEquals("CS303",data.selected!!.courseCode(),"Enter must select the focused row, not a stale search cursor")
        onNodeWithTag("nav-Teaching context").performClick()
        onNodeWithTag("offering-search").performTextInput("CS301")
        onNodeWithTag("offering-search").performKeyInput{pressKey(Key.Enter)}
        assertEquals("CS301",data.selected!!.courseCode())
        onNodeWithTag("nav-Activity").performClick()
        onNodeWithText("Your academic week").assertExists()
        onNodeWithTag("nav-Overview").performClick()
        runOnIdle {width=900;height=720};snapshot("refined-overview-900")
        onNodeWithTag("open-javafx").performClick();assertEquals(1,opens)
        runOnIdle {width=864;height=621};snapshot("refined-minimum-content")
        onNodeWithTag("refresh").assertIsDisplayed()
        onNodeWithTag("open-javafx").assertIsDisplayed()
        runOnIdle {width=1700;height=1050};snapshot("refined-overview-1700")
        val first=pixels();mainClock.advanceTimeBy(2000)
        assertContentEquals(first,pixels(),"Reduced motion must leave idle pixels unchanged")
    }

    @Test fun springIndicatorMovesSettlesAndReducedMotionSnaps() = runSkikoComposeUiTest(size=Size(1360f,860f)) {
        val data=fixture(folder.root.toPath().resolve("motion.db")).load(null)
        var page by mutableStateOf("Overview");var reduced by mutableStateOf(false)
        setContent { ProfessorDashboard(data,false,null,page,{page=it},reduced,MotionMode.FULL,{},{},{},{}) }
        mainClock.autoAdvance=false
        val start=onNodeWithTag("navigation-indicator").fetchSemanticsNode().boundsInRoot.top
        runOnIdle {page="Activity"}
        mainClock.advanceTimeBy(64)
        val intermediate=onNodeWithTag("navigation-indicator").fetchSemanticsNode().boundsInRoot.top
        assertTrue(intermediate>start && intermediate<start+88,"Indicator must glide between rows")
        snapshot("motion-intermediate")
        mainClock.advanceTimeBy(1600)
        val settled=onNodeWithTag("navigation-indicator").fetchSemanticsNode().boundsInRoot.top
        assertEquals(start+88,settled,0.5f)
        val idle=pixels();mainClock.advanceTimeBy(2000)
        assertContentEquals(idle,pixels(),"Full motion must also rest after transitions; no ambient loop")
        runOnIdle {reduced=true;page="Overview"}
        mainClock.advanceTimeBy(32)
        assertEquals(start,onNodeWithTag("navigation-indicator").fetchSemanticsNode().boundsInRoot.top,0.5f)
        onNodeWithText("Your workspace").assertExists()
        onNodeWithText("60%").assertExists()
        val began=System.nanoTime()
        repeat(10){onNodeWithTag("dashboard-root").captureToImage()}
        File(output(),"render-observation.txt").writeText("10 warmed captures: "+(System.nanoTime()-began)/1_000_000+
            " ms. Readback/synchronization included; not FPS. Spring reached destination and idle pixels remained stable.\n")
    }

    @Test fun emptyWorkspaceRendersHonestGuidance() = runSkikoComposeUiTest(size=Size(900f,720f)) {
        val bridge=DashboardBridge(folder.root.toPath().resolve("empty-ui.db"));bridge.initialize()
        val data=bridge.load(null)
        setContent {ProfessorDashboard(data,false,null,"Overview",{},true,MotionMode.REDUCED,{},{},{},{})}
        onNodeWithText("Begin with a course offering").assertExists()
        onNodeWithText("0%").assertDoesNotExist()
        snapshot("refined-empty-workspace")
        onNodeWithTag("choose-context").performClick()
        onNodeWithText("No offerings yet").assertExists()
    }

    private fun output()=File(System.getProperty("compose.test.output")).also {it.mkdirs()}
    private fun ComposeUiTest.pixels():IntArray {
        val image=onNodeWithTag("dashboard-root").captureToImage().toAwtImage()
        return image.getRGB(0,0,image.width,image.height,null,0,image.width)
    }
    private fun ComposeUiTest.snapshot(name:String) {
        waitForIdle()
        ImageIO.write(onNodeWithTag("dashboard-root").captureToImage().toAwtImage(),"png",File(output(),name+".png"))
    }
}
