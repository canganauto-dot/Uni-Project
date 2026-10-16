package com.professoros.service;

import com.professoros.database.Database;
import com.professoros.model.CourseInput;
import com.professoros.model.CourseValidationException;
import com.professoros.model.DuplicateCourseCodeException;
import com.professoros.repository.CourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class CourseServiceTest {
    @TempDir Path directory;
    private CourseService service;
    private CourseRepository repository;
    private final Instant time = Instant.parse("2026-10-16T09:00:00Z");

    @BeforeEach
    void initialize() throws Exception {
        Database database = new Database(directory.resolve("courses.db"));
        database.initialize();
        repository = new CourseRepository(database);
        service = new CourseService(repository, Clock.fixed(time, ZoneOffset.UTC));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void requiresCourseCode(String code) {
        var failure = assertThrows(CourseValidationException.class,
                () -> service.create(new CourseInput(code, "Course", "", 3, "")));
        assertEquals("Course code is required.", failure.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void requiresCourseName(String name) {
        var failure = assertThrows(CourseValidationException.class,
                () -> service.create(new CourseInput("CS201", name, "", 3, "")));
        assertEquals("Course name is required.", failure.getMessage());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, 0, 31, Integer.MAX_VALUE})
    void rejectsInvalidCreditHours(Integer credits) {
        assertThrows(CourseValidationException.class,
                () -> service.create(new CourseInput("CS201", "Course", "", credits, "")));
        assertTrue(service.findAll().isEmpty());
    }

    @Test
    void normalizesFieldsAndAssignsTimestamps() {
        var course = service.create(new CourseInput(" cs201 ", "  برمجة  ", " Computing ", 3, " Notes "));
        assertEquals("CS201", course.courseCode());
        assertEquals("برمجة", course.courseName());
        assertEquals("Computing", course.department());
        assertEquals("Notes", course.description());
        assertEquals(time, course.createdAt());
        assertEquals(time, course.updatedAt());
        var optional = service.create(new CourseInput("CS202", "Other", null, 1, null));
        assertEquals("", optional.department());
        assertEquals("", optional.description());
    }

    @Test
    void rejectsDuplicateCodeAfterNormalizationAndOnEdit() {
        var one = service.create(new CourseInput("CS201", "One", "", 3, ""));
        var two = service.create(new CourseInput("CS202", "Two", "", 3, ""));
        assertThrows(DuplicateCourseCodeException.class,
                () -> service.create(new CourseInput(" cs201 ", "Duplicate", "", 3, "")));
        assertThrows(DuplicateCourseCodeException.class,
                () -> service.update(two.id(), new CourseInput(" cs201 ", "Duplicate", "", 3, "")));
        assertDoesNotThrow(() -> service.update(one.id(), new CourseInput("cs201", "Renamed", "", 4, "")));
    }

    @Test
    void validatesLimitsAndCodeCharacters() {
        for (CourseInput invalid : java.util.List.of(
                new CourseInput("C".repeat(21), "Name", "", 3, ""),
                new CourseInput("CS/201", "Name", "", 3, ""),
                new CourseInput("CS201", "N".repeat(201), "", 3, ""),
                new CourseInput("CS201", "Name", "D".repeat(121), 3, ""),
                new CourseInput("CS201", "Name", "", 3, "D".repeat(5001)),
                new CourseInput("CS201", "Name\0", "", 3, ""))) {
            assertThrows(CourseValidationException.class, () -> service.create(invalid));
        }
        assertThrows(CourseValidationException.class, () -> service.create(null));
    }

    @Test
    void advancesUpdatedTimestampAndPreservesCreatedTimestamp() {
        var original = service.create(new CourseInput("CS201", "Course", "", 3, ""));
        CourseService later = new CourseService(repository, Clock.fixed(time.plusSeconds(60), ZoneOffset.UTC));
        var updated = later.update(original.id(), new CourseInput("CS201", "New name", "", 3, ""));
        assertEquals(time, updated.createdAt());
        assertEquals(time.plusSeconds(60), updated.updatedAt());
        var backwardsClock = service.update(original.id(), new CourseInput("CS201", "Again", "", 3, ""));
        assertEquals(updated.updatedAt(), backwardsClock.updatedAt());
    }

    @Test
    void reportsMissingCoursesOnReadEditAndDelete() {
        assertThrows(CourseValidationException.class, () -> service.findById(123));
        assertThrows(CourseValidationException.class,
                () -> service.update(123, new CourseInput("CS201", "Course", "", 3, "")));
        assertThrows(CourseValidationException.class, () -> service.delete(123));
    }
}
