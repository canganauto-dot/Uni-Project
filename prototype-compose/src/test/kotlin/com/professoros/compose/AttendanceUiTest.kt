@file:OptIn(androidx.compose.ui.test.InternalTestApi::class, androidx.compose.ui.test.ExperimentalTestApi::class)
package com.professoros.compose

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.professoros.model.*
import com.professoros.service.AttendanceCalculator
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import javax.imageio.ImageIO
import kotlin.test.*

class AttendanceUiTest {
    @get:Rule val folder=TemporaryFolder()
    private fun fixture():Pair<WorkspaceServices,Long> {
        val s=WorkspaceServices(folder.root.toPath().resolve("attendance.db"));s.initialize()
        val course=s.courses.create(CourseInput("CS201","Software Engineering","Computing",3,""));val a=s.academic
        val term=a.terms().create(AcademicTermInput("Fall","2026–2027",LocalDate.of(2026,9,1),LocalDate.of(2027,1,1),AcademicStatus.ACTIVE))
        val offering=a.offerings().create(CourseOfferingInput(course.id(),term.id(),"A","",AcademicStatus.ACTIVE))
        val enrollments=listOf(StudentInput("0001","أحمد","علي","",""),StudentInput("0002","Sara","Hassan","",""),StudentInput("0003","Omar","Khalid","","")).map {a.enrollments().enroll(a.students().create(it).id(),offering.id())}
        a.enrollments().updateStatus(enrollments[2].id(),EnrollmentStatus.WITHDRAWN)
        val lectures=(1..4).map {a.lectures().create(offering.id(),lectureInput(it,LectureStatus.PLANNED))}
        a.attendance().save(offering.id(),lectures[0].id(),mapOf(enrollments[0].id() to AttendanceStatus.PRESENT,enrollments[1].id() to AttendanceStatus.ABSENT,enrollments[2].id() to AttendanceStatus.ABSENT))
        a.attendance().save(offering.id(),lectures[1].id(),mapOf(enrollments[0].id() to AttendanceStatus.LATE,enrollments[1].id() to AttendanceStatus.EXCUSED))
        a.attendance().save(offering.id(),lectures[2].id(),mapOf(enrollments[0].id() to AttendanceStatus.ABSENT))
        a.lectures().update(offering.id(),lectures[2].id(),lectureInput(3,LectureStatus.CANCELLED))
        return s to offering.id()
    }
    private fun lectureInput(number:Int,status:LectureStatus)=LectureInput(number,"Session $number",LocalDate.of(2026,10,number),"Objects","",60,status,"")

