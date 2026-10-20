package com.professoros.service;
import com.professoros.database.Database;
import com.professoros.model.*;
import com.professoros.repository.*;
import com.professoros.support.LectureFixture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayInputStream;
import static org.junit.jupiter.api.Assertions.*;
class AttendanceTest {
    @TempDir Path directory;
    LectureFixture f;AcademicServices a;long offering,enrollment,otherEnrollment;Lecture lecture;
    @BeforeEach void setup() throws Exception {
        f=LectureFixture.create(directory.resolve("attendance.db"));a=f.academic();offering=f.first().id();
        var student=a.students().create(new StudentInput("00017","أحمد","علي","",""));
        enrollment=a.enrollments().enroll(student.id(),offering).id();otherEnrollment=a.enrollments().enroll(student.id(),f.second().id()).id();
        lecture=a.lectures().create(offering,LectureFixture.input(1,LocalDate.now(),LectureStatus.COMPLETED));
    }
    @Test void saveEditClearAndRestartPreserveOneRecord() throws Exception {
        assertNull(a.attendance().load(offering).status(enrollment,lecture.id()));
        a.attendance().save(offering,lecture.id(),Map.of(enrollment,AttendanceStatus.ABSENT));
        var first=a.attendance().load(offering).records().getFirst();
        a.attendance().save(offering,lecture.id(),Map.of(enrollment,AttendanceStatus.LATE));
        f.database().initialize();var restored=AcademicServices.create(new Database(directory.resolve("attendance.db"))).attendance().load(offering);
        assertEquals(1,restored.records().size());assertEquals(AttendanceStatus.LATE,restored.status(enrollment,lecture.id()));assertEquals(first.createdAt(),restored.records().getFirst().createdAt());
        var clear=new HashMap<Long,AttendanceStatus>();clear.put(enrollment,null);a.attendance().save(offering,lecture.id(),clear);
        assertTrue(a.attendance().load(offering).records().isEmpty());
    }
    @Test void serviceRejectsCrossOfferingAndCancelledWithoutPartialSave() {
        var changes=new LinkedHashMap<Long,AttendanceStatus>();changes.put(enrollment,AttendanceStatus.PRESENT);changes.put(otherEnrollment,AttendanceStatus.ABSENT);
        assertThrows(DomainValidationException.class,()->a.attendance().save(offering,lecture.id(),changes));assertTrue(a.attendance().load(offering).records().isEmpty());
        assertThrows(DomainValidationException.class,()->a.attendance().save(f.second().id(),lecture.id(),Map.of(otherEnrollment,AttendanceStatus.LATE)));
        a.lectures().update(offering,lecture.id(),LectureFixture.input(1,LocalDate.now(),LectureStatus.CANCELLED));
        assertThrows(DomainValidationException.class,()->a.attendance().save(offering,lecture.id(),Map.of(enrollment,AttendanceStatus.PRESENT)));
        assertThrows(DomainValidationException.class,()->a.attendance().load(9999));
    }
    @Test void repositoryRollsBackInvalidBatchAndForeignKeysProtectHistory() throws Exception {
        var repository=new AttendanceRepository(f.database());var changes=new LinkedHashMap<Long,AttendanceStatus>();changes.put(enrollment,AttendanceStatus.PRESENT);changes.put(otherEnrollment,AttendanceStatus.LATE);
        assertThrows(DomainValidationException.class,()->repository.save(offering,lecture.id(),changes,Instant.now()));assertTrue(repository.findByOffering(offering).isEmpty());
        repository.save(offering,lecture.id(),Map.of(enrollment,AttendanceStatus.PRESENT),Instant.now());
        assertThrows(DomainValidationException.class,()->a.lectures().delete(offering,lecture.id()));
        try(var c=f.database().openConnection();var s=c.createStatement()) {
            assertThrows(SQLException.class,()->s.execute("DELETE FROM enrollments WHERE id="+enrollment));
            assertThrows(SQLException.class,()->s.execute("UPDATE lectures SET course_offering_id="+f.second().id()+" WHERE id="+lecture.id()));
            assertThrows(SQLException.class,()->s.execute("UPDATE attendance SET status='UNKNOWN'"));
            assertThrows(SQLException.class,()->s.execute("INSERT INTO attendance SELECT * FROM attendance"));
        }
    }
    @Test void percentagesExcludeExcusedUnmarkedCancelledAndCourseWithdrawals() {
        var statuses=List.of(AttendanceStatus.PRESENT,AttendanceStatus.ABSENT,AttendanceStatus.LATE,AttendanceStatus.EXCUSED);
        for(int i=0;i<4;i++) {var l=i==0?lecture:a.lectures().create(offering,LectureFixture.input(i+1,LocalDate.now().plusDays(i),LectureStatus.COMPLETED));a.attendance().save(offering,l.id(),Map.of(enrollment,statuses.get(i)));}
        a.lectures().create(offering,LectureFixture.input(5,LocalDate.now(),LectureStatus.PLANNED));
        var cancelled=a.lectures().create(offering,LectureFixture.input(6,LocalDate.now(),LectureStatus.COMPLETED));a.attendance().save(offering,cancelled.id(),Map.of(enrollment,AttendanceStatus.ABSENT));a.lectures().update(offering,cancelled.id(),LectureFixture.input(6,LocalDate.now(),LectureStatus.CANCELLED));
        var result=AttendanceCalculator.student(a.attendance().load(offering),enrollment);
        assertEquals(new AttendanceSummary(1,1,1,1,1,new BigDecimal("66.67")),result);
        a.enrollments().updateStatus(enrollment,EnrollmentStatus.WITHDRAWN);
        assertNull(AttendanceCalculator.course(a.attendance().load(offering)).percentage());assertEquals(result,AttendanceCalculator.student(a.attendance().load(offering),enrollment));
    }
    @Test void noRecordedDenominatorIsNotZeroAndDraftRevertingClearsDirty() {
        var book=a.attendance().load(offering);assertNull(AttendanceCalculator.student(book,enrollment).percentage());
        var draft=new AttendanceDraft(book,lecture.id());draft.set(enrollment,AttendanceStatus.ABSENT);assertTrue(draft.dirty());assertNull(book.status(enrollment,lecture.id()));draft.set(enrollment,null);assertFalse(draft.dirty());
        assertThrows(DomainValidationException.class,()->draft.set(otherEnrollment,AttendanceStatus.PRESENT));
        a.attendance().save(offering,lecture.id(),Map.of(enrollment,AttendanceStatus.EXCUSED));assertNull(AttendanceCalculator.student(a.attendance().load(offering),enrollment).percentage());
    }
    @Test void excelAddsAttendancePreservesTextIdsBlankAndGradeZero() throws Exception {
        var next=a.lectures().create(offering,LectureFixture.input(2,LocalDate.now(),LectureStatus.PLANNED));
        a.attendance().save(offering,lecture.id(),Map.of(enrollment,AttendanceStatus.ABSENT));
        var category=a.grades().saveCategory(offering,null,"Coursework",new BigDecimal("100"));
        var assessment=a.grades().saveAssessment(offering,null,category.id(),"Test",new BigDecimal("10"),LocalDate.now());
        a.grades().saveGrades(offering,Map.of(new GradeKey(enrollment,assessment.id()),"0"));
        byte[] bytes=new ExcelExportService(a.grades(),a.offerings(),a.attendance()).render(offering);
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(4,workbook.getNumberOfSheets());var row=workbook.getSheet("Attendance").getRow(6);
            assertEquals("00017",row.getCell(0).getStringCellValue());assertEquals("أحمد علي",row.getCell(1).getStringCellValue());
            assertEquals("ABSENT",row.getCell(3).getStringCellValue());assertEquals(org.apache.poi.ss.usermodel.CellType.BLANK,row.getCell(4).getCellType());assertEquals(0,row.getCell(10).getNumericCellValue());
            assertEquals(0,workbook.getSheet("Grades").getRow(6).getCell(3).getNumericCellValue());
        }
    }
}
