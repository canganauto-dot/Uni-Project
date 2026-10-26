package com.professoros.demo;

import com.professoros.database.Database;
import com.professoros.model.*;
import com.professoros.service.*;
import com.professoros.excel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DemoDataSeederTest {
    @TempDir Path directory;
    @Test void refusesLiveDatabaseNames() throws Exception {
        Path live=directory.resolve("professor-os.db");new Database(live).initialize();
        byte[] before=Files.readAllBytes(live);
        assertThrows(IllegalArgumentException.class,()->DemoDataSeeder.seed(live,LocalDate.of(2026,10,26)));
        assertArrayEquals(before,Files.readAllBytes(live));
    }
    @Test void interconnectedDataSurvivesReopenWithCorrectGradesAttendanceAndSnapshots() throws Exception {
        Path path=directory.resolve(".demo-stage-test.db");var db=new Database(path);db.initialize();
        String history=history(db);
        DemoDataSeeder.seed(path,LocalDate.of(2026,10,26));
        new Database(path).initialize();assertEquals(history,history(db));
        var a=AcademicServices.create(new Database(path));
        assertEquals(180,a.students().findAll().size());assertEquals(12,a.offerings().findAll().size());assertEquals(120,a.questions().findAll().size());
        assertTrue(a.students().findAll().stream().anyMatch(s->s.firstName().contains("أحمد")));
        assertTrue(a.students().findAll().stream().allMatch(s->s.studentNumber().startsWith("00")));
        var outcomes=new HashSet<String>();var attendanceStatuses=new HashSet<AttendanceStatus>();boolean zero=false,unmarked=false;
        for(var offering:a.offerings().findAll()) {
            var book=a.grades().load(offering.id());assertEquals(45,book.students().size());
            assertEquals(0,GradeCalculator.weightTotal(book).compareTo(java.math.BigDecimal.valueOf(100)));
            var scores=GradeCalculator.scores(book);
            zero|=scores.values().stream().anyMatch(s->s.signum()==0);
            for(var student:book.students())outcomes.add(GradeCalculator.calculate(book,student.enrollment().id(),scores).outcome());
            var attendance=a.attendance().load(offering.id());
            attendance.records().forEach(r->attendanceStatuses.add(r.status()));
            var summary=AttendanceCalculator.course(attendance);unmarked|=summary.unmarked()>0;
            if(summary.percentage()!=null)assertTrue(summary.percentage().signum()>=0&&summary.percentage().intValue()<=100);
            for(var exam:a.exams().findByOffering(offering.id())) {
                assertFalse(exam.marksDiffer());assertFalse(exam.questions().isEmpty());
                for(var q:exam.questions())assertEquals(offering.courseId(),a.questions().findById(q.questionId()).courseId());
            }
        }
        assertEquals(Set.of("PASS","FAIL","INCOMPLETE"),outcomes);assertTrue(zero);assertTrue(unmarked);
        assertEquals(Set.of(AttendanceStatus.values()),attendanceStatuses);
        Path samples=directory.resolve("review-samples");DemoImportSamples.write(samples,2026);
        for(String name:List.of("students-english.xlsx","students-arabic.xlsx")) {
            var sheet=new StudentWorkbookReader().read(samples.resolve(name)).sheets().getFirst();
            var preview=a.imports().preview(sheet,1,StudentColumns.detect(sheet.rows().getFirst()),null);
            assertEquals(24,preview.actionable());
            assertTrue(preview.rows().stream().allMatch(r->r.action()==StudentImportRow.Action.CREATE));
        }
        var edge=new StudentWorkbookReader().read(samples.resolve("students-validation-cases.xlsx")).sheets().getFirst();
        var preview=a.imports().preview(edge,1,StudentColumns.detect(edge.rows().getFirst()),null);
        assertTrue(preview.rows().stream().anyMatch(r->r.action()==StudentImportRow.Action.SKIP));
        assertTrue(preview.rows().stream().filter(r->r.action()==StudentImportRow.Action.ERROR).count()>=5);
        try(var c=db.openConnection();var s=c.createStatement();var r=s.executeQuery("PRAGMA foreign_key_check")){assertFalse(r.next());}
        // A repeat is a replacement on the same stage, never an append or duplicate migration.
        DemoDataSeeder.seed(path,LocalDate.of(2026,10,26));assertEquals(180,a.students().findAll().size());assertEquals(history,history(db));
    }
    @Test void sampleWorkbooksExerciseDetectionLeadingZerosAndUnsafeCells() throws Exception {
        Path out=directory.resolve("samples");DemoImportSamples.write(out);
        var reader=new StudentWorkbookReader();
        var english=reader.read(out.resolve("students-english.xlsx")).sheets().getFirst();
        assertEquals(25,english.rows().size());assertEquals("00990001",english.rows().get(1).cell(0).text());
        assertEquals(5,StudentColumns.detect(english.rows().getFirst()).size());
        var arabic=reader.read(out.resolve("students-arabic.xlsx")).sheets().getFirst();
        assertEquals(4,StudentColumns.detect(arabic.rows().getFirst()).size());
        var manual=reader.read(out.resolve("students-manual-mapping.xlsx")).sheets().getFirst();
        assertTrue(StudentColumns.detect(manual.rows().getFirst()).isEmpty());
        assertThrows(java.nio.file.FileAlreadyExistsException.class,()->DemoImportSamples.write(out));
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(out.resolve("students-validation-cases.xlsx").toFile())) {
            assertEquals(org.apache.poi.ss.usermodel.CellType.FORMULA,book.getSheetAt(0).getRow(8).getCell(0).getCellType());
            assertEquals(org.apache.poi.ss.usermodel.CellType.NUMERIC,book.getSheetAt(0).getRow(7).getCell(0).getCellType());
        }
    }
    private String history(Database db) throws Exception {
        var result=new StringBuilder();try(var c=db.openConnection();var s=c.createStatement();var r=s.executeQuery("SELECT * FROM schema_version ORDER BY version")) {
            while(r.next())for(int i=1;i<=4;i++)result.append(r.getString(i)).append('|');
        }return result.toString();
    }
}
