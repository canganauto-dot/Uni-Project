package com.professoros.model;

/** Joined read model for human-readable selections; catalog fields are not copied into storage. */
public record OfferingSummary(CourseOffering offering, String courseCode, String courseName,
                              String termName, String academicYear) {
    public String label() {
        return courseCode + " · " + termName + " " + academicYear + " · Section " + offering.section();
    }
    @Override public String toString() { return label(); }
}
