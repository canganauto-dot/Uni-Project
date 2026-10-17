package com.professoros.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

public record Lecture(long id, long courseOfferingId, int lectureNumber, String title, LocalDate plannedDate,
                      String topics, String description, int durationMinutes, LectureStatus status,
                      String professorNotes, Instant createdAt, Instant updatedAt) {
    public boolean matches(String query) {
        String term = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return Integer.toString(lectureNumber).contains(term) || title.toLowerCase(Locale.ROOT).contains(term)
                || topics.toLowerCase(Locale.ROOT).contains(term) || description.toLowerCase(Locale.ROOT).contains(term)
                || professorNotes.toLowerCase(Locale.ROOT).contains(term);
    }
}
