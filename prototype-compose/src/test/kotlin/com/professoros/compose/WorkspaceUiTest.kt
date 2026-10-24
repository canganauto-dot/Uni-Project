@file:OptIn(androidx.compose.ui.test.InternalTestApi::class, androidx.compose.ui.test.ExperimentalTestApi::class)
package com.professoros.compose

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import com.professoros.model.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO
import kotlin.test.*

class WorkspaceUiTest {
    @get:Rule val folder=TemporaryFolder()
    @Test fun lectureFormScopeDuplicateAndProgress() = runSkikoComposeUiTest(size=Size(900f,720f)) {
        val path=folder.root.toPath().resolve("lectures.db");fixture(path)
        val services=WorkspaceServices(path);val id=services.academic.offerings().findAll().first().id()
        lateinit var c:WorkspaceController
        setContent {
            val scope=rememberCoroutineScope();c=remember {WorkspaceController(services,scope)}
            LaunchedEffect(Unit){c.select(id)}
            Box(Modifier.fillMaxSize()) {LectureScreen(c);WorkspaceOverlays(c)}
        }
        settled(c);capture("migration-lectures-minimum")
        onNodeWithTag("new-lecture").performClick()
        onNodeWithTag("field-number").performTextInput("1")
        onNodeWithTag("field-title").performTextInput("New lecture")
        onNodeWithTag("field-date").performTextInput(LocalDate.now().toString())
        onNodeWithTag("save-form").performClick();settled(c)
        assertNotNull(c.error);assertNotNull(c.form)
        onNodeWithTag("field-number").performTextReplacement("6")
        onNodeWithTag("save-form").performClick();settled(c)
        assertEquals(6,c.lectures!!.progress().total())
        val added=c.lectures!!.lectures().single {it.lectureNumber()==6}
        runOnIdle {c.editLecture(added);c.form!!.values["status"]="COMPLETED";c.saveForm()};settled(c)
        assertEquals(4,c.lectures!!.progress().completed())
        onNodeWithTag("lecture-search").performTextInput("New lecture")
        onNodeWithTag("lectures-table-"+added.id()).assertExists()
        runOnIdle {c.mutate {services.academic.lectures().delete(id,added.id())}};settled(c)
        assertEquals(5,c.lectures!!.progress().total())
        val other=services.academic.offerings().findAll().last().id()
        runOnIdle {c.select(other)};settled(c);assertTrue(c.lectures!!.lectures().isEmpty())
    }
    @Test fun catalogEditingValidationDiscardAndPersistence() = runSkikoComposeUiTest(size=Size(1360f,860f)) {
        val services=WorkspaceServices(folder.root.toPath().resolve("catalog.db")); services.initialize()
        lateinit var c:WorkspaceController
        setContent {
            val scope=rememberCoroutineScope();c=remember {WorkspaceController(services,scope)}
            LaunchedEffect(Unit){c.refresh()}
            Box(Modifier.fillMaxSize()) {CatalogScreen("Courses",c){};WorkspaceOverlays(c)}
        }
        settled(c)
        onNodeWithTag("new-course").performClick()
        onNodeWithTag("save-form").performClick();settled(c)
        onNodeWithTag("form-error").assertExists()
        onNodeWithTag("field-code").performTextInput("CS201")
        onNodeWithTag("field-name").performTextInput("هندسة البرمجيات")
        capture("migration-course-form","editor-modal")
        onNodeWithTag("save-form").performClick();settled(c)
        assertNull(c.form);assertEquals("CS201",c.catalog.courses.single().courseCode())
        val saved=c.catalog.courses.single()
        runOnIdle {c.editCourse(saved)}
        onNodeWithTag("field-name").performTextReplacement("Changed")
        runOnIdle {c.closeEditor()}
        onNodeWithTag("cancel-confirm").performClick()
        assertEquals("Changed",c.form!!["name"])
        runOnIdle {c.closeEditor()};onNodeWithTag("confirm").performClick()
        assertNull(c.form);assertEquals("هندسة البرمجيات",services.courses.findById(saved.id()).courseName())
        capture("migration-courses")
        runOnIdle {c.editStudent()}
        onNodeWithTag("field-number").performTextInput("00042")
        onNodeWithTag("field-first").performTextInput("أحمد")
        onNodeWithTag("field-last").performTextInput("علي")
        onNodeWithTag("save-form").performClick();settled(c)
        assertEquals("00042",c.catalog.students.single().studentNumber())
        val term=services.academic.terms().create(AcademicTermInput("Fall","2026–2027",LocalDate.of(2026,9,1),LocalDate.of(2027,1,1),AcademicStatus.ACTIVE))
        val offering=services.academic.offerings().create(CourseOfferingInput(saved.id(),term.id(),"A","",AcademicStatus.ACTIVE))
        runOnIdle {c.select(offering.id())};settled(c)
        runOnIdle {c.enrollStudent();c.form!!.values["student"]=c.catalog.students.single().id().toString();c.saveForm()};settled(c)
        assertEquals(1,c.roster.size)
        runOnIdle {c.mutate {services.courses.delete(saved.id())}};settled(c)
        assertNotNull(c.error);assertEquals(1,c.catalog.courses.size)
        val reopened=WorkspaceServices(folder.root.toPath().resolve("catalog.db"));reopened.initialize()
        assertEquals("00042",reopened.academic.enrollments().findStudentsForOffering(offering.id()).single().student().studentNumber())
    }
    private fun ComposeUiTest.settled(c:WorkspaceController) {waitForIdle();waitUntil(timeoutMillis=10000){!c.busy};waitForIdle()}
    private fun ComposeUiTest.capture(name:String,tag:String?=null) {
        waitForIdle();val node=if(tag==null)onRoot() else onNodeWithTag(tag)
        val file=File(System.getProperty("compose.test.output"),"$name.png");file.parentFile.mkdirs()
        ImageIO.write(node.captureToImage().toAwtImage(),"png",file)
    }
}
