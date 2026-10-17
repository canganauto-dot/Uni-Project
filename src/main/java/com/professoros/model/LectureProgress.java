package com.professoros.model;

import java.time.LocalDate;

/** Simple descriptive counts, not predictions or weighted estimates. */
public record LectureProgress(int total, int completed, int partial, int cancelled, int overdue,
                              int dueToday, LocalDate nextPlannedDate, LocalDate asOf) {
    public int scheduledTotal() { return total - cancelled; }
    public double completionPercent() { return scheduledTotal() == 0 ? 0 : completed * 100.0 / scheduledTotal(); }
    public String scheduleStatus() {
        if (total == 0) { return "No lectures scheduled"; }
        if (scheduledTotal() == 0) { return "All lectures cancelled"; }
        if (completed == scheduledTotal()) { return "All scheduled lectures completed"; }
        if (overdue > 0) { return "Behind schedule · " + overdue + " unfinished past planned date"; }
        if (dueToday > 0) { return "Due today · " + dueToday + " unfinished"; }
        return nextPlannedDate == null ? "On schedule" : "On schedule · next planned date " + nextPlannedDate;
    }
}
