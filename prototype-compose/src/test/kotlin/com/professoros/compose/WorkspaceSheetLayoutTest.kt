@file:OptIn(androidx.compose.ui.test.InternalTestApi::class, androidx.compose.ui.test.ExperimentalTestApi::class)
package com.professoros.compose

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.professoros.database.Database
import com.professoros.demo.DemoDataSeeder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO
import kotlin.test.*

class WorkspaceSheetLayoutTest {
    @get:Rule val folder=TemporaryFolder()

    @Test fun sheetsFillResizedWorkspaceAndKeepHeadersAndStudentIdentityVisible()=runSkikoComposeUiTest(size=Size(1920f,1080f)) {
        val path=folder.root.toPath().resolve(".demo-stage-layout.db")
        Database(path).initialize();DemoDataSeeder.seed(path,LocalDate.of(2026,10,27))
        val snapshot=DashboardBridge(path).load(null)
        var width by mutableStateOf(880);var height by mutableStateOf(660)
        var page by mutableStateOf("Gradebook");lateinit var c:WorkspaceController
        setContent {
            val scope=rememberCoroutineScope()
            c=remember {WorkspaceController(WorkspaceServices(path),scope)}
            LaunchedEffect(Unit){c.select(snapshot.selected!!.offering().id())}
            Box(Modifier.requiredSize(width.dp,height.dp)) {
                ProfessorDashboard(snapshot,c.busy,null,page,{page=it},{},{},featureContent={
                    if(it=="Gradebook")GradebookScreen(c) else AttendanceScreen(c)
                })
            }
        }
        waitForIdle();waitUntil(timeoutMillis=15000){!c.busy};waitForIdle()
        fun bounds(tag:String)=onNodeWithTag(tag,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        fun capture(name:String) {
            val file=File(System.getProperty("compose.test.output"),"$name.png")
            file.parentFile.mkdirs()
            ImageIO.write(onNodeWithTag("dashboard-root").captureToImage().toAwtImage(),"png",file)
        }
        val first=c.gradebook!!.book.students().minBy {it.student().studentNumber()}.enrollment().id()
        val student=bounds("grade-student-$first")
        val header=bounds("grade-frozen-header")
        val minGrid=bounds("grade-grid")
        assertTrue(minGrid.width>760f);assertTrue(minGrid.height>230f)
        onNodeWithTag("save-grades").assertIsDisplayed()
        capture("gradebook-layout-minimum")
        val scrollHeader=bounds("grade-scroll-header")
        onNodeWithTag("grade-scroll-header").performSemanticsAction(SemanticsActions.ScrollBy){it(500f,0f)}
        assertTrue(onNodeWithTag("grade-scroll-header").fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()>0f)
        assertEquals(student,bounds("grade-student-$first"),"Student identity must remain frozen during horizontal scrolling")
        assertEquals(header,bounds("grade-frozen-header"))
        assertEquals(scrollHeader,bounds("grade-scroll-header"))
        onNodeWithTag("grade-grid-rows").performScrollToIndex(20)
        assertEquals(header,bounds("grade-frozen-header"),"Column headers must stay visible during vertical scrolling")
        runOnIdle {width=1920;height=1080};waitForIdle()
        assertTrue(bounds("grade-grid").width>1650f)
        assertTrue(bounds("grade-grid").height>minGrid.height+350f)
        onNodeWithTag("grade-grid-rows").performScrollToIndex(0)
        capture("gradebook-layout-maximized")

        runOnIdle {page="Attendance";width=880;height=660};waitForIdle()
        val students=c.attendance!!.book.students().sortedBy {it.student().studentNumber()}
        val ids=students.take(2).map {it.enrollment().id()}
        val firstRow=bounds("attendance-row-${ids[0]}")
        val secondRow=bounds("attendance-row-${ids[1]}")
        assertEquals(firstRow.left,secondRow.left)
        assertTrue(secondRow.top>=firstRow.bottom,"Exactly one student belongs on each row")
        assertEquals(bounds("attendance-${ids[0]}-PRESENT").left,bounds("attendance-${ids[1]}-PRESENT").left)
        onNodeWithTag("attendance-${ids[0]}-UNMARKED").assertIsDisplayed()
        onNodeWithTag("save-attendance").assertIsDisplayed()
        val minRoster=bounds("attendance-roster")
        assertTrue(minRoster.width>760f);assertTrue(minRoster.height>230f)
        capture("attendance-layout-minimum")
        val rosterHeader=bounds("attendance-roster-header")
        onNodeWithTag("attendance-roster-rows").performScrollToIndex(34)
        assertEquals(rosterHeader,bounds("attendance-roster-header"))
        onNodeWithTag("attendance-row-${students.last().enrollment().id()}").assertIsDisplayed()
        runOnIdle {width=1920;height=1080};waitForIdle()
        assertTrue(bounds("attendance-roster").width>1650f)
        assertTrue(bounds("attendance-roster").height>minRoster.height+350f)
        onNodeWithTag("attendance-roster-rows").performScrollToIndex(0)
        capture("attendance-layout-maximized")
    }
}
