package com.professoros.model;

public record CourseOfferingInput(long courseId, long academicTermId, String section,
                                  String notes, AcademicStatus status) { }
