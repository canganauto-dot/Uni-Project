package com.professoros.model;
import java.math.BigDecimal;
import java.time.*;
public record Assessment(long id,long offeringId,long categoryId,String title,BigDecimal maximumMarks,LocalDate date,Instant createdAt,Instant updatedAt) {
    @Override public String toString() { return title+" / "+maximumMarks; }
}
