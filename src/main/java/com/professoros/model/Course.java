package com.professoros.model;

import java.time.Instant;
import java.util.Locale;

/** Persisted domain data, independent of JavaFX and JDBC. Optional text uses empty strings. */
public record Course(long id, String courseCode, String courseName, String department,
                     int creditHours, String description, Instant createdAt, Instant updatedAt) {
    /** Literal, case-insensitive substring search shared by the table and repository. */
    public boolean matches(String query) {
        String term = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return courseCode.toLowerCase(Locale.ROOT).contains(term)
                || courseName.toLowerCase(Locale.ROOT).contains(term)
                || department.toLowerCase(Locale.ROOT).contains(term);
    }
}
