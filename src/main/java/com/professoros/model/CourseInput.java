package com.professoros.model;

/** Untrusted create/edit input. IDs and timestamps are assigned by the application. */
public record CourseInput(String courseCode, String courseName, String department,
                          Integer creditHours, String description) { }
