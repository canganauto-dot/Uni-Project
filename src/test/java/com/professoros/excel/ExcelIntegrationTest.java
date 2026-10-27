package com.professoros.excel;
import com.professoros.model.*;
import com.professoros.service.*;
import com.professoros.support.LectureFixture;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExcelIntegrationTest {
    @TempDir Path directory;
    LectureFixture f;
    @BeforeEach void setup() throws Exception { f=LectureFixture.create(directory.resolve("app.db")); }
    Path workbook(String name,String[][] data) throws Exception {
        Path file=directory.resolve(name);
        try(var w=new XSSFWorkbook()) {
            var s=w.createSheet("Students"); int r=0;
            for(var values:data) { var row=s.createRow(r++); for(int c=0;c<values.length;c++) { row.createCell(c).setCellValue(values[c]); } }
            try(var out=Files.newOutputStream(file)) { w.write(out); }
        } return file;
    }
    StudentImportPreview preview(Path file,Long offering) {
        var sheet=new StudentWorkbookReader().read(file).sheets().getFirst(); int row=StudentColumns.headerRow(sheet);
        return f.academic().imports().preview(sheet,row,StudentColumns.detect(sheet.rows().stream().filter(r->r.number()==row).findFirst().orElseThrow()),offering);
    }
    @Test void arabicHeadersDigitsFullNameAndSectionImportWithoutLosingZeros() throws Exception {
        var file=workbook("arabic.xlsx",new String[][]{{"الرقم الجامعي","اسم الطالب","البريد الإلكتروني","الشعبة"},{"٠٠٠١٢٣","أحمد علي حسن","ahmed@example.edu","A"}});
        var preview=preview(file,f.first().id()); assertEquals("000123",preview.rows().getFirst().input().studentNumber());
        assertEquals("علي حسن",preview.rows().getFirst().input().lastName()); assertEquals(StudentImportRow.Action.CREATE,preview.rows().getFirst().action());
        var result=f.academic().imports().confirm(preview); assertEquals(1,result.created()); assertEquals(1,result.enrolled());
        assertEquals("000123",f.academic().enrollments().findStudentsForOffering(f.first().id()).getFirst().student().studentNumber());
    }
    @Test void globalImportKeepsSectionInNotesAndReimportSkipsWithoutUpdating() throws Exception {
        var file=workbook("global.xlsx",new String[][]{{"Student ID","First Name","Last Name","Section"},{"001","Sara","Hassan","B"}});
        assertEquals(1,f.academic().imports().confirm(preview(file,null)).created());
        var student=f.academic().students().findAll().getFirst(); assertEquals("Imported section: B",student.notes());
        assertEquals(StudentImportRow.Action.SKIP,preview(file,null).rows().getFirst().action());
        assertThrows(DomainValidationException.class,()->f.academic().imports().confirm(preview(file,null)));
        assertEquals(student,f.academic().students().findAll().getFirst());
    }
    @Test void sameExistingStudentEnrolledButConflictingProfileAndSectionAreRejected() throws Exception {
        var s=f.academic().students().create(new StudentInput("001","Sara","Hassan","sara@example.edu","Original"));
        var file=workbook("existing.xlsx",new String[][]{{"ID","Name","Email","Section"},{"001","Sara Hassan","sara@example.edu","A"},{"002","Ahmed Ali","","B"},{"003","A B","invalid","A"}});
        var p=preview(file,f.first().id()); assertEquals(StudentImportRow.Action.ENROLL,p.rows().getFirst().action());
        var result=f.academic().imports().confirm(p); assertEquals(0,result.created()); assertEquals(1,result.enrolled()); assertEquals(2,result.failed());
        assertEquals(s,f.academic().students().findById(s.id()));
        var conflict=workbook("conflict.xlsx",new String[][]{{"ID","Name"},{"001","Different Person"}});
        assertEquals(StudentImportRow.Action.ERROR,preview(conflict,null).rows().getFirst().action());
    }
    @Test void allRepeatedIdsAreFlaggedAndBlankRowsIgnored() throws Exception {
        var file=workbook("duplicates.xlsx",new String[][]{{"ID","Name"},{"001","Sara Hassan"},{"",""},{"001","Sara Hassan"},{"002","Ahmed Ali"}});
        var p=preview(file,null); assertEquals(3,p.rows().size());
        assertEquals(2,p.rows().stream().filter(r->r.action()==StudentImportRow.Action.ERROR).count());
        var result=f.academic().imports().confirm(p); assertEquals(1,result.created()); assertEquals(2,result.failed());
    }
    @Test void formattedNumericIdsPreserveDisplayedZerosAndUnsafeNumbersAreErrors() throws Exception {
        Path file=directory.resolve("numeric.xlsx");
        try(var w=new XSSFWorkbook()) {
            var s=w.createSheet("Roster"); s.createRow(0).createCell(0).setCellValue("Student ID"); s.getRow(0).createCell(1).setCellValue("Name");
            var style=w.createCellStyle(); style.setDataFormat(w.createDataFormat().getFormat("000000"));
            var row=s.createRow(1); row.createCell(0).setCellValue(123); row.getCell(0).setCellStyle(style); row.createCell(1).setCellValue("Sara Hassan");
            var bad=s.createRow(2); bad.createCell(0).setCellValue(1234567890123456d); bad.createCell(1).setCellValue("Ahmed Ali");
            try(var out=Files.newOutputStream(file)) { w.write(out); }
        }
        var p=preview(file,null); assertEquals("000123",p.rows().getFirst().input().studentNumber()); assertTrue(p.rows().getFirst().message().contains("Numeric ID"));
        assertEquals(StudentImportRow.Action.ERROR,p.rows().getLast().action());
    }
    @Test void formulasAndExcelErrorsAreNeverEvaluatedOrImported() throws Exception {
        var file=workbook("formula.xlsx",new String[][]{{"ID","Name"},{"001","Sara Hassan"},{"002","Ahmed Ali"}});
        try(var in=Files.newInputStream(file);var w=new XSSFWorkbook(in)) {
            w.getSheetAt(0).getRow(1).getCell(0).setCellFormula("1+1");
            w.getSheetAt(0).getRow(2).getCell(1).setCellErrorValue(FormulaError.REF.getCode());
            try(var out=Files.newOutputStream(file)) { w.write(out); }
        }
        var p=preview(file,null); assertEquals(2,p.rows().stream().filter(r->r.action()==StudentImportRow.Action.ERROR).count()); assertEquals(0,p.actionable());
    }
    @Test void manualMappingAndTitleRowsWorkAndAmbiguousHeadersRequireMapping() throws Exception {
        var file=workbook("mapping.xlsx",new String[][]{{"University roster"},{"Code","Person","Contact"},{"001","Sara Hassan","sara@example.edu"}});
        var sheet=new StudentWorkbookReader().read(file).sheets().getFirst();
        var map=Map.of(StudentColumns.Field.ID,0,StudentColumns.Field.FULL_NAME,1,StudentColumns.Field.EMAIL,2);
        var p=f.academic().imports().preview(sheet,2,map,null); assertEquals(1,p.actionable());
        assertThrows(DomainValidationException.class,()->f.academic().imports().preview(sheet,2,Map.of(),null));
        var header=new StudentWorkbook.Row(1,List.of(new StudentWorkbook.Value("ID","",false),new StudentWorkbook.Value("Student ID","",false)));
        assertFalse(StudentColumns.detect(header).containsKey(StudentColumns.Field.ID));
    }
    @Test void malformedEmptyAndHeaderOnlyFilesFailClearly() throws Exception {
        Path bad=directory.resolve("bad.xlsx"); Files.writeString(bad,"not an Excel zip");
        assertThrows(com.professoros.repository.StorageException.class,()->new StudentWorkbookReader().read(bad));
        var empty=workbook("empty.xlsx",new String[][]{}); assertThrows(DomainValidationException.class,()->new StudentWorkbookReader().read(empty));
        var header=workbook("header.xlsx",new String[][]{{"ID","Name"}});
        assertThrows(DomainValidationException.class,()->preview(header,null));
        assertThrows(DomainValidationException.class,()->new StudentWorkbookReader().read(directory.resolve("wrong.xls")));
    }
    @Test void changedStudentRollsBackEarlierRowsAndClosedOfferingDoesNotCreateStudents() throws Exception {
        var old=f.academic().students().create(new StudentInput("002","Sara","Hassan","",""));
        var file=workbook("rollback.xlsx",new String[][]{{"ID","Name"},{"001","Ahmed Ali"},{"002","Sara Hassan"}});
        var p=preview(file,f.first().id());
        f.academic().students().update(old.id(),new StudentInput("002","Sara","Changed","",""));
        assertThrows(DomainValidationException.class,()->f.academic().imports().confirm(p));
        assertEquals(1,f.academic().students().findAll().size()); assertTrue(f.academic().enrollments().findStudentsForOffering(f.first().id()).isEmpty());
        var p2=preview(file,f.first().id());
        f.academic().offerings().update(f.first().id(),new CourseOfferingInput(f.course().id(),f.term().id(),"A","",AcademicStatus.COMPLETED));
        assertThrows(DomainValidationException.class,()->f.academic().imports().confirm(p2));
        assertEquals(1,f.academic().students().findAll().size());
    }
    @Test void alreadyEnrolledIsSkippedWithoutReactivatingAndCountsAreAccurate() throws Exception {
        var s=f.academic().students().create(new StudentInput("001","Sara","Hassan","",""));
        var e=f.academic().enrollments().enroll(s.id(),f.first().id()); f.academic().enrollments().updateStatus(e.id(),EnrollmentStatus.WITHDRAWN);
        var file=workbook("skipped.xlsx",new String[][]{{"ID","Name"},{"001","Sara Hassan"},{"002","Ahmed Ali"}});
        var result=f.academic().imports().confirm(preview(file,f.first().id()));
        assertEquals(1,result.created()); assertEquals(1,result.enrolled()); assertEquals(1,result.skipped());
        assertEquals(EnrollmentStatus.WITHDRAWN,f.academic().enrollments().findStudentsForOffering(f.first().id()).stream().filter(r->r.enrollment().id()==e.id()).findFirst().orElseThrow().enrollment().status());
    }
    @Test void gradeExportPreservesCalculationsBlankVsZeroIdsAndTextAndRejectsOverwrite() throws Exception {
        var a=f.academic().students().create(new StudentInput("001","Sara","Hassan","",""));
        var b=f.academic().students().create(new StudentInput("002","Ahmed","Ali","",""));
        var c=f.academic().students().create(new StudentInput("003","=SUM(1,2)","Name","",""));
        long ea=f.academic().enrollments().enroll(a.id(),f.first().id()).id();
        f.academic().enrollments().enroll(b.id(),f.first().id()); f.academic().enrollments().enroll(c.id(),f.first().id());
        var grades=f.academic().grades();
        var category=grades.saveCategory(f.first().id(),null,"Assignments",new BigDecimal("100"));
        var assessment=grades.saveAssessment(f.first().id(),null,category.id(),"Project",new BigDecimal("20"),LocalDate.of(2026,11,1));
        grades.saveGrades(f.first().id(),Map.of(new GradeKey(ea,assessment.id()),"0"));
        var export=new ExcelExportService(grades,f.academic().offerings()); byte[] bytes=export.render(f.first().id());
        Path file=directory.resolve("grades.xlsx"); export.save(bytes,file);
        assertThrows(DomainValidationException.class,()->export.save(bytes,file)); assertArrayEquals(bytes,Files.readAllBytes(file));
        try(var w=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(List.of("Grades","Assessment Breakdown","Statistics"),java.util.stream.IntStream.range(0,w.getNumberOfSheets()).mapToObj(w::getSheetName).toList());
            var sheet=w.getSheet("Grades");
            for(int i=6;i<=sheet.getLastRowNum();i++) {
                var row=sheet.getRow(i); assertEquals(CellType.STRING,row.getCell(0).getCellType());
                assertEquals(row.getCell(0).getStringCellValue(),new DataFormatter(Locale.ROOT).formatCellValue(row.getCell(0)));
                if(row.getCell(0).getStringCellValue().equals("001")) { assertEquals(0,row.getCell(3).getNumericCellValue()); assertEquals("F",row.getCell(6).getStringCellValue()); assertEquals("FAIL",row.getCell(7).getStringCellValue()); }
                else { assertEquals(CellType.BLANK,row.getCell(3).getCellType()); assertEquals(CellType.BLANK,row.getCell(5).getCellType()); assertEquals("INCOMPLETE",row.getCell(7).getStringCellValue()); }
            }
            for(var sh:w) { for(var row:sh) { for(var cell:row) { assertNotEquals(CellType.FORMULA,cell.getCellType()); } } }
            assertFalse(sheet.isDisplayGridlines()); assertNotNull(sheet.getPaneInformation()); assertEquals(42,sheet.getRow(5).getHeightInPoints());
            assertEquals(1,w.getSheet("Statistics").getRow(6).getCell(1).getNumericCellValue());
            assertEquals(2,w.getSheet("Statistics").getRow(7).getCell(1).getNumericCellValue());
            assertEquals(0,w.getSheet("Statistics").getRow(8).getCell(1).getNumericCellValue());
        }
        Path qa=Path.of("target","excel-qa"); Files.createDirectories(qa); Files.write(qa.resolve("gradebook.xlsx"),bytes);
    }
    @Test void emptyExportHasNoInventedMeanOrPassRate() throws Exception {
        var bytes=new ExcelExportService(f.academic().grades(),f.academic().offerings()).render(f.first().id());
        try(var w=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(CellType.BLANK,w.getSheet("Statistics").getRow(8).getCell(1).getCellType());
            assertEquals(CellType.BLANK,w.getSheet("Statistics").getRow(13).getCell(1).getCellType());
        }
    }
    @Test void nonzeroWeightedResultsAndClassStatisticsMatchGradebookExactly() throws Exception {
        var student=f.academic().students().create(new StudentInput("0001","Sara","Hassan","",""));
        long enrollment=f.academic().enrollments().enroll(student.id(),f.first().id()).id(); var grades=f.academic().grades(); long offering=f.first().id();
        var c1=grades.saveCategory(offering,null,"Assignments",new BigDecimal("40")); var c2=grades.saveCategory(offering,null,"Exam",new BigDecimal("60"));
        var a1=grades.saveAssessment(offering,null,c1.id(),"Project",new BigDecimal("20"),LocalDate.of(2026,11,1));
        var a2=grades.saveAssessment(offering,null,c2.id(),"Final",new BigDecimal("80"),LocalDate.of(2026,12,1));
        grades.saveGrades(offering,Map.of(new GradeKey(enrollment,a1.id()),"15",new GradeKey(enrollment,a2.id()),"70"));
        var book=grades.load(offering); var expected=GradeCalculator.calculate(book,enrollment,GradeCalculator.scores(book));
        assertEquals(new BigDecimal("82.50"),expected.weightedPoints());
        try(var w=new XSSFWorkbook(new ByteArrayInputStream(new ExcelExportService(grades,f.academic().offerings()).render(offering)))) {
            var row=w.getSheet("Grades").getRow(6); assertEquals("0001",row.getCell(0).getStringCellValue());
            assertEquals(82.5,row.getCell(5).getNumericCellValue()); assertEquals(.825,row.getCell(6).getNumericCellValue());
            var display = new DataFormatter(Locale.ROOT);
            assertEquals("82.5%", display.formatCellValue(row.getCell(6)));
            assertEquals("100%", display.formatCellValue(w.getSheet("Statistics").getRow(13).getCell(1)));
            assertEquals(expected.letter(),row.getCell(7).getStringCellValue()); assertEquals(expected.outcome(),row.getCell(8).getStringCellValue());
            assertEquals(.825,w.getSheet("Statistics").getRow(8).getCell(1).getNumericCellValue());
            assertEquals(1,w.getSheet("Statistics").getRow(13).getCell(1).getNumericCellValue());
        }
    }
    @Test void realisticBilingualRosterImportsAndExportsWithoutLosingNamesZerosOrMissingGrades() throws Exception {
        List<String[]> data=new ArrayList<>(); data.add(new String[]{"College of Engineering — Fall roster"});
        data.add(new String[]{"الرقم الجامعي","اسم الطالب","البريد الإلكتروني","الشعبة"});
        for(int i=1;i<=36;i++) { data.add(new String[]{String.format(Locale.ROOT,"%06d",i),i%2==0?"سارة عبد الرحمن":"Ahmed Ali Hassan",i%3==0?"":"student"+i+"@example.edu","A"}); }
        data.add(new String[]{"009999","أحمد علي","","A"}); data.add(new String[]{"009999","أحمد علي","","A"});
        data.add(new String[]{"","Missing Identifier","","A"}); data.add(new String[]{"","","",""});
        Path file=workbook("realistic-roster.xlsx",data.toArray(String[][]::new));
        var p=preview(file,f.first().id()); assertEquals(39,p.rows().size());
        var result=f.academic().imports().confirm(p); assertEquals(36,result.created()); assertEquals(36,result.enrolled()); assertEquals(3,result.failed());
        var roster=f.academic().enrollments().findStudentsForOffering(f.first().id());
        var grades=f.academic().grades(); long offering=f.first().id();
        var category=grades.saveCategory(offering,null,"Assignments",new BigDecimal("100"));
        var assessment=grades.saveAssessment(offering,null,category.id(),"مشروع البرمجة",new BigDecimal("20"),LocalDate.of(2026,11,1));
        grades.saveGrades(offering,Map.of(new GradeKey(roster.getFirst().enrollment().id(),assessment.id()),"0"));
        byte[] bytes=new ExcelExportService(grades,f.academic().offerings()).render(offering);
        try(var w=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var s=w.getSheet("Grades"); assertEquals(41,s.getLastRowNum()); int blanks=0,zeros=0,arabic=0;
            for(int i=6;i<=s.getLastRowNum();i++) {
                var row=s.getRow(i); assertTrue(row.getCell(0).getStringCellValue().matches("0000[0-9]{2}"));
                if(row.getCell(1).getStringCellValue().contains("سارة")) { arabic++; }
                if(row.getCell(3).getCellType()==CellType.BLANK) { blanks++; assertEquals(CellType.BLANK,row.getCell(5).getCellType()); }
                else { zeros++; assertEquals(0,row.getCell(3).getNumericCellValue()); }
            }
            assertEquals(35,blanks); assertEquals(1,zeros); assertEquals(18,arabic);
        }
        Path qa=Path.of("target","excel-qa"); Files.createDirectories(qa.resolve("qa-data"));
        Files.copy(file,qa.resolve("realistic-roster.xlsx"),StandardCopyOption.REPLACE_EXISTING);
        Files.write(qa.resolve("realistic-gradebook.xlsx"),bytes);
        Files.copy(directory.resolve("app.db"),qa.resolve("qa-data/professor-os.db"),StandardCopyOption.REPLACE_EXISTING);
    }
    @Test void scaledNumericFormatsCannotSilentlyChangeStudentIds() throws Exception {
        Path file=directory.resolve("scaled.xlsx");
        try(var w=new XSSFWorkbook()) {
            var s=w.createSheet("Roster"); var h=s.createRow(0); h.createCell(0).setCellValue("ID"); h.createCell(1).setCellValue("Name");
            var row=s.createRow(1); row.createCell(0).setCellValue(123000); row.createCell(1).setCellValue("أحمد علي");
            var style=w.createCellStyle(); style.setDataFormat(w.createDataFormat().getFormat("0,")); row.getCell(0).setCellStyle(style);
            try(var out=Files.newOutputStream(file)) { w.write(out); }
        }
        var p=preview(file,null);
        assertEquals(StudentImportRow.Action.ERROR,p.rows().getFirst().action(),"123000 must not silently become ID 123");
        assertTrue(p.rows().getFirst().message().contains("Text"));
    }
    @Test void rootPathsProduceActionableValidationInsteadOfNullPointer() {
        Path root=directory.toAbsolutePath().getRoot();
        assertThrows(DomainValidationException.class,()->new StudentWorkbookReader().read(root));
        assertThrows(DomainValidationException.class,()->new ExcelExportService(f.academic().grades(),f.academic().offerings()).save(new byte[0],root));
    }
    @Test void missingWorkbookAndFolderNamedXlsxExplainHowToRecover() throws Exception {
        var reader=new StudentWorkbookReader();
        var missing=assertThrows(DomainValidationException.class,()->reader.read(directory.resolve("missing.xlsx")));
        assertTrue(missing.getMessage().contains("Browse"));
        Path folder=Files.createDirectory(directory.resolve("folder.xlsx"));
        assertTrue(assertThrows(DomainValidationException.class,()->reader.read(folder)).getMessage().contains("folder"));
    }
    @Test void oversizedAndMacroDisguisedFilesAreRejectedBeforeParsing() throws Exception {
        Path big=directory.resolve("large.xlsx");
        try(var file=new java.io.RandomAccessFile(big.toFile(),"rw")) { file.setLength(21L*1024*1024); }
        assertThrows(DomainValidationException.class,()->new StudentWorkbookReader().read(big));
        Path macro=directory.resolve("macro.xlsx");
        try(var zip=new java.util.zip.ZipOutputStream(Files.newOutputStream(macro))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("xl/vbaProject.bin")); zip.write(new byte[]{1,2}); zip.closeEntry();
        }
        assertThrows(DomainValidationException.class,()->new StudentWorkbookReader().read(macro));
    }
}
