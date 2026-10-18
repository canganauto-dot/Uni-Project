package com.professoros.model;
import java.math.BigDecimal;
import java.time.Instant;
public record AssessmentCategory(long id,long offeringId,String name,BigDecimal weight,Instant createdAt,Instant updatedAt) {
    @Override public String toString() { return name+" ("+weight+"%)"; }
}
