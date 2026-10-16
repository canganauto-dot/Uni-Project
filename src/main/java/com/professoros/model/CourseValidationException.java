package com.professoros.model;

/** Expected, user-correctable course failure; messages are safe to display. */
public class CourseValidationException extends RuntimeException {
    public CourseValidationException(String message) {
        super(message);
    }
}
