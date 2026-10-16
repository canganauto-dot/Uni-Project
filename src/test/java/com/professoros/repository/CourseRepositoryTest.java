package com.professoros.repository;

import com.professoros.database.Database;
import com.professoros.model.CourseInput;
import com.professoros.model.DuplicateCourseCodeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class CourseRepositoryTest {
    @TempDir Path directory;
    private CourseRepository repository;
    private final Instant created = Instant.parse("2026-10-16T09:00:00Z");

    @BeforeEach
    void initialize() throws Exception {
        Database database = new Database(directory.resolve("courses.db"));
        database.initialize();
        repository = new CourseRepository(database);
    }

    private static CourseInput input(String code, String name, String department) {
        return new CourseInput(code, name, department, 3, "Professor's notes; SELECT is plain text.");
    }

    @Test
    void createsRetrievesAndSurvivesReinitialization() throws Exception {
        var course = repository.create(input("CS201", "Object Oriented Programming", "Computing"), created);
        assertTrue(course.id() > 0);
        assertEquals(created, course.createdAt());
        assertEquals(course, repository.findById(course.id()).orElseThrow());
        assertEquals(course, repository.findByCourseCode("cs201").orElseThrow());
        Database reopened = new Database(directory.resolve("courses.db"));
        reopened.initialize();
        assertEquals(course, new CourseRepository(reopened).findAll().getFirst());
    }

    @Test
    void updatesAllEditableFieldsAndPreservesCreationTime() {
        var original = repository.create(input("CS201", "OOP", "Computing"), created);
        Instant updated = created.plusSeconds(60);
        var changed = repository.update(original.id(),
                new CourseInput("CS202", "Databases", "Engineering", 4, "Updated description"), updated).orElseThrow();
        assertEquals(original.id(), changed.id());
        assertEquals("CS202", changed.courseCode());
        assertEquals("Databases", changed.courseName());
        assertEquals("Engineering", changed.department());
        assertEquals(4, changed.creditHours());
        assertEquals("Updated description", changed.description());
        assertEquals(created, changed.createdAt());
        assertEquals(updated, changed.updatedAt());
        assertEquals(changed, repository.findById(original.id()).orElseThrow());
    }

    @Test
    void deletesOnlyRequestedCourseAndReportsMissingRows() {
        var first = repository.create(input("CS201", "One", ""), created);
        var second = repository.create(input("CS202", "Two", ""), created);
        assertTrue(repository.delete(first.id()));
        assertFalse(repository.delete(first.id()));
        assertTrue(repository.findById(first.id()).isEmpty());
        assertEquals(second, repository.findAll().getFirst());
        assertTrue(repository.update(first.id(), input("CS203", "Missing", ""), created).isEmpty());
    }

    @Test
    void databaseRejectsDuplicateInsertAndUpdateWithoutChangingExistingRows() {
        var first = repository.create(input("CS201", "One", ""), created);
        var second = repository.create(input("CS202", "Two", ""), created);
        assertThrows(DuplicateCourseCodeException.class,
                () -> repository.create(input("CS201", "Duplicate", ""), created));
        assertThrows(DuplicateCourseCodeException.class,
                () -> repository.update(second.id(), input("CS201", "Duplicate", ""), created));
        assertEquals(first, repository.findById(first.id()).orElseThrow());
        assertEquals(second, repository.findById(second.id()).orElseThrow());
    }

    @Test
    void searchMatchesCodeNameAndDepartmentLiterallyIgnoringCase() {
        var first = repository.create(input("CS201", "ÉTUDE 100%", "Computing"), created);
        repository.create(input("MATH1", "Algebra", "Science"), created);
        for (String query : new String[]{"cs201", "étude", "COMPUTING", "100%"}) {
            assertEquals(java.util.List.of(first), repository.search(query));
        }
        assertTrue(repository.search("' OR 1=1 --").isEmpty());
        assertTrue(repository.search("_").isEmpty());
        assertEquals(2, repository.search("  ").size());
    }

    @Test
    void schemaRejectsInvalidCreditHoursEvenWithoutService() {
        assertThrows(CourseStorageException.class, () -> repository.create(
                new CourseInput("CS201", "Course", "", 0, ""), created));
        assertTrue(repository.findAll().isEmpty());
    }
}
