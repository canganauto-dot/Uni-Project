package com.professoros.model;

public enum LectureStatus {
    PLANNED("Planned"), COMPLETED("Completed"), PARTIALLY_COMPLETED("Partially completed"), CANCELLED("Cancelled");
    private final String label;
    LectureStatus(String label) { this.label = label; }
    public boolean unfinished() { return this == PLANNED || this == PARTIALLY_COMPLETED; }
    @Override public String toString() { return label; }
}
