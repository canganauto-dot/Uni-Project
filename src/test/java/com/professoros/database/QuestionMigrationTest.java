package com.professoros.database;
import com.professoros.model.*;
import com.professoros.repository.*;
import com.professoros.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class QuestionMigrationTest {
    @TempDir Path directory;
    @Test void upgradesVersionSixPreservingCoursesAndAppliesSevenOnce() throws Exception {
        Database database = new Database(directory.resolve("upgrade.db"));
        try(var connection=database.openConnection()) {
            new MigrationRunner(List.of("/db/migrations/V001__create_courses.sql","/db/migrations/V002__academic_terms.sql",
                    "/db/migrations/V003__course_offerings.sql","/db/migrations/V004__students.sql",
                    "/db/migrations/V005__enrollments.sql","/db/migrations/V006__lectures.sql")).migrate(connection);
        }
        var courses=new CourseService(new CourseRepository(database));
        var course=courses.create(new CourseInput("CS201","OOP","",3,""));
        database.initialize();
        var service=AcademicServices.create(database).questions();
        var saved=service.create(new QuestionInput(course.id(),QuestionType.MCQ,"Choose A","",Difficulty.EASY,BigDecimal.ONE,"","","",
                List.of(new AnswerChoice("A",true),new AnswerChoice("B",false))));
        String history=history(database);
        database.initialize();
        assertEquals(history,history(database)); assertEquals(course,courses.findById(course.id()));
        assertEquals(saved,service.findById(saved.id()));
        try(var c=database.openConnection();var s=c.createStatement();var r=s.executeQuery("PRAGMA foreign_key_check")) { assertFalse(r.next()); }
    }
    private String history(Database database) throws Exception {
        try(var c=database.openConnection();var s=c.createStatement();var r=s.executeQuery("SELECT group_concat(version || ':' || checksum || ':' || applied_at) FROM schema_version")) {
            assertTrue(r.next()); String result=r.getString(1); assertEquals(9,result.split(",").length); return result;
        }
    }
}
