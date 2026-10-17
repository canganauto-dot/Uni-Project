package com.professoros.model;

import java.time.Instant;
import java.util.Locale;

public record Student(long id, String studentNumber, String firstName, String lastName, String email,
                      String notes, Instant createdAt, Instant updatedAt) {
    public String fullName() { return firstName + " " + lastName; }
    public boolean matches(String query) {
        String term = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return studentNumber.toLowerCase(Locale.ROOT).contains(term)
                || fullName().toLowerCase(Locale.ROOT).contains(term)
                || email.toLowerCase(Locale.ROOT).contains(term);
    }
    @Override public String toString() { return studentNumber + " — " + fullName(); }
}
