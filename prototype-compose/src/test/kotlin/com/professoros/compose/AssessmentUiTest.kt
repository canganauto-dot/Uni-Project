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
import java.math.BigDecimal
import java.time.LocalDate
import javax.imageio.ImageIO
import kotlin.test.*

class AssessmentUiTest {
    @get:Rule val folder=TemporaryFolder()
    private fun question(course:Long,type:QuestionType=QuestionType.SHORT_ANSWER,text:String="Explain encapsulation") = QuestionInput(course,type,text,"OOP",Difficulty.MEDIUM,BigDecimal("5"),if(type==QuestionType.TRUE_FALSE)"TRUE" else "Model answer","Explanation","",if(type==QuestionType.MCQ)listOf(AnswerChoice("A",true),AnswerChoice("B",false)) else emptyList())

    @Test fun allQuestionTypesValidationEditingSearchAndDelete()=runSkikoComposeUiTest(size=Size(1360f,860f)) {
        val path=folder.root.toPath().resolve("questions.db");fixture(path)
        val services=WorkspaceServices(path);val course=services.courses.findAll().first().id()
        lateinit var c:WorkspaceController
        setContent {val scope=rememberCoroutineScope();c=remember {WorkspaceController(services,scope)};LaunchedEffect(Unit){c.refresh()}
            Box(Modifier.fillMaxSize()){QuestionScreen(c);WorkspaceOverlays(c)}}
        settled(c)
        onNodeWithTag("new-question").performClick()
        runOnIdle {c.questionDraft!!.form.values["course"]=course.toString();c.questionDraft!!.form.values["text"]="ما هي الكبسلة؟"}
        onNodeWithTag("save-question").performClick();settled(c);assertNotNull(c.error)
        onNodeWithTag("choice-0").performScrollTo().performTextInput("إخفاء البيانات")
        onNodeWithTag("choice-1").performScrollTo().performTextInput("خيار آخر")
        onNodeWithTag("correct-0").performScrollTo().performClick()
        capture("migration-mcq-form","editor-modal")
        onNodeWithTag("save-question").performClick();settled(c)
        assertEquals(1,c.questions.size);assertTrue(c.questions.single().choices().first().correct())
        for(type in QuestionType.entries.filter {it!=QuestionType.MCQ}) {
            runOnIdle {c.editQuestion(courseId=course);c.questionDraft!!.form.values.apply {put("type",type.name);put("text","Question ${type.name}");put("answer",if(type==QuestionType.TRUE_FALSE)"TRUE" else "Answer")}}
            onNodeWithTag("save-question").performClick();settled(c)
        }
        assertEquals(5,c.questions.size)
        onNodeWithTag("question-search").performTextInput("الكبسلة")
        val mcq=c.questions.single {it.type()==QuestionType.MCQ}
        onNodeWithTag("questions-table-"+mcq.id()).assertExists()
        runOnIdle {c.editQuestion(mcq);c.questionDraft!!.form.values["marks"]="0"}
        onNodeWithTag("save-question").performClick();settled(c);assertNotNull(c.questionDraft);assertNotNull(c.error)
        runOnIdle {c.questionDraft!!.form.values["marks"]="3.5"}
        onNodeWithTag("save-question").performClick();settled(c)
        assertEquals(BigDecimal("3.50"),c.questions.single {it.id()==mcq.id()}.marks())
        runOnIdle {c.mutate {services.academic.questions().delete(mcq.id())}};settled(c)
        assertEquals(4,c.questions.size)
        onNodeWithTag("question-search").performTextClearance();capture("migration-question-bank")
    }

