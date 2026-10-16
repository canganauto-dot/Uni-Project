package com.professoros.model;

public final class DuplicateCourseCodeException extends CourseValidationException {
    public DuplicateCourseCodeException(String code) {
        super("Course code " + code + " already exists.");
    }
}
