@file:OptIn(androidx.compose.ui.test.InternalTestApi::class, androidx.compose.ui.test.ExperimentalTestApi::class)
package com.professoros.compose

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.professoros.model.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.ss.usermodel.CellType
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.math.BigDecimal
import java.time.LocalDate
import javax.imageio.ImageIO
import kotlin.test.*

class DocumentWorkflowUiTest {
    @get:Rule val folder=TemporaryFolder()
    private fun setup():Pair<WorkspaceServices,Long> {
        val path=folder.root.toPath().resolve("documents.db");fixture(path)
        val s=WorkspaceServices(path)
        val course=s.courses.findAll().single {it.courseCode()=="CS301"}
        return s to s.academic.offerings().findByCourse(course.id()).first().id()
    }
    private fun workbook():Path {
        val path=folder.root.toPath().resolve("students.xlsx")
        XSSFWorkbook().use {w->
            val s=w.createSheet("الطلبة")
            listOf("رقم الطالب","اسم الطالب","البريد الإلكتروني","الشعبة").forEachIndexed {i,v->s.createRowIfMissing(0).createCell(i).setCellValue(v)}
            listOf(listOf("00001","أحمد علي","","A"),listOf("42","Sara Hassan","","A"),listOf("duplicate","One Student","","A"),listOf("duplicate","Other Student","","A"),listOf("formula","Formula Student","","A"),listOf("001","Student 1","","A")).forEachIndexed {i,row->row.forEachIndexed {col,v->s.createRowIfMissing(i+1).createCell(col).setCellValue(v)}}
            s.getRow(2).getCell(0).apply {setCellValue(42.0);cellStyle=w.createCellStyle().apply {dataFormat=w.createDataFormat().getFormat("00000")}}
            s.getRow(5).getCell(0).cellFormula="1+1"
            s.createRow(8)
            Files.newOutputStream(path).use {w.write(it)}
        };return path
    }
    @Test fun importPreviewMappingValidationGlobalAndEnrollmentPersistence()=runSkikoComposeUiTest(size=Size(1100f,950f)) {
        val (s,id)=setup();val file=workbook();lateinit var c:WorkspaceController;var compact by mutableStateOf(false)
        setContent {val scope=rememberCoroutineScope();c=remember {WorkspaceController(s,scope)};LaunchedEffect(Unit){c.select(id)}
            Box(Modifier.requiredSize(if(compact)864.dp else 1100.dp,if(compact)621.dp else 950.dp)){CatalogScreen("Students",c){};WorkspaceOverlays(c)}}
        settled(c);onNodeWithTag("import-students").performClick()
        runOnIdle {c.loadStudentWorkbook {file}};settled(c)
        onNodeWithTag("preview-import").performClick();settled(c)
        assertEquals(2L,c.studentImport!!.reviewed!!.actionable())
        assertEquals(3,c.studentImport!!.reviewed!!.rows().count {it.action()==StudentImportRow.Action.ERROR})
        assertContains(c.studentImport!!.reviewed!!.rows().single {it.input().studentNumber()=="00042"}.message(),"Numeric ID")
        onNodeWithTag("map-ID").performScrollTo().performClick();onNodeWithTag("map-ID--1").performClick()
        assertNull(c.studentImport!!.reviewed)
        onNodeWithTag("preview-import").performClick();settled(c);assertContains(c.error.orEmpty(),"Map Student ID")
        onNodeWithTag("map-ID").performScrollTo().performClick();onNodeWithTag("map-ID-0").performClick()
        onNodeWithTag("preview-import").performClick();settled(c)
        onNodeWithTag("import-rows").performScrollTo()
        onNodeWithTag("import-rows-2").performScrollTo().performClick();onNodeWithTag("import-row-detail").performScrollTo()
        capture("documents-import-preview")
        runOnIdle {compact=true};capture("documents-import-minimum");runOnIdle {compact=false}
        runOnIdle {c.closeEditor()};onNodeWithTag("cancel-confirm").performClick();assertNotNull(c.studentImport)
        onNodeWithTag("confirm-import").performClick();onNodeWithTag("confirm").performClick();settled(c)
        assertEquals(StudentImportResult(2,0,1,3),c.studentImport!!.result)
        onNodeWithTag("confirm-import").assertIsNotEnabled()
        runOnIdle {c.closeEditor();c.openStudentImport(id);c.loadStudentWorkbook {file}};settled(c)
        onNodeWithTag("preview-import").performClick();settled(c)
        assertEquals(2L,c.studentImport!!.reviewed!!.actionable())
        onNodeWithTag("confirm-import").performClick();onNodeWithTag("confirm").performClick();settled(c)
        assertEquals(StudentImportResult(0,2,1,3),c.studentImport!!.result)
        val reopened=WorkspaceServices(folder.root.toPath().resolve("documents.db"));reopened.initialize()
        assertEquals("أحمد",reopened.academic.students().findAll().single {it.studentNumber()=="00001"}.firstName())
        assertEquals(14,reopened.academic.enrollments().findStudentsForOffering(id).size)
        val bad=folder.root.toPath().resolve("broken.xlsx");Files.writeString(bad,"not a workbook")
        runOnIdle {c.loadStudentWorkbook {bad}};settled(c);assertNull(c.studentImport!!.reviewed);assertContains(c.error.orEmpty(),"valid .xlsx")
    }

