package com.professoros.service;

import com.professoros.model.Course;
import com.professoros.model.CourseInput;
import com.professoros.model.CourseValidationException;
import com.professoros.model.DuplicateCourseCodeException;
import com.professoros.repository.CourseRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

public final class CourseService {
    private final CourseRepository repository;
    private final Clock clock;

    public CourseService(CourseRepository repository) {
        this(repository, Clock.systemUTC());
    }

    public CourseService(CourseRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Course create(CourseInput input) {
        CourseInput valid = validate(input);
        checkDuplicate(valid.courseCode(), 0);
        return repository.create(valid, now());
    }

    public Course update(long id, CourseInput input) {
        CourseInput valid = validate(input);
        Course existing = findById(id);
        checkDuplicate(valid.courseCode(), id);
        Instant timestamp = now();
        if (timestamp.isBefore(existing.updatedAt())) {
            timestamp = existing.updatedAt(); // Do not go backwards if the system clock changes.
        }
        return repository.update(id, valid, timestamp).orElseThrow(CourseService::missingCourse);
    }

    public void delete(long id) {
        if (!repository.delete(id)) {
            throw missingCourse();
        }
    }

    public Course findById(long id) {
        return repository.findById(id).orElseThrow(CourseService::missingCourse);
    }

    public List<Course> findAll() {
        return repository.findAll();
    }

    public List<Course> search(String query) {
        return repository.search(query);
    }

    private void checkDuplicate(String code, long excludedId) {
        repository.findByCourseCode(code).filter(course -> course.id() != excludedId).ifPresent(course -> {
            throw new DuplicateCourseCodeException(code);
        });
        // The database UNIQUE constraint remains authoritative if another writer wins a race.
    }

    private static CourseInput validate(CourseInput input) {
        if (input == null) {
            throw new CourseValidationException("Course details are required.");
        }
        String code = text(input.courseCode()).toUpperCase(Locale.ROOT);
        String name = text(input.courseName());
        String department = text(input.department());
        String description = text(input.description());
        required(code, "Course code", 20);
        if (!code.matches("[A-Z0-9][A-Z0-9 ._-]*")) {
            throw new CourseValidationException("Course code must start with a letter or number and use only A–Z, 0–9, spaces, dots, hyphens or underscores.");
        }
        required(name, "Course name", 200);
        maximum(department, "Department", 120);
        maximum(description, "Description", 5000);
        if (input.creditHours() == null) {
            throw new CourseValidationException("Credit hours must be a whole number between 1 and 30.");
        }
        if (input.creditHours() <= 0) {
            throw new CourseValidationException("Credit hours must be greater than zero.");
        }
        if (input.creditHours() > 30) {
            throw new CourseValidationException("Credit hours must not exceed 30.");
        }
        return new CourseInput(code, name, department, input.creditHours(), description);
    }

    private static String text(String value) {
        return value == null ? "" : value.strip();
    }

    private static void required(String value, String label, int limit) {
        if (value.isEmpty()) {
            throw new CourseValidationException(label + " is required.");
        }
        maximum(value, label, limit);
    }

    private static void maximum(String value, String label, int limit) {
        if (value.codePointCount(0, value.length()) > limit || value.indexOf('\0') >= 0) {
            throw new CourseValidationException(label + " must contain at most " + limit + " characters and no null characters.");
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static CourseValidationException missingCourse() {
        return new CourseValidationException("This course no longer exists. Refresh the course list and try again.");
    }
}
