package com.professoros.demo;

import java.util.List;

/** Fixed presentation fixtures. Never read from or written to the application database. */
public final class DemoDashboard {
    public record Metric(String label, String value, String detail) { }
    public record Entry(String marker, String title, String detail) { }
    public record Progress(String course, String detail, double fraction) { }

    public List<Metric> metrics() {
        return List.of(
                new Metric("Courses", "4", "In this sample semester"),
                new Metric("Students", "126", "Across sample courses"),
                new Metric("Upcoming Exams", "2", "Sample assessments"),
                new Metric("Pending Grading", "18", "Sample submissions"));
    }

    public List<Entry> schedule() {
        return List.of(
                new Entry("09:00", "Software Engineering", "Lecture 07 · Room B204"),
                new Entry("11:30", "Database Systems", "Lab session · Computing lab"),
                new Entry("14:00", "Student office hours", "Consultations · Faculty office"));
    }

    public List<Entry> deadlines() {
        return List.of(
                new Entry("MON", "Midterm exam outline", "Software Engineering · In 2 days"),
                new Entry("WED", "Assignment 03 grading", "Database Systems · In 4 days"),
                new Entry("FRI", "Project proposal review", "Research Methods · In 6 days"));
    }

    public List<Progress> progress() {
        return List.of(
                new Progress("Software Engineering", "7 of 12 lectures", 7.0 / 12),
                new Progress("Database Systems", "5 of 12 lectures", 5.0 / 12),
                new Progress("Data Structures", "8 of 14 lectures", 8.0 / 14),
                new Progress("Research Methods", "4 of 10 lectures", 4.0 / 10));
    }

    public List<Entry> activity() {
        return List.of(
                new Entry("01", "Lecture notes organized", "Software Engineering · Sample activity"),
                new Entry("02", "Assessment draft prepared", "Database Systems · Sample activity"),
                new Entry("03", "Attendance reviewed", "Data Structures · Sample activity"));
    }
}
