package com.professoros.database;

import com.professoros.model.*;
import com.professoros.repository.*;
import com.professoros.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LectureMigrationTest {
    @TempDir Path directory;
    @Test void upgradesVersionFivePreservingHierarchyAndAppliesSixOnlyOnce() throws Exception {
        Database database = new Database(directory.resolve("upgrade.db"));
        try (var connection = database.openConnection()) {
            new MigrationRunner(List.of("/db/migrations/V001__create_courses.sql","/db/migrations/V002__academic_terms.sql",
                    "/db/migrations/V003__course_offerings.sql","/db/migrations/V004__students.sql","/db/migrations/V005__enrollments.sql")).migrate(connection);
        }
        AcademicServices academic = AcademicServices.create(database);
        var course = new CourseService(new CourseRepository(database)).create(new CourseInput("CS201","OOP","",3,""));
        var term = academic.terms().create(new AcademicTermInput("Fall","2026",LocalDate.of(2026,9,1),LocalDate.of(2027,1,1),AcademicStatus.ACTIVE));
        var offering = academic.offerings().create(new CourseOfferingInput(course.id(),term.id(),"A","",AcademicStatus.ACTIVE));
        var student = academic.students().create(new StudentInput("1","Ahmed","Ali","",""));
        var enrollment = academic.enrollments().enroll(student.id(),offering.id());
        database.initialize();
        var saved = academic.lectures().create(offering.id(),new LectureInput(1,"Introduction",LocalDate.of(2026,10,17),"","",60,LectureStatus.PLANNED,""));
        String applied;
        try (var connection = database.openConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT applied_at FROM schema_version WHERE version=6")) {
            assertTrue(result.next()); applied = result.getString(1);
        }
        database.initialize();
        assertEquals(saved,academic.lectures().findById(offering.id(),saved.id()));
        assertEquals(enrollment,academic.enrollments().findStudentsForOffering(offering.id()).getFirst().enrollment());
        try (var connection = database.openConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT applied_at FROM schema_version WHERE version=6")) {
            assertTrue(result.next()); assertEquals(applied,result.getString(1)); assertFalse(result.next());
        }
    }
}
