package com.professoros.model;
import java.math.BigDecimal;
public record GradingScale(BigDecimal a,BigDecimal b,BigDecimal c,BigDecimal d,BigDecimal pass) {
    public static GradingScale standard() { return new GradingScale(new BigDecimal("90"),new BigDecimal("80"),new BigDecimal("70"),new BigDecimal("60"),new BigDecimal("60")); }
}
