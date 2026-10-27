package com.professoros.service;

import com.professoros.model.*;
import com.professoros.pdf.*;
import com.professoros.repository.*;
import com.professoros.support.LectureFixture;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ExamPdfServiceTest {
    @TempDir Path directory;
    @Test void usesSavedSnapshotsAndExportsTwoDistinctFilesWithoutOverwriting() throws Exception {
        var f=LectureFixture.create(directory.resolve("export.db"));
        var bank=f.academic().questions();
        var original=new QuestionInput(f.course().id(),QuestionType.ESSAY,"SAVED_PROMPT","",Difficulty.EASY,BigDecimal.TEN,"SAVED_SECRET","SAVED_EXPLANATION","",List.of());
        var question=bank.create(original);
        var exam=f.academic().exams().save(f.first().id(),null,new ExamInput("Midterm",LocalDate.of(2026,11,1),60,BigDecimal.TEN,""),List.of(question.id()));
        bank.update(question.id(),new QuestionInput(f.course().id(),QuestionType.ESSAY,"LIVE_CHANGED","",Difficulty.EASY,BigDecimal.ONE,"LIVE_SECRET","","",List.of()));
        var service=new ExamPdfService(f.academic().exams(),f.academic().offerings(),new CourseService(new CourseRepository(f.database())));
        var source=service.load(f.first().id(),exam.id());
        var profile=new ExamPrintProfile("University","Department","Professor","");
        var student=service.render(source,profile,PdfKind.STUDENT); var key=service.render(source,profile,PdfKind.ANSWER_KEY);
        try(var doc=Loader.loadPDF(student.bytes())) {
            String text=new PDFTextStripper().getText(doc);
            assertTrue(text.contains("SAVED_PROMPT")); assertFalse(text.contains("LIVE_CHANGED")); assertFalse(text.contains("SECRET"));
        }
        try(var doc=Loader.loadPDF(key.bytes())) {
            String text=new PDFTextStripper().getText(doc); assertTrue(text.contains("SAVED_SECRET")); assertFalse(text.contains("LIVE_SECRET"));
        }
        Path studentPath=directory.resolve("student.pdf"),keyPath=directory.resolve("key.pdf");
        service.saveAs(student,studentPath,false); service.saveAs(key,keyPath,false);
        byte[] before=Files.readAllBytes(studentPath);
        assertThrows(DomainValidationException.class,() -> service.saveAs(key,studentPath,false));
        assertArrayEquals(before,Files.readAllBytes(studentPath));
        assertNotEquals(Files.size(studentPath),0);
        assertTrue(service.preview(student,0).length>100);
        assertThrows(DomainValidationException.class,() -> service.preview(student,student.pageCount()));
        assertThrows(DomainValidationException.class,() -> service.saveAs(student,directory.resolve("bad.txt"),true));
        assertThrows(DomainValidationException.class,() -> service.load(f.second().id(),exam.id()));
    }
    @Test void requiresMismatchAcknowledgmentAndDoesNotLeaveOutputOnValidationFailure() {
        var pdf=new RenderedExamPdf(new byte[]{1,2,3},PdfKind.STUDENT,1,List.of("Mismatch"),true);
        var service=new ExamPdfService(null,null,null);
        Path output=directory.resolve("exam.pdf");
        assertThrows(DomainValidationException.class,() -> service.saveAs(pdf,output,false)); assertFalse(Files.exists(output));
    }
    @Test void rejectsNullRootEmptyAndNonPdfDestinationsWithoutWriting() throws Exception {
        var pdf=new RenderedExamPdf(new byte[]{1,2,3},PdfKind.STUDENT,1,List.of(),false);
        var service=new ExamPdfService(null,null,null);
        for(Path destination:java.util.Arrays.asList(null,directory.toAbsolutePath().getRoot(),Path.of(""),directory.resolve("bad.txt"))) {
            var failure=assertThrows(DomainValidationException.class,()->service.saveAs(pdf,destination,false));
            assertTrue(failure.getMessage().contains("filename"));
        }
        try(var files=Files.list(directory)) { assertEquals(0,files.count()); }
    }
    @Test void directoryAndMissingParentPathsFailWithoutStagingArtifacts() throws Exception {
        var pdf=new RenderedExamPdf(new byte[]{1,2,3},PdfKind.STUDENT,1,List.of(),false);
        var service=new ExamPdfService(null,null,null);
        Path folder=Files.createDirectory(directory.resolve("folder.pdf"));
        assertThrows(DomainValidationException.class,()->service.saveAs(pdf,folder,false));
        var failure=assertThrows(StorageException.class,()->service.saveAs(pdf,directory.resolve("missing/report.pdf"),false));
        assertTrue(failure.getMessage().contains("folder"));
        try(var files=Files.list(directory)) { assertEquals(List.of(folder),files.toList()); }
    }
}
