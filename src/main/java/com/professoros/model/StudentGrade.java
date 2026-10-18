package com.professoros.model;
import java.math.BigDecimal;
import java.time.Instant;
public record StudentGrade(GradeKey key,BigDecimal score,Instant createdAt,Instant updatedAt) { }
