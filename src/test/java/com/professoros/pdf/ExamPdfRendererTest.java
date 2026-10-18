package com.professoros.pdf;

import com.professoros.model.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.List;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class ExamPdfRendererTest {
    @TempDir Path directory;
    private static ExamPrintProfile profile() { return new ExamPrintProfile("Example University","Computer Science","Dr. Example",""); }
    private static ExamQuestion question(int number,QuestionType type,String text,String image) {
        return new ExamQuestion(number,number,type,text,"Topic",Difficulty.MEDIUM,new BigDecimal("2.50"),
                type==QuestionType.TRUE_FALSE?"FALSE":"SECRET_MODEL_"+number,"SECRET_EXPLANATION_"+number,image,
                type==QuestionType.MCQ?List.of(new AnswerChoice("First choice",false),new AnswerChoice("Second choice",true)):List.of());
    }
    private static ExamPrintSource source(List<ExamQuestion> questions) {
        return new ExamPrintSource(new Exam(1,1,"Software Engineering Midterm",LocalDate.of(2026,11,15),90,new BigDecimal("12.50"),
                "Answer all questions. Show your reasoning.",ExamStatus.FINALIZED,Instant.EPOCH,Instant.EPOCH,questions),
                "CS201 - Object Oriented Programming","Computer Science","Fall 2026 - Section A");
    }
    private List<ExamQuestion> fiveTypes(String image) {
        return List.of(question(1,QuestionType.MCQ,"Choose the correct design principle.",image),
                question(2,QuestionType.TRUE_FALSE,"An interface always contains only one method.",""),
                question(3,QuestionType.SHORT_ANSWER,"Define encapsulation.",""),
                question(4,QuestionType.ESSAY,"Compare composition and inheritance. Explain a suitable use case for each.",""),
                question(5,QuestionType.CODING,"Complete this method:\npublic int sum(int a, int b) {\n    // return the sum\n}",""));
    }
    @Test void studentContainsAllPromptsButNoAnswerFieldsOrCorrectnessMarkers() throws Exception {
        var renderer=new ExamPdfRenderer(); var source=source(fiveTypes(""));
        var student=renderer.render(source,profile(),PdfKind.STUDENT);
        var key=renderer.render(source,profile(),PdfKind.ANSWER_KEY);
        String studentText=text(student),keyText=text(key);
        for(int i=1;i<=5;i++) {
            assertTrue(studentText.contains("Question "+i)); assertTrue(keyText.contains("Question "+i));
            assertFalse(studentText.contains("SECRET_")); assertTrue(keyText.contains("SECRET_EXPLANATION_"+i));
        }
        assertTrue(keyText.contains("Correct answer: B) Second choice"));
        assertFalse(studentText.contains("Correct answer")); assertFalse(studentText.contains("Explanation"));
        assertTrue(studentText.contains("Student name")); assertTrue(studentText.contains("Student ID"));
        assertTrue(studentText.contains("Total marks: 12.50")); assertTrue(studentText.contains("public int sum"));
        assertTrue(studentText.contains("First choice")); assertTrue(studentText.contains("Second choice"));
        try(var doc=Loader.loadPDF(student.bytes())) {
            assertNull(doc.getDocumentCatalog().getMetadata()); assertNull(doc.getDocumentCatalog().getNames());
            assertFalse(doc.getDocumentInformation().getTitle().contains("SECRET"));
            for(PDPage page:doc.getPages()) { assertTrue(page.getAnnotations().isEmpty()); }
        }
        writeQa(student,"student-exam"); writeQa(key,"answer-key");
    }
    @Test void paginationLongTextAndUnbrokenTokensStayWithinA4Margins() throws Exception {
        var questions=new ArrayList<ExamQuestion>();
        for(int i=1;i<=12;i++) { questions.add(question(i,QuestionType.ESSAY,("Detailed prompt with words to wrap correctly. ").repeat(30)+"END_"+i,"")); }
        questions.add(question(13,QuestionType.CODING,"x".repeat(280)+"\n    return 42;",""));
        var pdf=new ExamPdfRenderer().render(source(questions),profile(),PdfKind.STUDENT);
        assertTrue(pdf.pageCount()>5);
        try(var doc=Loader.loadPDF(pdf.bytes())) {
            String text=new PDFTextStripper().getText(doc);
            for(int i=1;i<=12;i++) { assertTrue(text.contains("END_"+i)); }
            for(int i=1;i<=doc.getNumberOfPages();i++) { assertTrue(text.contains("Page "+i+" of "+doc.getNumberOfPages())); }
            for(PDPage page:doc.getPages()) {
                assertEquals(PDRectangle.A4.getWidth(),page.getMediaBox().getWidth());
                assertEquals(PDRectangle.A4.getHeight(),page.getMediaBox().getHeight());
            }
            new PDFTextStripper() {
                @Override protected void processTextPosition(TextPosition p) {
                    assertTrue(p.getXDirAdj()>=47,"left margin");
                    assertTrue(p.getXDirAdj()+p.getWidthDirAdj()<=PDRectangle.A4.getWidth()-46,"right margin");
                    assertTrue(p.getYDirAdj()<PDRectangle.A4.getHeight()-20,"bottom margin");
                    super.processTextPosition(p);
                }
            }.getText(doc);
        }
    }
    @Test void embedsLogoAndQuestionImagesAndFailsClearlyForMissingImages() throws Exception {
        Path image=directory.resolve("diagram.png");
        BufferedImage pixels=new BufferedImage(180,80,BufferedImage.TYPE_INT_RGB);
        var g=pixels.createGraphics(); g.setColor(Color.WHITE); g.fillRect(0,0,180,80); g.setColor(Color.BLUE); g.drawRect(15,15,150,50); g.dispose();
        ImageIO.write(pixels,"png",image.toFile());
        var pdf=new ExamPdfRenderer().render(source(fiveTypes(image.toString())),
                new ExamPrintProfile("Example University","Computing","Dr. Example",image.toString()),PdfKind.STUDENT);
        try(var doc=Loader.loadPDF(pdf.bytes())) {
            int images=0;
            for(PDPage page:doc.getPages()) for(var name:page.getResources().getXObjectNames()) { if(page.getResources().isImageXObject(name)) { images++; } }
            assertEquals(2,images);
        }
        Files.delete(image);
        var failure=assertThrows(DomainValidationException.class,() -> new ExamPdfRenderer().render(source(fiveTypes(image.toString())),profile(),PdfKind.STUDENT));
        assertTrue(failure.getMessage().contains("Question 1 image"));
        Path invalid=directory.resolve("not-image.png"); Files.writeString(invalid,"not an image");
        assertThrows(DomainValidationException.class,() -> new ExamPdfRenderer().render(source(fiveTypes(invalid.toString())),profile(),PdfKind.STUDENT));
    }
    @Test void arabicMixedTextUsesEmbeddedFontsAndRendersWithoutSubstitution() throws Exception {
        var source=source(List.of(question(1,QuestionType.SHORT_ANSWER,"اشرح مفهوم الوراثة في Java مع مثال بسيط.","")));
        var pdf=new ExamPdfRenderer().render(source,new ExamPrintProfile("جامعة بغداد","قسم علوم الحاسوب","الدكتور أحمد علي",""),PdfKind.STUDENT);
        try(var doc=Loader.loadPDF(pdf.bytes())) {
            boolean arabic=false;
            for(PDPage page:doc.getPages()) for(var name:page.getResources().getFontNames()) {
                var font=page.getResources().getFont(name); assertTrue(font.isEmbedded());
                if(font.getName().contains("Naskh")) { arabic=true; }
            }
            assertTrue(arabic);
            var fonts=new PdfFonts(doc);
            var visual=fonts.visual("اشرح Java 2026",false);
            assertTrue(visual.rtl()); assertTrue(visual.visual().contains("Java")); assertTrue(visual.visual().contains("2026"));
        }
        writeQa(pdf,"arabic-exam");
    }
    @Test void rejectsUnsupportedCharactersAndEmptyExamsRatherThanSilentlyDroppingContent() {
        assertThrows(DomainValidationException.class,() -> new ExamPdfRenderer().render(source(List.of()),profile(),PdfKind.STUDENT));
        assertThrows(DomainValidationException.class,() -> new ExamPdfRenderer().render(source(List.of(question(1,QuestionType.ESSAY,"Unsupported 🦄",""))),profile(),PdfKind.STUDENT));
    }
    @Test void mismatchIsPreservedAndWarnedInBothOutputs() throws Exception {
        var source=source(List.of(question(1,QuestionType.ESSAY,"Prompt","")));
        for(PdfKind kind:PdfKind.values()) {
            var pdf=new ExamPdfRenderer().render(source,profile(),kind);
            assertTrue(pdf.marksMismatch()); assertFalse(pdf.warnings().isEmpty());
            assertTrue(text(pdf).contains("Total marks: 2.50")); assertTrue(text(pdf).contains("Intended: 12.50"));
        }
    }
    private static String text(RenderedExamPdf pdf) throws Exception { try(var doc=Loader.loadPDF(pdf.bytes())) { return new PDFTextStripper().getText(doc); } }
    private static void writeQa(RenderedExamPdf pdf,String name) throws Exception {
        Path output=Path.of("target","pdf-qa"); Files.createDirectories(output); Files.write(output.resolve(name+".pdf"),pdf.bytes());
        try(var doc=Loader.loadPDF(pdf.bytes())) {
            var renderer=new PDFRenderer(doc);
            for(int page=0;page<doc.getNumberOfPages();page++) {
                ImageIO.write(renderer.renderImageWithDPI(page,110),"png",output.resolve(name+"-"+(page+1)+".png").toFile());
            }
        }
    }
}
