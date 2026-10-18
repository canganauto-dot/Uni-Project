package com.professoros.service;
import com.professoros.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Calculation policy tests without JDBC or JavaFX. */
class GradeCalculatorTest {
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static final Instant NOW=Instant.parse("2026-10-18T00:00:00Z");
    private Gradebook book(List<AssessmentCategory> categories,List<Assessment> assessments) {
        return new Gradebook(1,categories,assessments,List.of(),List.of(),GradingScale.standard());
    }
    private AssessmentCategory category(long id,String weight) { return new AssessmentCategory(id,1,"C"+id,n(weight),NOW,NOW); }
    private Assessment assessment(long id,long category,String max) { return new Assessment(id,1,category,"A"+id,n(max),LocalDate.of(2026,11,1),NOW,NOW); }
    @ParameterizedTest @CsvSource({"100,A,PASS","90,A,PASS","89.99,B,PASS","80,B,PASS","70,C,PASS","60,D,PASS","59.99,F,FAIL","0,F,FAIL"})
    void inclusiveLetterAndPassThresholds(String score,String letter,String outcome) {
        var book=book(List.of(category(1,"100")),List.of(assessment(1,1,"100")));
        var result=GradeCalculator.calculate(book,1,Map.of(new GradeKey(1,1),n(score)));
        assertTrue(result.complete()); assertEquals(letter,result.letter()); assertEquals(outcome,result.outcome());
    }
    @Test void repeatingRatiosDoNotMisclassifyAnExactBoundary() {
        var book=book(List.of(category(1,"20"),category(2,"20"),category(3,"20"),category(4,"40")),
                List.of(assessment(1,1,"3"),assessment(2,2,"3"),assessment(3,3,"3"),assessment(4,4,"1")));
        var result=GradeCalculator.calculate(book,1,Map.of(new GradeKey(1,1),n("2"),new GradeKey(1,2),n("2"),new GradeKey(1,3),n("2"),new GradeKey(1,4),n("1")));
        assertEquals(n("80.00"),result.weightedPoints()); assertEquals("B",result.letter());
    }
    @Test void otherEnrollmentScoresDoNotFillMissingCells() {
        var book=book(List.of(category(1,"100")),List.of(assessment(1,1,"100")));
        var result=GradeCalculator.calculate(book,1,Map.of(new GradeKey(2,1),n("100")));
        assertFalse(result.complete()); assertEquals(0,result.graded()); assertEquals(n("0.00"),result.weightedPoints());
    }
}