    @Test fun pdfSnapshotsConfidentialityPaginationAndSafeExport()=runSkikoComposeUiTest(size=Size(1100f,950f)) {
        val (s,id)=setup();val course=s.academic.offerings().findById(id).courseId()
        fun input(type:QuestionType,text:String)=QuestionInput(course,type,text,"Topic",Difficulty.MEDIUM,BigDecimal("5"),if(type==QuestionType.TRUE_FALSE)"TRUE" else "SECRET_MODEL_ANSWER","SECRET_EXPLANATION","",if(type==QuestionType.MCQ)listOf(AnswerChoice("Choice A",true),AnswerChoice("Choice B",false))else emptyList())
        val qs=QuestionType.entries.map {s.academic.questions().create(input(it,if(it==QuestionType.ESSAY)"Explain design. ".repeat(180) else "Saved ${it.name} question · مرحباً"))}
        val exam=s.academic.exams().save(id,null,ExamInput("Midterm",LocalDate.now(),90,BigDecimal("30"),"Read each question."),qs.map {it.id()})
        s.academic.questions().update(qs.first().id(),input(qs.first().type(),"LIVE_BANK_REPLACEMENT"))
        lateinit var c:WorkspaceController;var compact by mutableStateOf(false)
        setContent {val scope=rememberCoroutineScope();c=remember {WorkspaceController(s,scope)};LaunchedEffect(Unit){c.select(id)}
            Box(Modifier.requiredSize(if(compact)864.dp else 1100.dp,if(compact)621.dp else 950.dp)){ExamScreen(c);WorkspaceOverlays(c)}}
        settled(c);onNodeWithTag("exams-table-${exam.id()}").performClick();onNodeWithTag("open-pdf").performClick();settled(c)
        onNodeWithTag("render-pdf").performClick();settled(c);assertNotNull(c.error)
        onNodeWithTag("pdf-university").performTextInput("جامعة بغداد")
        onNodeWithTag("pdf-professor").performTextInput("Dr. Ahmed")
        onNodeWithTag("render-pdf").performClick();settled(c)
        val student=c.pdfExport!!.rendered!!;assertTrue(student.pageCount()>1)
        onNodeWithTag("save-pdf").assertIsNotEnabled()
        onNodeWithTag("pdf-acknowledge").performScrollTo().performClick()
        onNodeWithTag("pdf-next").performClick();settled(c);assertEquals(1,c.pdfExport!!.page)
        onNodeWithTag("pdf-image").performScrollTo();capture("documents-pdf-preview")
        runOnIdle {compact=true};capture("documents-pdf-minimum");runOnIdle {compact=false}
        val paper=folder.root.toPath().resolve("student.pdf")
        runOnIdle {c.savePdf {paper}};settled(c)
        val bytes=Files.readAllBytes(paper)
        val text=Loader.loadPDF(bytes).use {PDFTextStripper().getText(it)}
        assertFalse(text.contains("SECRET_"));assertFalse(text.contains("LIVE_BANK_REPLACEMENT"));assertContains(text,"Saved MCQ")
        runOnIdle {c.savePdf {paper}};settled(c);assertContains(c.error.orEmpty(),"already exists");assertContentEquals(bytes,Files.readAllBytes(paper))
        onNodeWithTag("pdf-kind").performScrollTo().performClick();onNodeWithTag("pdf-kind-ANSWER_KEY").performClick()
        onNodeWithTag("save-pdf").assertIsNotEnabled();assertNull(c.pdfExport!!.png)
        onNodeWithTag("render-pdf").performClick();settled(c)
        val key=folder.root.toPath().resolve("key.pdf");runOnIdle {c.savePdf {key}};settled(c)
        val answers=Loader.loadPDF(Files.readAllBytes(key)).use {PDFTextStripper().getText(it)}
        assertContains(answers,"SECRET_MODEL_ANSWER");assertContains(answers,"SECRET_EXPLANATION");assertContains(answers,"Correct answer: A)")
        onNodeWithTag("pdf-logo").performScrollTo().performTextInput(folder.root.toPath().resolve("missing.png").toString())
        onNodeWithTag("render-pdf").performClick();settled(c);assertNotNull(c.error);assertNull(c.pdfExport!!.rendered)
        assertEquals(exam.questions(),s.academic.exams().findById(id,exam.id()).questions())
    }