    @Test fun manualExamGenerationSnapshotsGuardsAndFinalization()=runSkikoComposeUiTest(size=Size(1360f,960f)) {
        val path=folder.root.toPath().resolve("exams.db");fixture(path)
        val services=WorkspaceServices(path);val offering=services.academic.offerings().findAll().first()
        val bank=listOf(services.academic.questions().create(question(offering.courseId(),QuestionType.MCQ,"MCQ original")),services.academic.questions().create(question(offering.courseId())))
        val foreign=services.academic.questions().create(question(services.courses.findAll().last().id(),text="Foreign course"))
        lateinit var c:WorkspaceController
        setContent {val scope=rememberCoroutineScope();c=remember {WorkspaceController(services,scope)};LaunchedEffect(Unit){c.select(offering.id())}
            Box(Modifier.fillMaxSize()){ExamScreen(c);WorkspaceOverlays(c)}}
        settled(c);onNodeWithTag("new-exam").performClick();settled(c)
        assertEquals(2,c.examDraft!!.bank.size)
        onNodeWithTag("exam-title").performTextInput("Midterm")
        onNodeWithTag("exam-date").performTextInput(LocalDate.now().toString())
        onNodeWithTag("exam-intended").performTextReplacement("10")
        for(q in bank) {onNodeWithTag("exam-bank-table-"+q.id()).performScrollTo().performClick();onNodeWithTag("add-exam-question").performScrollTo().performClick()}
        assertEquals(BigDecimal("10.00"),c.examDraft!!.total)
        onNodeWithTag("exam-tab-Composition").performScrollTo().performClick()
        onNodeWithTag("move-up-1").performScrollTo().performClick()
        assertEquals(bank.last().id(),c.examDraft!!.selected.first().questionId())
        capture("migration-exam-composition","editor-modal")
        runOnIdle {c.guard {fail("Must not navigate while dirty")}}
        onNodeWithTag("cancel-confirm").performClick();assertEquals(2,c.examDraft!!.selected.size)
        onNodeWithTag("save-exam").performClick();settled(c)
        val exam=c.exams.single();assertEquals(bank.last().id(),exam.questions().first().questionId())
        services.academic.questions().update(bank.first().id(),question(offering.courseId(),QuestionType.MCQ,"Live bank changed"))
        runOnIdle {c.openExam(exam.id())};settled(c)
        assertEquals("MCQ original",c.examDraft!!.bank.single {it.id()==bank.first().id()}.text())
        onNodeWithTag("exam-tab-Generate").performScrollTo().performClick()
        onNodeWithTag("generation-count").performScrollTo().performTextReplacement("3")
        onNodeWithTag("generate-selection").performScrollTo().performClick();onNodeWithTag("confirm").performClick();settled(c)
        assertNotNull(c.error);assertEquals(2,c.examDraft!!.selected.size)
        onNodeWithTag("generation-count").performScrollTo().performTextReplacement("2")
        onNodeWithTag("quota-MEDIUM").performScrollTo().performTextInput("2")
        onNodeWithTag("generate-selection").performScrollTo().performClick();onNodeWithTag("confirm").performClick();settled(c)
        assertNull(c.error);assertEquals(2,c.examDraft!!.selected.size)
        onNodeWithTag("save-exam").performClick();settled(c)
        assertFailsWith<DomainValidationException>{services.academic.exams().save(offering.id(),exam.id(),ExamInput("Invalid",LocalDate.now(),60,BigDecimal.TEN,""),listOf(foreign.id()))}
        onNodeWithTag("exams-table-"+exam.id()).performClick()
        onNodeWithText("Finalize").performClick();onNodeWithTag("confirm").performClick();settled(c)
        assertEquals(ExamStatus.FINALIZED,c.exams.single().status())
        assertEquals(1,services.academic.questions().findById(bank.first().id()).usageCount())
        onNodeWithText("Archive").performClick();onNodeWithTag("confirm").performClick();settled(c)
        assertEquals(ExamStatus.ARCHIVED,c.exams.single().status())
        val reopened=WorkspaceServices(path);reopened.initialize()
        assertEquals("MCQ original",reopened.academic.exams().findById(offering.id(),exam.id()).questions().single {it.questionId()==bank.first().id()}.text())
    }
    private fun ComposeUiTest.settled(c:WorkspaceController){waitForIdle();waitUntil(timeoutMillis=10000){!c.busy};waitForIdle()}
    private fun ComposeUiTest.capture(name:String,tag:String?=null) {
        waitForIdle();val node=if(tag==null)onRoot() else onNodeWithTag(tag);val file=File(System.getProperty("compose.test.output"),"$name.png")
        ImageIO.write(node.captureToImage().toAwtImage(),"png",file)
    }
}
