package com.professoros.model;
import java.math.BigDecimal;
import java.time.LocalDate;
public record ExamInput(String title, LocalDate date, Integer durationMinutes, BigDecimal intendedMarks, String instructions) { }
