package com.professoros.model;

import java.time.Instant;

public record Enrollment(long id, long studentId, long courseOfferingId, Instant enrolledAt, EnrollmentStatus status) { }
