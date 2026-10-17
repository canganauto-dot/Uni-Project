package com.professoros.model;

import java.time.Instant;

public record CourseOffering(long id, long courseId, long academicTermId, String section,
                             String notes, AcademicStatus status, Instant createdAt, Instant updatedAt) { }
