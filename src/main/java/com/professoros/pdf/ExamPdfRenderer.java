package com.professoros.pdf;

import com.professoros.model.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import static com.professoros.pdf.PdfFonts.Style.*;

/** Pure document rendering from one saved snapshot. No JavaFX, JDBC or bank lookups. */
public final class ExamPdfRenderer {
    public RenderedExamPdf render(ExamPrintSource source,ExamPrintProfile profile,PdfKind kind) throws IOException {
        Objects.requireNonNull(source); Objects.requireNonNull(kind);
        profile=validate(profile);
        Exam exam=source.exam();
        if(exam.questions().isEmpty()) { throw new DomainValidationException("Add and save at least one question before exporting."); }
        List<String> warnings=new ArrayList<>();
        if(exam.marksDiffer()) { warnings.add("Calculated marks "+exam.calculatedMarks()+" differ from intended marks "+exam.intendedMarks()+". The PDF displays both; no marks were changed."); }
        if(exam.status()==ExamStatus.DRAFT) { warnings.add("This is a saved draft. The PDF is labelled DRAFT."); }
        try(PDDocument document=new PDDocument(); ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
            document.getDocumentInformation().setTitle(exam.title()+" - "+kind);
            document.getDocumentInformation().setCreator("Professor OS");
            document.getDocumentInformation().setSubject(kind.toString());
            PdfFonts fonts=new PdfFonts(document);
            try(Layout layout=new Layout(document,fonts,kind)) {
                if(!profile.logoPath().isBlank()) { layout.image(PdfImages.read(profile.logoPath(),"University logo"),64,64); }
                layout.paragraph(profile.university(),HEADING,18);
                layout.paragraph(profile.department(),BODY,11);
                layout.space(8); layout.rule();
                layout.paragraph(exam.title(),HEADING,16);
                layout.paragraph(kind+(exam.status()==ExamStatus.DRAFT?" - DRAFT":""),HEADING,11);
                layout.paragraph("Course: "+source.courseLabel(),BODY,11);
                layout.paragraph("Professor: "+profile.professor(),BODY,11);
                layout.paragraph("Offering: "+source.offeringLabel(),BODY,10);
                layout.paragraph("Date: "+exam.date()+"    Duration: "+exam.durationMinutes()+" minutes",BODY,11);
                layout.paragraph("Total marks: "+exam.calculatedMarks()+(exam.marksDiffer()?"    Intended: "+exam.intendedMarks()+" (mismatch)":""),
                        HEADING,11);
                if(kind==PdfKind.STUDENT) {
                    layout.space(10);
                    layout.paragraph("Student name: __________________________________________",BODY,11);
                    layout.paragraph("Student ID: _____________________________________________",BODY,11);
                }
                if(!exam.instructions().isBlank()) {
                    layout.space(8); layout.paragraph("Instructions",HEADING,11); layout.paragraph(exam.instructions(),BODY,11);
                }
                layout.space(12); layout.rule();
                int number=0;
                for(ExamQuestion question:exam.questions()) {
                    layout.ensure(65);
                    layout.paragraph("Question "+(++number)+"  |  "+question.type()+"  |  "+question.marks()+" marks",HEADING,12);
                    layout.paragraph(question.text(),question.type()==QuestionType.CODING?CODE:BODY,question.type()==QuestionType.CODING?10:11);
                    if(!question.imagePath().isBlank()) {
                        layout.image(PdfImages.read(question.imagePath(),"Question "+number+" image"),Layout.WIDTH,220);
                    }
                    if(question.type()==QuestionType.MCQ) {
                        int index=0;
                        for(AnswerChoice choice:question.choices()) { layout.paragraph((char)('A'+index++)+") "+choice.text(),BODY,11); }
                    } else if(question.type()==QuestionType.TRUE_FALSE && kind==PdfKind.STUDENT) {
                        layout.paragraph("(  ) True          (  ) False",BODY,11);
                    }
                    if(kind==PdfKind.ANSWER_KEY) { answer(layout,question); }
                    else if(question.type()==QuestionType.SHORT_ANSWER) { layout.answerLines(3); }
                    else if(question.type()==QuestionType.ESSAY || question.type()==QuestionType.CODING) { layout.answerLines(6); }
                    layout.space(12);
                }
            }
            int pages=document.getNumberOfPages();
            for(int i=0;i<pages;i++) {
                try(var stream=new PDPageContentStream(document,document.getPage(i),PDPageContentStream.AppendMode.APPEND,true,true)) {
                    Layout.text(stream,fonts,kind+"  |  Page "+(i+1)+" of "+pages,Layout.MARGIN,28,BODY,9);
                }
            }
            document.save(bytes);
            return new RenderedExamPdf(bytes.toByteArray(),kind,pages,warnings,exam.marksDiffer());
        }
    }
    private static void answer(Layout layout,ExamQuestion q) throws IOException {
        layout.space(6);
        if(q.type()==QuestionType.MCQ) {
            for(int i=0;i<q.choices().size();i++) {
                if(q.choices().get(i).correct()) { layout.paragraph("Correct answer: "+(char)('A'+i)+") "+q.choices().get(i).text(),HEADING,11); }
            }
        } else {
            layout.paragraph("Answer",HEADING,11);
            layout.paragraph(q.answer().isBlank()?"No model answer provided.":q.answer(),q.type()==QuestionType.CODING?CODE:BODY,11);
        }
        if(!q.explanation().isBlank()) { layout.paragraph("Explanation",HEADING,10); layout.paragraph(q.explanation(),BODY,11); }
    }
    private static ExamPrintProfile validate(ExamPrintProfile profile) {
        if(profile==null) { throw new DomainValidationException("Print details are required."); }
        return new ExamPrintProfile(required(profile.university(),"University name"),required(profile.department(),"Department"),
                required(profile.professor(),"Professor name"),profile.logoPath()==null?"":profile.logoPath().strip());
    }
    private static String required(String value,String label) {
        if(value==null || value.isBlank() || value.codePointCount(0,value.length())>200 || value.indexOf('\0')>=0) {
            throw new DomainValidationException(label+" is required and must contain at most 200 characters.");
        }
        return value.strip();
    }
    private static final class Layout implements AutoCloseable {
        static final float MARGIN=48,WIDTH=PDRectangle.A4.getWidth()-2*MARGIN,BOTTOM=55;
        final PDDocument document; final PdfFonts fonts; final PdfKind kind;
        PDPageContentStream stream; float y;
        Layout(PDDocument document,PdfFonts fonts,PdfKind kind) throws IOException { this.document=document; this.fonts=fonts; this.kind=kind; page(); }
        void page() throws IOException {
            if(stream!=null) { stream.close(); }
            if(document.getNumberOfPages()>=500) { throw new DomainValidationException("PDF exceeds the 500-page export limit."); }
            PDPage page=new PDPage(PDRectangle.A4); document.addPage(page); stream=new PDPageContentStream(document,page);
            y=PDRectangle.A4.getHeight()-MARGIN;
            if(document.getNumberOfPages()>1) { paragraph(kind+" - continued",HEADING,10); rule(); }
        }
        void ensure(float height) throws IOException { if(y-height<BOTTOM) { page(); } }
        void space(float height) { y=Math.max(BOTTOM,y-height); }
        void rule() throws IOException { ensure(12); stream.setStrokingColor(new Color(185,193,205)); stream.setLineWidth(0.6f); stream.moveTo(MARGIN,y); stream.lineTo(MARGIN+WIDTH,y); stream.stroke(); y-=12; }
        void paragraph(String value,PdfFonts.Style style,float size) throws IOException {
            float leading=size*1.65f;
            if(style==HEADING) { ensure(leading+20); }
            for(var line:fonts.wrap(value,style,size,WIDTH-(style==CODE?16:0))) {
                ensure(leading);
                if(style==CODE) {
                    stream.setNonStrokingColor(new Color(243,245,248)); stream.addRect(MARGIN,y-leading+3,WIDTH,leading); stream.fill();
                }
                float x=line.rtl()?MARGIN+WIDTH-fonts.width(line.visual(),style,size):MARGIN+(style==CODE?8:0);
                text(stream,fonts,line.visual(),x,y-size,style,size); y-=leading;
            }
        }
        static void text(PDPageContentStream stream,PdfFonts fonts,String value,float x,float baseline,PdfFonts.Style style,float size) throws IOException {
            stream.setNonStrokingColor(new Color(30,40,55)); stream.beginText(); stream.newLineAtOffset(x,baseline);
            // Switch fonts within a line so Latin runs and shaped Arabic are embedded correctly.
            for(int cp:value.codePoints().toArray()) {
                stream.setFont(fonts.font(cp,style),size); stream.showText(new String(Character.toChars(cp)));
            }
            stream.endText();
        }
        void image(BufferedImage image,float maxWidth,float maxHeight) throws IOException {
            float scale=Math.min(maxWidth/image.getWidth(),maxHeight/image.getHeight());
            float width=image.getWidth()*scale,height=image.getHeight()*scale;
            ensure(height+12);
            stream.drawImage(LosslessFactory.createFromImage(document,image),MARGIN,y-height,width,height);
            y-=height+12;
        }
        void answerLines(int count) throws IOException {
            for(int i=0;i<count;i++) { ensure(22); y-=20; stream.setStrokingColor(new Color(210,215,222)); stream.setLineWidth(0.4f); stream.moveTo(MARGIN,y); stream.lineTo(MARGIN+WIDTH,y); stream.stroke(); }
        }
        @Override public void close() throws IOException { if(stream!=null) { stream.close(); stream=null; } }
    }
}
