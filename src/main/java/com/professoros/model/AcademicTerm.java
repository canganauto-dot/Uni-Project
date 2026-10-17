package com.professoros.model;

import java.time.Instant;
import java.time.LocalDate;

public record AcademicTerm(long id, String name, String academicYear, LocalDate startDate,
                           LocalDate endDate, AcademicStatus status, Instant createdAt, Instant updatedAt) {
    public String label() { return name + " " + academicYear; }
    @Override public String toString() { return label(); }
}
