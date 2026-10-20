package com.professoros.database;
import com.professoros.model.*;
import com.professoros.repository.*;
import com.professoros.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class ExamMigrationTest {
    @TempDir Path directory;
    @Test void upgradesVersionSevenPreservesBankAndAppliesEightOnlyOnce() throws Exception {
        var db=new Database(directory.resolve("upgrade.db"));
        try(var c=db.openConnection()) {
            new MigrationRunner(List.of("/db/migrations/V001__create_courses.sql","/db/migrations/V002__academic_terms.sql",
                    "/db/migrations/V003__course_offerings.sql","/db/migrations/V004__students.sql",
                    "/db/migrations/V005__enrollments.sql","/db/migrations/V006__lectures.sql","/db/migrations/V007__question_bank.sql")).migrate(c);
        }
        var academic=AcademicServices.create(db);
        var course=new CourseService(new CourseRepository(db)).create(new CourseInput("CS201","OOP","",3,""));
        var term=academic.terms().create(new AcademicTermInput("Fall","2026",LocalDate.of(2026,9,1),LocalDate.of(2027,1,1),AcademicStatus.ACTIVE));
        var offering=academic.offerings().create(new CourseOfferingInput(course.id(),term.id(),"A","",AcademicStatus.ACTIVE));
        var question=academic.questions().create(new QuestionInput(course.id(),QuestionType.ESSAY,"Explain inheritance","OOP",Difficulty.MEDIUM,BigDecimal.TEN,"","","",List.of()));
        db.initialize();
        var exam=academic.exams().save(offering.id(),null,new ExamInput("Midterm",LocalDate.of(2026,11,1),60,BigDecimal.TEN,""),List.of(question.id()));
        String history=history(db); db.initialize();
        assertEquals(history,history(db)); assertEquals(question,academic.questions().findById(question.id()));
        assertEquals(exam,academic.exams().findById(offering.id(),exam.id()));
        try(var c=db.openConnection();var s=c.createStatement();var r=s.executeQuery("PRAGMA foreign_key_check")) { assertFalse(r.next()); }
    }
    private static String history(Database db) throws Exception {
        try(var c=db.openConnection();var s=c.createStatement();var r=s.executeQuery("SELECT group_concat(version || ':' || checksum || ':' || applied_at) FROM schema_version")) {
            assertTrue(r.next()); var result=r.getString(1); assertEquals(10,result.split(",").length); return result;
        }
    }
}
