@file:OptIn(androidx.compose.ui.test.InternalTestApi::class, androidx.compose.ui.test.ExperimentalTestApi::class)
package com.professoros.compose

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*

class WorkspaceLayoutTest {
    @get:Rule val folder=TemporaryFolder()
    @Test fun integratedPagesAtNormalAndMinimumSizes()=runSkikoComposeUiTest(size=Size(1360f,860f)) {
        val path=folder.root.toPath().resolve("layout.db");val bridge=fixture(path);val data=bridge.load(null)
        lateinit var c:WorkspaceController
        var page by mutableStateOf("Courses");var width by mutableStateOf(1360);var height by mutableStateOf(860)
        setContent {
            val scope=rememberCoroutineScope();c=remember {WorkspaceController(WorkspaceServices(path),scope)}
            LaunchedEffect(Unit){c.select(data.selected!!.offering().id())}
            Box(Modifier.requiredSize(width.dp,height.dp)) {
                ProfessorDashboard(data,c.busy,null,page,{next->c.guard {page=next}},true,MotionMode.REDUCED,{},{c.refresh()},{c.select(it)},{},featureContent={p->
                    when(p){"Teaching context"->RosterScreen(c);"Lectures"->LectureScreen(c);"Question Bank"->QuestionScreen(c);"Exam Builder"->ExamScreen(c);else->CatalogScreen(p,c){c.select(it)}}})
                WorkspaceOverlays(c,true)
            }
        }
        waitForIdle();waitUntil(timeoutMillis=10000){!c.busy}
        for(size in listOf(1360 to 860,864 to 621)) {
            runOnIdle {width=size.first;height=size.second}
            for(destination in listOf("Courses","Students","Terms","Offerings","Teaching context","Lectures","Question Bank","Exam Builder")) {
                onNodeWithTag("nav-$destination").performScrollTo().performClick()
                waitForIdle();assertEquals(destination,page)
                val file=File(System.getProperty("compose.test.output"),"migration-shell-${destination.replace(' ','-')}-${size.first}.png")
                ImageIO.write(onNodeWithTag("dashboard-root").captureToImage().toAwtImage(),"png",file)
            }
        }
        runOnIdle {c.editOffering()}
        onNodeWithTag("field-course").performClick()
        onNodeWithTag("field-course-search").performTextInput("هندسة")
        val course=c.catalog.courses.single {it.courseName().contains("هندسة")}
        onNodeWithTag("field-course-"+course.id()).performClick()
        assertEquals(course.id().toString(),c.form!!["course"])
        onNodeWithTag("field-term").performClick()
        onNodeWithTag("field-term-"+c.catalog.terms.first().id()).performClick()
        onNodeWithTag("field-section").performTextInput("B")
        onNodeWithTag("save-form").performClick();waitForIdle();waitUntil(timeoutMillis=10000){!c.busy}
        assertEquals(4,c.catalog.offerings.size)
    }
}
