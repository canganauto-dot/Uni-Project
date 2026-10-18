package com.professoros.model;
import java.math.BigDecimal;
public record GradeResult(BigDecimal weightedPoints,int graded,int required,boolean complete,String letter,String outcome) { }
