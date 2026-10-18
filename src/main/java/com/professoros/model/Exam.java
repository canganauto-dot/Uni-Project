package com.professoros.model;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
public record Exam(long id, long offeringId, String title, LocalDate date, int durationMinutes,
                   BigDecimal intendedMarks, String instructions, ExamStatus status, Instant createdAt,
                   Instant updatedAt, List<ExamQuestion> questions) {
    public Exam { questions = List.copyOf(questions); }
    public BigDecimal calculatedMarks() { return questions.stream().map(ExamQuestion::marks).reduce(BigDecimal.ZERO,BigDecimal::add); }
    public boolean marksDiffer() { return calculatedMarks().compareTo(intendedMarks)!=0; }
}