    @Test fun gradeAttendanceExportUsesSavedValuesAndRefusesOverwrite()=runSkikoComposeUiTest(size=Size(1100f,950f)) {
        val (s,id)=setup();val a=s.academic;val students=a.enrollments().findStudentsForOffering(id)
        val category=a.grades().saveCategory(id,null,"Work",BigDecimal("100"))
        val assessment=a.grades().saveAssessment(id,null,category.id(),"Assignment",BigDecimal("10"),LocalDate.now())
        val enrollment=students.first().enrollment().id();val lecture=a.lectures().overview(id).lectures().first().id()
        a.grades().saveGrades(id,mapOf(GradeKey(enrollment,assessment.id()) to "0"))
        a.attendance().save(id,lecture,mapOf(enrollment to AttendanceStatus.LATE))
        lateinit var c:WorkspaceController
        setContent {val scope=rememberCoroutineScope();c=remember {WorkspaceController(s,scope)};LaunchedEffect(Unit){c.select(id)}
            Box(Modifier.fillMaxSize()){AttendanceScreen(c);WorkspaceOverlays(c)}}
        settled(c);val path=folder.root.toPath().resolve("report.xlsx")
        runOnIdle {c.attendance!!.set(enrollment,AttendanceStatus.ABSENT);c.exportGradeWorkbook {path}}
        assertContains(c.error.orEmpty(),"attendance edits");assertFalse(Files.exists(path))
        runOnIdle {c.attendance!!.discard();c.exportGradeWorkbook {path}}
        onNodeWithTag("confirm").performClick();settled(c);assertTrue(Files.exists(path))
        XSSFWorkbook(Files.newInputStream(path)).use {w->
            assertEquals(setOf("Grades","Assessment Breakdown","Statistics","Attendance"),(0 until w.numberOfSheets).map {w.getSheetName(it)}.toSet())
            val rows=w.getSheet("Grades").drop(6);val zero=rows.single {it.getCell(0).stringCellValue==students.first().student().studentNumber()}
            assertEquals(CellType.STRING,zero.getCell(0).cellType);assertTrue(zero.getCell(0).stringCellValue.startsWith("00"));assertEquals(0.0,zero.getCell(3).numericCellValue)
            assertTrue(rows.filter {it!=zero}.all {it.getCell(3)==null||it.getCell(3).cellType==CellType.BLANK})
            assertEquals("LATE",w.getSheet("Attendance").drop(6).single {it.getCell(0).stringCellValue==students.first().student().studentNumber()}.getCell(3).stringCellValue)
            assertTrue(w.all {sheet->sheet.all {row->row.none {it.cellType==CellType.FORMULA}}})
        }
        val before=Files.readAllBytes(path)
        runOnIdle {c.detail=null;c.exportGradeWorkbook {path}};onNodeWithTag("confirm").performClick();settled(c)
        assertContains(c.error.orEmpty(),"already exists");assertContentEquals(before,Files.readAllBytes(path))
    }
    private fun org.apache.poi.ss.usermodel.Sheet.createRowIfMissing(i:Int)=getRow(i)?:createRow(i)
    private fun ComposeUiTest.settled(c:WorkspaceController){waitForIdle();waitUntil(timeoutMillis=20000){!c.busy};waitForIdle()}
    private fun ComposeUiTest.capture(name:String){val file=File(System.getProperty("compose.test.output"),"$name.png");file.parentFile.mkdirs();ImageIO.write(onNodeWithTag("editor-modal").captureToImage().toAwtImage(),"png",file)}
}
