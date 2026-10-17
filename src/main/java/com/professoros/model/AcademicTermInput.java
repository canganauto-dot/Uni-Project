package com.professoros.model;

import java.time.LocalDate;

public record AcademicTermInput(String name, String academicYear, LocalDate startDate,
                                LocalDate endDate, AcademicStatus status) { }
