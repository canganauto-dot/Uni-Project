package com.professoros.model;

import java.time.LocalDate;

/** No offering ID: creation/editing receives an explicit offering scope from the controller. */
public record LectureInput(Integer lectureNumber, String title, LocalDate plannedDate, String topics,
                           String description, Integer durationMinutes, LectureStatus status, String professorNotes) { }