    @Test fun marksCalculationsHistoryClearingAndRestart()=runSkikoComposeUiTest(size=Size(1360f,900f)) {
        val (services,id)=fixture();val data=DashboardBridge(folder.root.toPath().resolve("attendance.db")).load(id)
        lateinit var c:WorkspaceController;var page by mutableStateOf("Overview");var width by mutableStateOf(1360);var height by mutableStateOf(900)
        setContent {val scope=rememberCoroutineScope();c=remember {WorkspaceController(services,scope)};LaunchedEffect(Unit){c.select(id)}
            CompositionLocalProvider(){Box(Modifier.requiredSize(width.dp,height.dp)) {
                ProfessorDashboard(data,c.busy,null,page,{p->c.guard {page=p}},{c.guard {c.refresh()}},{},featureContent={AttendanceScreen(c)})
                WorkspaceOverlays(c)
            }}}
        settled(c);onNodeWithTag("nav-Attendance").performScrollTo().performClick()
        val book=c.attendance!!.book;val first=book.students().single {it.student().studentNumber()=="0001"}.enrollment().id()
        val lecture=book.lectures().first().id()
        assertEquals(BigDecimal("66.67"),AttendanceCalculator.course(book).percentage())
        assertEquals(BigDecimal("100.00"),AttendanceCalculator.student(book,first).percentage())
        onNodeWithTag("attendance-$first-UNMARKED").performScrollTo().performClick()
        assertTrue(c.attendance!!.dirty);assertNull(c.attendance!!.value(first))
        // Percentages explicitly remain based on saved records until Save.
        onNodeWithTag("attendance-percent-$first",true).assertTextContains("100%")
        onNodeWithTag("save-attendance").performScrollTo().performClick();settled(c)
        assertFalse(c.attendance!!.dirty);assertNull(services.academic.attendance().load(id).status(first,lecture))
        onNodeWithTag("attendance-row-$first").performScrollTo().performClick().performKeyInput {pressKey(Key.A)}
        assertEquals(AttendanceStatus.ABSENT,c.attendance!!.value(first))
        onNodeWithTag("attendance-row-$first").performKeyInput {keyDown(Key.CtrlLeft);pressKey(Key.S);keyUp(Key.CtrlLeft)};settled(c)
        assertEquals(BigDecimal("50.00"),AttendanceCalculator.student(c.attendance!!.book,first).percentage())
        onNodeWithTag("attendance-row-$first").performScrollTo().performClick()
        onNodeWithTag("attendance-history").performScrollTo().performClick()
        assertContains(c.detail!!.text,"(cancelled)");assertContains(c.detail!!.text,"Unmarked");assertContains(c.detail!!.text,"Late")
        runOnIdle {c.detail=null}
        onNodeWithTag("attendance-search").performTextInput("أحمد")
        assertEquals(1,onAllNodes(hasTestTag("attendance-row-$first")).fetchSemanticsNodes().size)
        onNodeWithTag("attendance-search").performTextReplacement("")
        capture("attendance-compose-normal")
        runOnIdle {width=864;height=621};capture("attendance-compose-minimum")
        val reopened=WorkspaceServices(folder.root.toPath().resolve("attendance.db"));reopened.initialize()
        assertEquals(AttendanceStatus.ABSENT,reopened.academic.attendance().load(id).status(first,lecture))
        assertEquals(1,reopened.academic.attendance().load(id).records().count {it.enrollmentId()==first && it.lectureId()==lecture})
    }

    @Test fun bulkIncludesHiddenRowsGuardsAndCancelledSessionValidation()=runSkikoComposeUiTest(size=Size(1200f,950f)) {
        val (services,id)=fixture();lateinit var c:WorkspaceController
        setContent {val scope=rememberCoroutineScope();c=remember {WorkspaceController(services,scope)};LaunchedEffect(Unit){c.select(id)}
            Box(Modifier.fillMaxSize()){AttendanceScreen(c);WorkspaceOverlays(c)}}
        settled(c);val book=c.attendance!!.book;val lectures=book.lectures();val first=book.students().single {it.student().studentNumber()=="0001"}.enrollment().id()
        onNodeWithTag("attendance-search").performTextInput("0001")
        onNodeWithTag("bulk-attendance").performClick();assertContains(c.confirmation!!.message,"including hidden and withdrawn")
        onNodeWithTag("cancel-confirm").performClick();assertFalse(c.attendance!!.dirty)
        onNodeWithTag("bulk-attendance").performClick();onNodeWithTag("confirm").performClick()
        assertTrue(book.students().all {c.attendance!!.value(it.enrollment().id())==AttendanceStatus.PRESENT})
        onNodeWithTag("attendance-lecture").performClick();onNodeWithTag("attendance-lecture-${lectures[1].id()}").performClick()
        onNodeWithTag("cancel-confirm").performClick();assertEquals(lectures[0].id(),c.attendance!!.lectureId);assertTrue(c.attendance!!.dirty)
        var left=false;runOnIdle {c.guard {left=true}};onNodeWithTag("cancel-confirm").performClick();assertFalse(left)
        runOnIdle {c.select(null)};onNodeWithTag("cancel-confirm").performClick();assertEquals(id,c.offeringId)
        onNodeWithTag("save-attendance").performClick();settled(c)
        assertTrue(c.attendance!!.book.students().all {c.attendance!!.book.status(it.enrollment().id(),lectures[0].id())==AttendanceStatus.PRESENT})
        onNodeWithTag("attendance-bulk-status").performClick();onNodeWithTag("attendance-bulk-status-UNMARKED").performClick()
        onNodeWithTag("bulk-attendance").performClick();onNodeWithTag("confirm").performClick()
        onNodeWithTag("save-attendance").performClick();settled(c)
        assertTrue(c.attendance!!.book.records().none {it.lectureId()==lectures[0].id()})
        // The current session stays selected after save and clear.
        assertEquals(lectures[0].id(),c.attendance!!.lectureId)
        runOnIdle {c.chooseAttendanceLecture(lectures[2].id())}
        onNodeWithTag("attendance-readonly").assertExists();onNodeWithTag("bulk-attendance").assertIsNotEnabled()
        onNodeWithTag("attendance-$first-PRESENT").assertIsNotEnabled()
        runOnIdle {c.chooseAttendanceLecture(lectures[1].id());c.attendance!!.set(first,AttendanceStatus.ABSENT)}
        // A concurrent cancellation is still rejected by the Java service; no staged marks disappear.
        services.academic.lectures().update(id,lectures[1].id(),lectureInput(2,LectureStatus.CANCELLED))
        runOnIdle {c.saveAttendance()};settled(c);assertNotNull(c.error);assertTrue(c.attendance!!.dirty)
        onNodeWithTag("reload-attendance").performClick();onNodeWithTag("confirm").performClick();settled(c)
        assertTrue(c.attendance!!.readOnly);assertFalse(c.attendance!!.dirty)
        assertNotEquals(AttendanceStatus.ABSENT,services.academic.attendance().load(id).status(first,lectures[1].id()))
    }

