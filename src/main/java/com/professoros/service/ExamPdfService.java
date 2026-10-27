package com.professoros.service;

import com.professoros.model.DomainValidationException;
import com.professoros.pdf.*;
import com.professoros.repository.StorageException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.*;

/** Saved-data loading, PDF preview and non-overwriting file export. No question-bank reads. */
public final class ExamPdfService {
    private final ExamService exams;
    private final CourseOfferingService offerings;
    private final CourseService courses;
    private final ExamPdfRenderer renderer=new ExamPdfRenderer();
    public ExamPdfService(ExamService exams,CourseOfferingService offerings,CourseService courses) {
        this.exams=exams; this.offerings=offerings; this.courses=courses;
    }
    public ExamPrintSource load(long offeringId,long examId) {
        var exam=exams.findById(offeringId,examId);
        var offering=offerings.summary(offeringId);
        var course=courses.findById(offering.offering().courseId());
        return new ExamPrintSource(exam,course.courseCode()+" - "+course.courseName(),course.department(),offering.label());
    }
    public RenderedExamPdf render(ExamPrintSource source,ExamPrintProfile profile,PdfKind kind) {
        try { return renderer.render(source,profile,kind); }
        catch(IOException failure) { throw new StorageException("PDF generation failed. Check the referenced images and try again.",failure); }
    }
    public byte[] preview(RenderedExamPdf pdf,int page) {
        if(page<0 || page>=pdf.pageCount()) { throw new DomainValidationException("Preview page is out of range."); }
        try(var document=Loader.loadPDF(pdf.bytes()); var output=new ByteArrayOutputStream()) {
            ImageIO.write(new PDFRenderer(document).renderImageWithDPI(page,90),"png",output);
            return output.toByteArray();
        } catch(IOException failure) { throw new StorageException("The PDF preview could not be rendered.",failure); }
    }
    public void saveAs(RenderedExamPdf pdf,Path destination,boolean mismatchAcknowledged) {
        if(pdf.marksMismatch() && !mismatchAcknowledged) { throw new DomainValidationException("Acknowledge the mark-total mismatch before exporting."); }
        if(destination==null || destination.toString().isBlank() || destination.toAbsolutePath().normalize().getFileName()==null) {
            throw new DomainValidationException("Choose a filename ending in .pdf, not a drive or folder.");
        }
        Path target=destination.toAbsolutePath().normalize();
        if(!target.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) { throw new DomainValidationException("Choose a filename ending in .pdf."); }
        if(Files.isDirectory(target)) { throw new DomainValidationException("Choose a PDF filename, not a folder."); }
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)) { throw new DomainValidationException("This file already exists. Choose a new filename; existing PDFs are never overwritten."); }
        Path staging=null;
        try {
            // Staging and destination are on the same filesystem; move without REPLACE_EXISTING.
            staging=Files.createTempFile(target.getParent(),".professor-os-",".tmp");
            Files.write(staging,pdf.bytes());
            Files.move(staging,target);
        } catch(FileAlreadyExistsException conflict) {
            throw new DomainValidationException("This file now exists. Choose a new filename; nothing was overwritten.");
        } catch(IOException failure) {
            throw new StorageException("Could not export the PDF. Check the folder and permissions, then choose a new filename.",failure);
        } finally {
            if(staging!=null) {
                try { Files.deleteIfExists(staging); }
                catch(IOException failure) { java.util.logging.Logger.getLogger(ExamPdfService.class.getName()).log(java.util.logging.Level.WARNING,"Could not remove PDF staging file "+staging,failure); }
            }
        }
    }
}
