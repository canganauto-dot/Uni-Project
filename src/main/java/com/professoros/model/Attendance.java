package com.professoros.model;
import java.time.Instant;
public record Attendance(long enrollmentId,long lectureId,long courseOfferingId,AttendanceStatus status,Instant createdAt,Instant updatedAt) { }
