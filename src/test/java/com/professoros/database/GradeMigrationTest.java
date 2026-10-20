package com.professoros.database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class GradeMigrationTest {
    @TempDir Path directory;
    @Test void upgradesEightPreservesCatalogAndRecordsNineExactlyOnce() throws Exception {
        var db=new Database(directory.resolve("upgrade.db"));
        try(var c=db.openConnection()) {
            new MigrationRunner(List.of("/db/migrations/V001__create_courses.sql","/db/migrations/V002__academic_terms.sql",
                "/db/migrations/V003__course_offerings.sql","/db/migrations/V004__students.sql","/db/migrations/V005__enrollments.sql",
                "/db/migrations/V006__lectures.sql","/db/migrations/V007__question_bank.sql","/db/migrations/V008__exam_builder.sql")).migrate(c);
        }
        var course=new com.professoros.service.CourseService(new com.professoros.repository.CourseRepository(db))
                .create(new com.professoros.model.CourseInput("CS201","OOP","",3,""));
        db.initialize(); db.initialize();
        assertEquals(course,new com.professoros.repository.CourseRepository(db).findById(course.id()).orElseThrow());
        try(var c=db.openConnection();var s=c.createStatement()) {
            try(var r=s.executeQuery("SELECT count(*),max(version) FROM schema_version")) { assertTrue(r.next()); assertEquals(10,r.getInt(1)); assertEquals(10,r.getInt(2)); }
            try(var r=s.executeQuery("PRAGMA foreign_key_check")) { assertFalse(r.next()); }
        }
    }
}
