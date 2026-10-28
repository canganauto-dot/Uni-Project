import com.professoros.app.AppPaths;
import com.professoros.database.Database;
import com.professoros.model.*;
import com.professoros.repository.CourseRepository;
import com.professoros.service.*;
import com.professoros.pdf.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.nio.file.*;
import java.math.*;
import java.util.*;

/** Developer verification against an isolated COPY using the installed runtime and JARs. */
class PackagedSmoke {
 public static void main(String[] args) throws Exception {
  Path directory=Path.of(args[0]).toAbsolutePath().normalize();
  if(!directory.getFileName().toString().startsWith("package-qa-"))throw new IllegalArgumentException("Isolated package-qa-* directory required");
  Path path=directory.resolve("professor-os.db");
  if(!AppPaths.databaseFile().equals(path))throw new IllegalStateException("Storage override mismatch: "+AppPaths.databaseFile());
  var db=new Database(path);db.initialize();var a=AcademicServices.create(db);
  Path output=directory.resolve("exports");Files.createDirectories(output);Path expected=directory.resolve("expected-score.txt");
  var book=a.grades().load(1);var key=new GradeKey(book.students().getFirst().enrollment().id(),book.assessments().getFirst().id());
  if(args[1].equals("write")) {
   var maximum=book.assessments().getFirst().maximumMarks();var score=maximum.divide(BigDecimal.valueOf(2),2,RoundingMode.HALF_UP);
   a.grades().saveGrades(1,Map.of(key,score.toPlainString()));Files.writeString(expected,score.toPlainString());
   var excel=new ExcelExportService(a.grades(),a.offerings(),a.attendance());Path xlsx=output.resolve("gradebook.xlsx");excel.save(excel.render(1),xlsx);
   try(var workbook=new XSSFWorkbook(xlsx.toFile())){if(workbook.getNumberOfSheets()!=4)throw new IllegalStateException("Missing report sheets");}
   var pdf=new ExamPdfService(a.exams(),a.offerings(),new CourseService(new CourseRepository(db)));
   var source=pdf.load(1,a.exams().findByOffering(1).getFirst().id());
   for(var kind:PdfKind.values()) {
    var rendered=pdf.render(source,new ExamPrintProfile("University of Baghdad","Computer Science","Package QA",null),kind);
    Path file=output.resolve(kind.name()+".pdf");pdf.saveAs(rendered,file,true);
    try(var document=Loader.loadPDF(file.toFile())){if(document.getNumberOfPages()<1||new PDFTextStripper().getText(document).isBlank())throw new IllegalStateException("Empty PDF");}
    if(pdf.preview(rendered,0).length<100)throw new IllegalStateException("No rendered PDF preview");
   }
  }
  var saved=a.grades().load(1).grades().stream().filter(g->g.key().equals(key)).findFirst().orElseThrow().score();
  if(saved.compareTo(new BigDecimal(Files.readString(expected)))!=0)throw new IllegalStateException("Persistence failure");
  try(var c=db.openConnection();var s=c.createStatement()) {
   try(var r=s.executeQuery("PRAGMA integrity_check")){if(!r.next()||!r.getString(1).equals("ok"))throw new IllegalStateException("Integrity failure");}
   try(var r=s.executeQuery("PRAGMA foreign_key_check")){if(r.next())throw new IllegalStateException("Foreign key failure");}
  }
  System.out.println("Packaged runtime "+System.getProperty("java.home")+": "+args[1]+" PASS; PDF, Excel, persistence, integrity verified");
 }
}
