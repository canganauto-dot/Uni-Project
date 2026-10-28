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
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import javax.imageio.ImageIO
import kotlin.test.*

class GradebookUiTest {
    @get:Rule val folder=TemporaryFolder()
    private fun fixture():Pair<WorkspaceServices,Long> {
        val s=WorkspaceServices(folder.root.toPath().resolve("grades.db"));s.initialize()
        val course=s.courses.create(CourseInput("CS201","هندسة البرمجيات","Computing",3,""))
        val a=s.academic;val term=a.terms().create(AcademicTermInput("Fall","2026–2027",LocalDate.of(2026,9,1),LocalDate.of(2027,1,1),AcademicStatus.ACTIVE))
        val offering=a.offerings().create(CourseOfferingInput(course.id(),term.id(),"A","",AcademicStatus.ACTIVE))
        listOf(StudentInput("0001","أحمد","علي","",""),StudentInput("0002","Sara","Hassan","","")).forEach {a.enrollments().enroll(a.students().create(it).id(),offering.id())}
        val category=a.grades().saveCategory(offering.id(),null,"Assignments",BigDecimal("100"))
        a.grades().saveAssessment(offering.id(),null,category.id(),"Design review",BigDecimal("20"),LocalDate.now())
        a.grades().saveAssessment(offering.id(),null,category.id(),"Project",BigDecimal("80"),LocalDate.now())
        return s to offering.id()
    }
    @Test fun spreadsheetKeyboardBlankZeroBulkValidationAndRestart()=runSkikoComposeUiTest(size=Size(1360f,860f)) {
        val (services,id)=fixture();lateinit var c:WorkspaceController
        val data=DashboardBridge(folder.root.toPath().resolve("grades.db")).load(id)
        var width by mutableStateOf(1360);var height by mutableStateOf(860);var page by mutableStateOf("Overview")
        setContent {
            val scope=rememberCoroutineScope();c=remember {WorkspaceController(services,scope)}
            LaunchedEffect(Unit){c.select(id)}
            CompositionLocalProvider() {
                Box(Modifier.requiredSize(width.dp,height.dp)) {
                    ProfessorDashboard(data,c.busy,null,page,{page=it},{c.guard{c.refresh()}},{},featureContent={GradebookScreen(c)})
                    WorkspaceOverlays(c)
                }
            }
        }
        settled(c)
        onNodeWithTag("nav-Gradebook").performClick();capture("gradebook-initial");val book=c.gradebook!!.book;val first=book.students().single {it.student().studentNumber()=="0001"};val second=book.students().single {it.student().studentNumber()=="0002"}
        val a=book.assessments().first();val b=book.assessments().last()
        fun tag(student:EnrolledStudent,assessment:Assessment)="grade-cell-${student.enrollment().id()}-${assessment.id()}"
        val key=GradeKey(first.enrollment().id(),a.id())
        onNodeWithTag(tag(first,a)).performClick().performTextInput("0")
        onNodeWithTag(tag(first,a)).performKeyInput {pressKey(Key.Tab)}
        onNodeWithTag(tag(first,b)).assertIsFocused()
        onNodeWithTag(tag(first,b)).performTextInput("80")
        onNodeWithTag(tag(first,b)).performKeyInput {pressKey(Key.Enter)}
        onNodeWithTag(tag(second,b)).assertIsFocused()
        assertTrue(c.gradebook!!.dirty)
        assertEquals("B",c.gradebook!!.summary().results.getValue(first.enrollment().id()).letter())
        assertFalse(c.gradebook!!.summary().results.getValue(second.enrollment().id()).complete())
        onNodeWithTag(tag(second,b)).performTextInput("81")
        onNodeWithTag("save-grades").performClick();settled(c)
        assertNotNull(c.error);assertTrue(c.gradebook!!.dirty);assertTrue(services.academic.grades().load(id).grades().isEmpty(),"Invalid batch must not partially persist")
        onNodeWithTag(tag(second,b)).performTextReplacement("")
        onNodeWithTag(tag(first,a)).performClick().performKeyInput {keyDown(Key.CtrlLeft);pressKey(Key.S);keyUp(Key.CtrlLeft)}
        settled(c);assertFalse(c.gradebook!!.dirty)
        assertEquals(BigDecimal("0"),services.academic.grades().load(id).grades().single {it.key()==key}.score().stripTrailingZeros())
        onNodeWithTag(tag(first,a)).performTextReplacement("")
        assertFalse(c.gradebook!!.summary().results.getValue(first.enrollment().id()).complete())
        var left=false
        runOnIdle {c.guard {left=true}};onNodeWithTag("cancel-confirm").performClick()
        assertFalse(left);assertTrue(c.gradebook!!.dirty)
        runOnIdle {c.editGradeCategory()};assertNotNull(c.error);assertNull(c.form)
        // A failed bulk batch is atomic even within the unsaved draft.
        onNodeWithTag("bulk-grades").performClick()
        onNodeWithTag("grade-bulk-values").performTextInput("0001\t10\n0002\t99")
        onNodeWithTag("apply-grade-bulk").performClick();assertNotNull(c.gradeBulk);assertEquals("",c.gradebook!!.value(key))
        onNodeWithTag("grade-bulk-values").performTextReplacement("0001\t10\n0002\t0")
        onNodeWithTag("apply-grade-bulk").performClick();assertNull(c.gradeBulk);assertEquals("10",c.gradebook!!.value(key))
        onNodeWithTag("save-grades").performClick();settled(c)
        capture("gradebook-compose-normal")
        onNodeWithTag("grade-search").performTextInput("أحمد")
        onNodeWithTag(tag(second,a)).assertDoesNotExist()
        onNodeWithTag("grade-search").performTextReplacement("")
        runOnIdle {width=864;height=621};capture("gradebook-compose-minimum")
        val reopened=WorkspaceServices(folder.root.toPath().resolve("grades.db"));reopened.initialize()
        val restored=GradebookEditor(reopened.academic.grades().load(id))
        assertEquals("10.00",restored.value(key));assertEquals("0.00",restored.value(GradeKey(second.enrollment().id(),a.id())))
        assertEquals("",restored.value(GradeKey(second.enrollment().id(),b.id())))
        assertEquals("A",restored.summary().results.getValue(first.enrollment().id()).letter())
        assertFalse(restored.summary().results.getValue(second.enrollment().id()).complete())
        runOnIdle {c.gradebook!!.set(key,"");c.saveGrades()};settled(c)
        assertTrue(reopened.academic.grades().load(id).grades().none {it.key()==key},"Saving blank must remove the score, not store zero")
    }
    @Test fun configurationCrudWeightRulesAndScopeGuards()=runSkikoComposeUiTest(size=Size(1200f,900f)) {
        val (services,id)=fixture();lateinit var c:WorkspaceController
        setContent {val scope=rememberCoroutineScope();c=remember {WorkspaceController(services,scope)};LaunchedEffect(Unit){c.select(id)}
            Box(Modifier.fillMaxSize()){GradebookScreen(c);WorkspaceOverlays(c)}}
        settled(c)
        val initial=c.gradebook!!.book
        runOnIdle {initial.students().forEach {student->initial.assessments().forEach {a->c.gradebook!!.set(GradeKey(student.enrollment().id(),a.id()),a.maximumMarks().toPlainString())}};c.saveGrades()};settled(c)
        assertTrue(c.gradebook!!.summary().results.values.all {it.complete()})
        onNodeWithTag("grade-tab-Categories & scale").performClick()
        onNodeWithTag("new-grade-category").performClick()
        onNodeWithTag("field-name").performTextInput("Quizzes")
        onNodeWithTag("field-weight").performTextInput("-1")
        onNodeWithTag("save-form").performClick();settled(c);assertNotNull(c.error)
        onNodeWithTag("field-weight").performTextReplacement("20")
        onNodeWithTag("save-form").performClick();settled(c)
        val extra=c.gradebook!!.book.categories().single {it.name()=="Quizzes"}
        onNodeWithTag("grade-weights").assertTextContains("120",substring=true)
        assertTrue(c.gradebook!!.summary().results.values.none {it.complete()})
        onNodeWithTag("grading-scale").performClick()
        onNodeWithTag("field-a").performTextReplacement("70")
        onNodeWithTag("save-form").performClick();settled(c);assertNotNull(c.error)
        onNodeWithTag("field-a").performTextReplacement("95")
        onNodeWithTag("save-form").performClick();settled(c)
        assertEquals(BigDecimal("95.00"),c.gradebook!!.book.scale().a())
        onNodeWithTag("grade-tab-Assessments").performClick();onNodeWithTag("new-assessment").performClick()
        onNodeWithTag("field-title").performTextInput("Quiz 1")
        runOnIdle {c.form!!.values["category"]=extra.id().toString()}
        onNodeWithTag("save-form").performClick();settled(c)
        val added=c.gradebook!!.book.assessments().single {it.title()=="Quiz 1"}
        runOnIdle {c.editAssessment(added);c.form!!.values["title"]="Quiz revised";c.saveForm()};settled(c)
        assertEquals("Quiz revised",c.gradebook!!.book.assessments().single {it.id()==added.id()}.title())
        runOnIdle {c.mutate {services.academic.grades().deleteCategory(id,extra.id())}};settled(c);assertNotNull(c.error)
        onNodeWithTag("grade-assessments-${added.id()}").performClick();onNodeWithText("Delete").performClick()
        onNodeWithTag("cancel-confirm").performClick();assertTrue(c.gradebook!!.book.assessments().any {it.id()==added.id()})
        onNodeWithText("Delete").performClick();onNodeWithTag("confirm").performClick();settled(c)
        onNodeWithTag("grade-tab-Categories & scale").performClick();onNodeWithTag("grade-categories-${extra.id()}").performClick()
        onNodeWithText("Delete").performClick();onNodeWithTag("confirm").performClick();settled(c)
        assertEquals(1,c.gradebook!!.book.categories().size)
        val key=GradeKey(c.gradebook!!.book.students().first().enrollment().id(),c.gradebook!!.book.assessments().first().id())
        runOnIdle {c.gradebook!!.set(key,"7");c.guard {c.select(null)}}
        onNodeWithTag("cancel-confirm").performClick();assertEquals(id,c.offeringId);assertEquals("7",c.gradebook!!.value(key))
        runOnIdle {c.guard {c.select(null)}};onNodeWithTag("confirm").performClick();settled(c);assertNull(c.gradebook)
        runOnIdle {c.select(id)};settled(c);assertEquals(initial.assessments().first().maximumMarks().toPlainString(),c.gradebook!!.value(key))
    }
    @Test fun keyboardTraversesVirtualRowsAndWideColumnsWithoutLosingEdits()=runSkikoComposeUiTest(size=Size(864f,800f)) {
        val (services,id)=fixture();val a=services.academic
        for(n in 3..14) a.enrollments().enroll(a.students().create(StudentInput(n.toString().padStart(4,'0'),"Student","$n","","")).id(),id)
        val category=a.grades().load(id).categories().first()
        for(n in 3..6)a.grades().saveAssessment(id,null,category.id(),"Task $n",BigDecimal("10"),LocalDate.now())
        lateinit var c:WorkspaceController
        setContent {val scope=rememberCoroutineScope();c=remember {WorkspaceController(services,scope)};LaunchedEffect(Unit){c.select(id)}
            CompositionLocalProvider(){Box(Modifier.fillMaxSize()){GradebookScreen(c);WorkspaceOverlays(c)}}}
        settled(c)
        val book=c.gradebook!!.book;val students=book.students().sortedBy {it.student().studentNumber()};val assessments=book.assessments()
        fun cell(row:Int,col:Int)=onNodeWithTag("grade-cell-${students[row].enrollment().id()}-${assessments[col].id()}")
        cell(0,0).performClick().performTextInput("4")
        for(row in 0..11){cell(row,0).performKeyInput {pressKey(Key.Enter)};cell(row+1,0).assertIsFocused()}
        for(col in 0..4){cell(12,col).performKeyInput {pressKey(Key.Tab)};cell(12,col+1).assertIsFocused()}
        cell(12,5).performTextInput("9")
        cell(12,5).performKeyInput {keyDown(Key.ShiftLeft);pressKey(Key.Tab);keyUp(Key.ShiftLeft)}
        cell(12,4).assertIsFocused()
        assertEquals("4",c.gradebook!!.value(GradeKey(students[0].enrollment().id(),assessments[0].id())))
        assertEquals("9",c.gradebook!!.value(GradeKey(students[12].enrollment().id(),assessments[5].id())))
        // A direct context request is guarded too; no stale sheet survives a different offering scope.
        runOnIdle {c.select(null)};assertNotNull(c.confirmation)
        onNodeWithTag("cancel-confirm").performClick();assertEquals(id,c.offeringId)
    }
    @Test fun eUsesConfiguredPassMinimumInTheEditorAndScaleForm()=runSkikoComposeUiTest(size=Size(1200f,900f)) {
        val (services,id)=fixture()
        services.academic.grades().saveScale(id,GradingScale(BigDecimal("90"),BigDecimal("80"),BigDecimal("70"),BigDecimal("60"),BigDecimal("50")))
        lateinit var c:WorkspaceController
        setContent {val scope=rememberCoroutineScope();c=remember {WorkspaceController(services,scope)};LaunchedEffect(Unit){c.select(id)}
            Box(Modifier.fillMaxSize()){GradebookScreen(c);WorkspaceOverlays(c)}}
        settled(c)
        val book=c.gradebook!!.book;val student=book.students().first().enrollment().id()
        val first=book.assessments().first().id();val second=book.assessments().last().id()
        onNodeWithTag("grade-cell-$student-$first").performClick().performTextInput("0")
        onNode(hasText("0% · — · INCOMPLETE") and hasAnyAncestor(hasTestTag("grade-result-$student")),useUnmergedTree=true).assertExists()
        for((score,letter) in listOf("49.99" to "F · FAIL","50" to "E · PASS","59.99" to "E · PASS","60" to "D · PASS")) {
            onNodeWithTag("grade-cell-$student-$second").performTextReplacement(score)
            onNodeWithText("$score% · $letter").assertExists()
        }
        onNodeWithTag("save-grades").performClick();settled(c)
        onNodeWithTag("grade-tab-Categories & scale").performClick()
        onNodeWithText("E / Pass 50%",substring=true).assertExists()
        onNodeWithTag("grading-scale").performClick()
        onNodeWithText("E / Pass minimum %").assertExists()
        onNodeWithTag("field-pass").performTextReplacement("55")
        onNodeWithTag("save-form").performClick();settled(c)
        assertEquals(BigDecimal("55.00"),c.gradebook!!.book.scale().pass())
        onNodeWithTag("grade-tab-Grades").performClick()
        onNodeWithTag("grade-cell-$student-$second").performTextReplacement("54.99")
        onNodeWithText("54.99% · F · FAIL").assertExists()
        onNodeWithTag("grade-cell-$student-$second").performTextReplacement("55")
        onNodeWithText("55% · E · PASS").assertExists()
    }
    private fun ComposeUiTest.settled(c:WorkspaceController){waitForIdle();waitUntil(timeoutMillis=10000){!c.busy};waitForIdle()}
    private fun ComposeUiTest.capture(name:String){waitForIdle();val file=File(System.getProperty("compose.test.output"),"$name.png");file.parentFile.mkdirs();ImageIO.write(onNodeWithTag("dashboard-root").captureToImage().toAwtImage(),"png",file)}
}