    @Test fun keyboardMovesAcrossVirtualRosterAndKeepsAllMarks()=runSkikoComposeUiTest(size=Size(864f,950f)) {
        val (services,id)=fixture();val a=services.academic
        for(n in 4..14)a.enrollments().enroll(a.students().create(StudentInput(n.toString().padStart(4,'0'),"Student","$n","","")).id(),id)
        lateinit var c:WorkspaceController
        setContent {val scope=rememberCoroutineScope();c=remember {WorkspaceController(services,scope)};LaunchedEffect(Unit){c.select(id)}
            Box(Modifier.fillMaxSize()){AttendanceScreen(c);WorkspaceOverlays(c)}}
        settled(c);val students=c.attendance!!.book.students().sortedBy {it.student().studentNumber()}
        fun row(n:Int)=onNodeWithTag("attendance-row-${students[n].enrollment().id()}")
        row(0).performClick().performKeyInput {pressKey(Key.E)}
        for(n in 0..11){row(n).performKeyInput {pressKey(Key.DirectionDown)};row(n+1).assertIsFocused()}
        row(12).performKeyInput {pressKey(Key.L)}
        assertEquals(AttendanceStatus.EXCUSED,c.attendance!!.value(students[0].enrollment().id()))
        assertEquals(AttendanceStatus.LATE,c.attendance!!.value(students[12].enrollment().id()))
        row(12).performKeyInput {pressKey(Key.U)};assertNull(c.attendance!!.value(students[12].enrollment().id()))
        runOnIdle {c.guard {c.select(null)}};onNodeWithTag("confirm").performClick();settled(c)
        onNodeWithText("Choose a teaching context to record attendance.").assertExists();assertNull(c.attendance)
    }
    private fun ComposeUiTest.settled(c:WorkspaceController){waitForIdle();waitUntil(timeoutMillis=10000){!c.busy};waitForIdle()}
    private fun ComposeUiTest.capture(name:String){waitForIdle();val file=File(System.getProperty("compose.test.output"),"$name.png");file.parentFile.mkdirs();ImageIO.write(onNodeWithTag("dashboard-root").captureToImage().toAwtImage(),"png",file)}
}
