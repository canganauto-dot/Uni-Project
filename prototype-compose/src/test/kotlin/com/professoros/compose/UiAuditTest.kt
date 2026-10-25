@file:OptIn(androidx.compose.ui.test.InternalTestApi::class, androidx.compose.ui.test.ExperimentalTestApi::class)
package com.professoros.compose

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.*
import com.professoros.model.*
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import javax.imageio.ImageIO
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.*

class UiAuditTest {
    @get:Rule val folder=TemporaryFolder()
    @Test fun selectorsSupportKeyboardSearchEmptyResultsAndVirtualScrolling()=runSkikoComposeUiTest(size=Size(864f,621f)) {
        var selected by mutableStateOf("")
        setContent {Select("Choice",selected,(0..49).map {Choice("$it","Option $it")},{selected=it},"audit-select")}
        onNodeWithTag("audit-select").performClick()
        onNodeWithTag("audit-select-search").performKeyInput {repeat(30){pressKey(Key.DirectionDown)};pressKey(Key.Enter)}
        assertEquals("30",selected)
        onNodeWithTag("audit-select-search").assertDoesNotExist()
        onNodeWithTag("audit-select").performClick()
        onNodeWithTag("audit-select-search").performTextInput("missing")
        onNodeWithText("No matching options").assertIsDisplayed()
        onNodeWithTag("audit-select-search").performKeyInput {pressKey(Key.Enter)}
        assertEquals("30",selected)
        onNodeWithTag("audit-select-search").performTextReplacement("Option 49")
        onNodeWithTag("audit-select-search").performKeyInput {pressKey(Key.Enter)}
        assertEquals("49",selected)
        onNodeWithTag("audit-select").performClick()
        onNodeWithTag("audit-select-search").performKeyInput {pressKey(Key.Escape)}
        onNodeWithTag("audit-select-search").assertDoesNotExist()
    }
    @Test fun newDestinationStartsAtTopWithoutClearingStateOnThemeChange()=runSkikoComposeUiTest(size=Size(864f,621f)) {
        val data=fixture(folder.root.toPath().resolve("scroll.db")).load(null)
        var page by mutableStateOf("Courses");var light by mutableStateOf(false)
        setContent {CompositionLocalProvider(LocalPalette provides if(light)LightPalette else DarkPalette) {
            ProfessorDashboard(data,false,null,page,{page=it},{},{},featureContent={p->Column {
                repeat(35){Copy("$p row $it",modifier=Modifier.testTag("audit-row-$it"))}
            }})
        }}
        onNodeWithTag("audit-row-34").performScrollTo()
        fun offset()=onNodeWithTag("page-scroll").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val scrolled=offset();assertTrue(scrolled>0)
        runOnIdle {light=true};waitForIdle();assertEquals(scrolled,offset())
        onNodeWithTag("nav-Students").performScrollTo().performClick();waitForIdle()
        assertEquals(0f,offset());onNodeWithTag("audit-row-0").assertIsDisplayed()
    }
    @Test fun batchOfferingReadPreservesOriginalLabelsOrderAndSelection() {
        val path=folder.root.toPath().resolve("offerings.db");val bridge=fixture(path)
        val offerings=WorkspaceServices(path).academic.offerings()
        val original=offerings.findAll().map {offerings.summary(it.id())}
        assertEquals(original,bridge.load(null).offerings)
        assertEquals(original.first(),bridge.load(null).selected)
        assertEquals(original.last(),bridge.load(original.last().offering().id()).selected)
        assertEquals(original.first(),bridge.load(Long.MAX_VALUE).selected)
    }
    @Test fun gradeSummaryCacheTracksEditsInvalidScoresAndDiscard() {
        val path=folder.root.toPath().resolve("cache.db");val data=fixture(path).load(null)
        val s=WorkspaceServices(path);val id=data.selected!!.offering().id();val grades=s.academic.grades()
        val category=grades.saveCategory(id,null,"Coursework",BigDecimal("100"))
        val assessment=grades.saveAssessment(id,null,category.id(),"Project",BigDecimal("100"),LocalDate.now())
        val editor=GradebookEditor(grades.load(id));val enrollment=editor.book.students().first().enrollment().id()
        val key=GradeKey(enrollment,assessment.id());val original=editor.summary()
        repeat(100){assertSame(original,editor.summary(),"Unrelated UI reads must reuse the calculated summary")}
        editor.set(key,"0");val zero=editor.summary();assertNotSame(original,zero)
        assertTrue(zero.results.getValue(enrollment).complete());assertSame(zero,editor.summary())
        editor.set(key,"101");assertNotNull(editor.summary().error)
        editor.discard();assertNull(editor.summary().error);assertFalse(editor.summary().results.getValue(enrollment).complete())
    }
    @Test fun allRoutesAtMinimumSizeInBothThemesAndScaledDensity()=runSkikoComposeUiTest(size=Size(1400f,1100f)) {
        val path=folder.root.toPath().resolve("audit.db");val bridge=fixture(path);val data=bridge.load(null)
        val s=WorkspaceServices(path);val id=data.selected!!.offering().id()
        val category=s.academic.grades().saveCategory(id,null,"Coursework",BigDecimal("100"))
        s.academic.grades().saveAssessment(id,null,category.id(),"Project",BigDecimal("100"),LocalDate.now())
        val questions=QuestionType.entries.map {type->s.academic.questions().create(QuestionInput(data.selected!!.offering().courseId(),type,
            "Explain interaction design · تصميم التفاعل", "Design",Difficulty.MEDIUM,BigDecimal("5"),if(type==QuestionType.TRUE_FALSE)"TRUE" else "Model answer","Explanation","",
            if(type==QuestionType.MCQ)listOf(AnswerChoice("One",true),AnswerChoice("Two",false)) else emptyList()))}
        s.academic.exams().save(id,null,ExamInput("Design review",LocalDate.now(),60,BigDecimal("25"),"Read every question."),questions.map {it.id()})
        val native=File(System.getProperty("compose.test.output"),"../native-ui-audit").apply {mkdirs()}
        java.nio.file.Files.copy(path,native.resolve("professor-os.db").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        var page by mutableStateOf("Overview");var light by mutableStateOf(false);var scale by mutableStateOf(1f)
        lateinit var c:WorkspaceController
        setContent {
            val scope=rememberCoroutineScope();c=remember {WorkspaceController(s,scope)}
            val preferences=remember {AppearanceSettings(path,scope)}
            LaunchedEffect(Unit){c.select(id)}
            CompositionLocalProvider(LocalPalette provides if(light)LightPalette else DarkPalette,LocalDensity provides Density(scale)) {
                Box(Modifier.requiredSize(864.dp,621.dp)) {
                    ProfessorDashboard(data,c.busy,null,page,{page=it},{},{},featureContent={p->when(p) {
                        "Teaching context"->RosterScreen(c);"Lectures"->LectureScreen(c);"Question Bank"->QuestionScreen(c)
                        "Exam Builder"->ExamScreen(c);"Gradebook"->GradebookScreen(c);"Attendance"->AttendanceScreen(c)
                        "Settings"->SettingsScreen(preferences) {page="Terms"};else->CatalogScreen(p,c){}
                    }})
                    WorkspaceOverlays(c)
                }
            }
        }
        waitForIdle();waitUntil(timeoutMillis=10000){!c.busy}
        val output=File(System.getProperty("compose.test.output"))
        for(theme in listOf(false,true))for(density in listOf(1f,1.5f)) {
            runOnIdle {light=theme;scale=density}
            for(route in listOf("Overview","Courses","Students","Terms","Offerings","Teaching context","Lectures","Question Bank","Exam Builder","Gradebook","Attendance","Activity","Settings")) {
                runOnIdle {page=route};waitForIdle()
                onNodeWithTag("refresh").assertIsDisplayed()
                ImageIO.write(onNodeWithTag("dashboard-root").captureToImage().toAwtImage(),"png",File(output,"audit-${if(theme)"light" else "dark"}-$density-${route.replace(' ','-')}.png"))
            }
        }
    }
}
