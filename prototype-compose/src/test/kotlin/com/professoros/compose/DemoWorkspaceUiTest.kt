@file:OptIn(androidx.compose.ui.test.InternalTestApi::class, androidx.compose.ui.test.ExperimentalTestApi::class)
package com.professoros.compose

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import com.professoros.database.Database
import com.professoros.demo.DemoDataSeeder
import com.professoros.model.AcademicStatus
import com.professoros.service.GradeCalculator
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO
import kotlin.test.*

class DemoWorkspaceUiTest {
    @get:Rule val folder=TemporaryFolder()
    @Test fun seededRecordsPopulateEveryRouteAndCompleteGradebooks()=runSkikoComposeUiTest(size=Size(1360f,860f)) {
        val path=folder.root.toPath().resolve(".demo-stage-ui.db")
        Database(path).initialize();DemoDataSeeder.seed(path,LocalDate.of(2026,10,26))
        val bridge=DashboardBridge(path);var data by mutableStateOf(bridge.load(null))
        var page by mutableStateOf("Overview");lateinit var c:WorkspaceController
        setContent {
            val scope=rememberCoroutineScope();c=remember {WorkspaceController(WorkspaceServices(path),scope)}
            val settings=remember {AppearanceSettings(path,scope)}
            LaunchedEffect(Unit){c.select(data.selected!!.offering().id())}
            Box(Modifier.fillMaxSize()) {
                ProfessorDashboard(data,c.busy,null,page,{p->c.guard {page=p}},{},{},featureContent={p->when(p) {
                    "Teaching context"->RosterScreen(c);"Lectures"->LectureScreen(c);"Question Bank"->QuestionScreen(c)
                    "Exam Builder"->ExamScreen(c);"Gradebook"->GradebookScreen(c);"Attendance"->AttendanceScreen(c)
                    "Settings"->SettingsScreen(settings){page="Terms"};else->CatalogScreen(p,c){}
                }})
                WorkspaceOverlays(c)
            }
        }
        waitForIdle();waitUntil(timeoutMillis=15000){!c.busy}
        assertEquals(70,c.catalog.students.size);assertEquals(35,c.roster.size);assertEquals(12,c.lectures!!.lectures().size)
        assertEquals(40,c.questions.size);assertEquals(3,c.exams.size);assertEquals(140,c.gradebook!!.book.grades().size)
        assertEquals(420,c.attendance!!.book.records().size)
        assertTrue(c.catalog.students.all { it.fullName().split(" ").size==3 })
        val output=File(System.getProperty("compose.test.output"))
        for(route in listOf("Overview","Courses","Students","Terms","Offerings","Teaching context","Lectures","Question Bank","Exam Builder","Gradebook","Attendance","Activity","Settings")) {
            onNodeWithTag("nav-$route").performScrollTo().performClick();waitForIdle()
            assertEquals(route,page);assertNull(c.error)
            if(route=="Overview") onNodeWithTag("completion-value",true).assertTextEquals("100%")
            if(route=="Lectures") onNodeWithText("100% complete",substring=true).assertExists()
            ImageIO.write(onNodeWithTag("dashboard-root").captureToImage().toAwtImage(),"png",File(output,"demo-${route.replace(' ','-')}.png"))
        }
        val second=c.catalog.offerings.first {it.offering().id()!=c.offeringId}.offering().id()
        runOnIdle {data=bridge.load(second);c.select(second);page="Gradebook"}
        waitForIdle();waitUntil(timeoutMillis=15000){!c.busy}
        val book=c.gradebook!!.book;val stats=GradeCalculator.classStatistics(book,GradeCalculator.scores(book))
        assertEquals(35,stats.complete());assertEquals(0,stats.incomplete());assertTrue(stats.passed()>0);assertTrue(stats.passed()<35)
        ImageIO.write(onNodeWithTag("dashboard-root").captureToImage().toAwtImage(),"png",File(output,"demo-second-course-grades.png"))
        assertEquals(70,WorkspaceServices(path).catalog().students.size)
    }
}
