package com.professoros.service;
import com.professoros.excel.GradeWorkbookWriter;
import com.professoros.model.DomainValidationException;
import com.professoros.repository.StorageException;
import java.io.IOException;
import java.nio.file.*;
public final class ExcelExportService {
    private final GradebookService grades;
    private final CourseOfferingService offerings;
    public ExcelExportService(GradebookService grades,CourseOfferingService offerings) { this.grades=grades; this.offerings=offerings; }
    public byte[] render(long offering) {
        var book=grades.load(offering); var label=offerings.summary(offering).label();
        try { return new GradeWorkbookWriter().write(book,label); }
        catch(IOException failure) { throw new StorageException("Could not generate the Excel grade report.",failure); }
    }
    public void save(byte[] bytes,Path destination) {
        if(destination==null || destination.toAbsolutePath().normalize().getFileName()==null) { throw new DomainValidationException("Choose a filename ending in .xlsx, not a drive or folder."); }
        Path target=destination.toAbsolutePath().normalize(),staging=null;
        if(!target.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")) { throw new DomainValidationException("Choose a filename ending in .xlsx."); }
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)) { throw new DomainValidationException("File already exists. Choose a new filename; nothing will be overwritten."); }
        try { staging=Files.createTempFile(target.getParent(),".professor-os-excel-",".tmp"); Files.write(staging,bytes); Files.move(staging,target); }
        catch(FileAlreadyExistsException failure) { throw new DomainValidationException("File now exists. Choose a new filename."); }
        catch(IOException failure) { throw new StorageException("Cannot save Excel report. Check the destination folder and permissions.",failure); }
        finally { if(staging!=null) { try { Files.deleteIfExists(staging); } catch(IOException failure) { java.util.logging.Logger.getLogger(getClass().getName()).log(java.util.logging.Level.WARNING,"Cannot remove Excel staging file",failure); } } }
    }
}
