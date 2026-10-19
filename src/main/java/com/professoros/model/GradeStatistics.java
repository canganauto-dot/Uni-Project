package com.professoros.model;
import java.math.BigDecimal;
public record GradeStatistics(int complete,int incomplete,long passed,BigDecimal mean,BigDecimal minimum,BigDecimal maximum) { }
