package com.professoros.service;
import com.professoros.model.*;
import java.math.*;
import java.util.*;
/** Pure calculations; missing scores never become stored zeros or inferred final failures. */
public final class GradeCalculator {
    private GradeCalculator() { }
    public static BigDecimal weightTotal(Gradebook book) { return book.categories().stream().map(AssessmentCategory::weight).reduce(BigDecimal.ZERO,BigDecimal::add); }
    public static String configurationMessage(Gradebook book) {
        String result="Category weights: "+weightTotal(book)+"%";
        if(weightTotal(book).compareTo(new BigDecimal("100"))!=0) { result+=" — WARNING: must total 100% for final results."; }
        var empty=book.categories().stream().filter(c->c.weight().signum()>0 && book.assessments().stream().noneMatch(a->a.categoryId()==c.id())).map(AssessmentCategory::name).toList();
        if(!empty.isEmpty()) { result+=" — Incomplete: add assessments to "+String.join(", ",empty)+"."; }
        return result;
    }
    public static GradeResult calculate(Gradebook book,long enrollmentId,Map<GradeKey,BigDecimal> scores) {
        Fraction points=new Fraction(BigInteger.ZERO,BigInteger.ONE); int required=0,graded=0;
        boolean configured=weightTotal(book).compareTo(new BigDecimal("100"))==0;
        for(var category:book.categories()) {
            if(category.weight().signum()==0) { continue; }
            var assessments=book.assessments().stream().filter(a->a.categoryId()==category.id()).toList();
            if(assessments.isEmpty()) { configured=false; }
            BigDecimal maximum=BigDecimal.ZERO,earned=BigDecimal.ZERO;
            for(var assessment:assessments) {
                required++; maximum=maximum.add(assessment.maximumMarks());
                var score=scores.get(new GradeKey(enrollmentId,assessment.id()));
                if(score!=null) { graded++; earned=earned.add(score); }
            }
            if(maximum.signum()>0) {
                points=points.add(new Fraction(earned.movePointRight(2).toBigIntegerExact().multiply(category.weight().movePointRight(2).toBigIntegerExact()),
                        maximum.movePointRight(2).toBigIntegerExact().multiply(BigInteger.valueOf(100))));
            }
        }
        boolean complete=configured && required>0 && graded==required;
        // Classify before display rounding: 89.999% must not become an A at a 90% boundary.
        var scale=book.scale();
        String letter=points.atLeast(scale.a())?"A":points.atLeast(scale.b())?"B":points.atLeast(scale.c())?"C":points.atLeast(scale.d())?"D":"F";
        return new GradeResult(new BigDecimal(points.numerator()).divide(new BigDecimal(points.denominator()),2,RoundingMode.HALF_UP),graded,required,complete,
                complete?letter:"—",complete?(points.atLeast(scale.pass())?"PASS":"FAIL"):"INCOMPLETE");
    }
    /** Reduced rational sums keep exact threshold comparisons even for repeating category ratios. */
    private record Fraction(BigInteger numerator,BigInteger denominator) {
        Fraction { var gcd=numerator.gcd(denominator); numerator=numerator.divide(gcd); denominator=denominator.divide(gcd); }
        Fraction add(Fraction other) { return new Fraction(numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),denominator.multiply(other.denominator)); }
        boolean atLeast(BigDecimal threshold) { return numerator.multiply(BigInteger.valueOf(100)).compareTo(threshold.movePointRight(2).toBigIntegerExact().multiply(denominator))>=0; }
    }
    public static Map<GradeKey,BigDecimal> scores(Gradebook book) {
        Map<GradeKey,BigDecimal> result=new HashMap<>(); book.grades().forEach(g->result.put(g.key(),g.score())); return result;
    }
    public static String statistics(Gradebook book,Map<GradeKey,BigDecimal> scores) {
        var results=book.students().stream().filter(s->s.enrollment().status()!=EnrollmentStatus.WITHDRAWN)
                .map(s->calculate(book,s.enrollment().id(),scores)).toList();
        var complete=results.stream().filter(GradeResult::complete).toList();
        if(complete.isEmpty()) { return "No complete results · "+results.size()+" incomplete · withdrawn students excluded"; }
        var values=complete.stream().map(GradeResult::weightedPoints).toList();
        var mean=values.stream().reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.valueOf(values.size()),2,RoundingMode.HALF_UP);
        return "Complete: "+complete.size()+" · Incomplete: "+(results.size()-complete.size())+" · Mean: "+mean+"% · Min: "+Collections.min(values)+"% · Max: "+Collections.max(values)+"% · Passed: "+complete.stream().filter(r->r.outcome().equals("PASS")).count()+" (withdrawn excluded)";
    }
}
