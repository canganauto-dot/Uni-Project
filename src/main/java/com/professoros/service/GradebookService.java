package com.professoros.service;
import com.professoros.model.*;
import com.professoros.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class GradebookService {
    private final GradebookRepository repository;
    private final CourseOfferingRepository offerings;
    private final EnrollmentRepository enrollments;
    private final Clock clock;
    public GradebookService(GradebookRepository repository,CourseOfferingRepository offerings,EnrollmentRepository enrollments) { this(repository,offerings,enrollments,Clock.systemUTC()); }
    public GradebookService(GradebookRepository repository,CourseOfferingRepository offerings,EnrollmentRepository enrollments,Clock clock) {
        this.repository=repository; this.offerings=offerings; this.enrollments=enrollments; this.clock=clock;
    }
    private void offering(long id) { offerings.findById(id).orElseThrow(()->AcademicValidation.missing("Offering")); }
    public Gradebook load(long offering) {
        offering(offering);
        return new Gradebook(offering,repository.categories(offering),repository.assessments(offering),
                enrollments.findStudentsForOffering(offering),repository.grades(offering),repository.scale(offering));
    }
    public static BigDecimal number(String text,String label) {
        try { return new BigDecimal(text.strip()); }
        catch(NumberFormatException | NullPointerException failure) { throw new DomainValidationException(label+" must be a decimal number."); }
    }
    private static BigDecimal range(BigDecimal value,String label,BigDecimal min,BigDecimal max) {
        if(value==null || value.compareTo(min)<0 || value.compareTo(max)>0 || value.stripTrailingZeros().scale()>2) {
            throw new DomainValidationException(label+" must be between "+min+" and "+max+", with at most two decimal places.");
        }
        return value;
    }
    public AssessmentCategory saveCategory(long offering,Long id,String name,BigDecimal weight) {
        offering(offering); name=AcademicValidation.text(name,"Category name",100,true);
        range(weight,"Category weight",BigDecimal.ZERO,new BigDecimal("100"));
        return repository.saveCategory(offering,id,name,weight,AcademicValidation.now(clock));
    }
    public void deleteCategory(long offering,long id) {
        offering(offering);
        if(repository.assessments(offering).stream().anyMatch(a->a.categoryId()==id)) { throw new DomainValidationException("Move or delete the category's assessments before deleting this category."); }
        repository.deleteCategory(offering,id);
    }
    public Assessment saveAssessment(long offering,Long id,long category,String title,BigDecimal maximum,LocalDate date) {
        offering(offering); title=AcademicValidation.text(title,"Assessment title",200,true);
        range(maximum,"Maximum marks",new BigDecimal("0.01"),new BigDecimal("100000"));
        if(date==null || date.getYear()<1 || date.getYear()>9999) { throw new DomainValidationException("Assessment date is required (years 0001–9999)."); }
        if(repository.categories(offering).stream().noneMatch(c->c.id()==category)) { throw new DomainValidationException("Choose a category from this offering."); }
        if(id!=null && repository.grades(offering).stream().anyMatch(g->g.key().assessmentId()==id && g.score().compareTo(maximum)>0)) {
            throw new DomainValidationException("Maximum marks cannot be below an existing score. Correct the grades first.");
        }
        return repository.saveAssessment(offering,id,category,title,maximum,date,AcademicValidation.now(clock));
    }
    public void deleteAssessment(long offering,long id) { offering(offering); repository.deleteAssessment(offering,id); }
    public void saveScale(long offering,GradingScale scale) {
        offering(offering);
        if(scale==null) { throw new DomainValidationException("Grading scale is required."); }
        for(var value:Arrays.asList(scale.a(),scale.b(),scale.c(),scale.d(),scale.pass())) { range(value,"Scale threshold",BigDecimal.ZERO,new BigDecimal("100")); }
        if(scale.a().compareTo(scale.b())<=0 || scale.b().compareTo(scale.c())<=0 || scale.c().compareTo(scale.d())<=0) {
            throw new DomainValidationException("Letter thresholds must satisfy A > B > C > D. E uses the Pass minimum; F is below it.");
        }
        repository.saveScale(offering,scale);
    }
    public static BigDecimal validateScore(String value,Assessment assessment) {
        if(value==null || value.isBlank()) { return null; }
        return range(number(value,"Score"),"Score",BigDecimal.ZERO,assessment.maximumMarks());
    }
    public void saveGrades(long offering,Map<GradeKey,String> entries) {
        var book=load(offering); Map<GradeKey,BigDecimal> changes=new LinkedHashMap<>();
        for(var entry:entries.entrySet()) {
            var key=entry.getKey();
            if(book.students().stream().noneMatch(s->s.enrollment().id()==key.enrollmentId())) { throw new DomainValidationException("The enrollment does not belong to this offering."); }
            var assessment=book.assessments().stream().filter(a->a.id()==key.assessmentId()).findFirst().orElseThrow(()->new DomainValidationException("The assessment does not belong to this offering."));
            changes.put(key,validateScore(entry.getValue(),assessment));
        }
        repository.saveGrades(offering,changes,AcademicValidation.now(clock));
    }
    /** Bulk plain-text input: one student-number TAB score per line, blank score clears a cell. */
    public Map<GradeKey,String> parseBulk(Gradebook book,long assessmentId,String text) {
        var assessment=book.assessments().stream().filter(a->a.id()==assessmentId).findFirst().orElseThrow(()->new DomainValidationException("Choose an assessment."));
        Map<GradeKey,String> result=new LinkedHashMap<>();
        for(String line:text.split("\\R",-1)) {
            if(line.isBlank()) { continue; }
            String[] parts=line.split("\\t",-1);
            if(parts.length!=2) { throw new DomainValidationException("Each bulk line must contain student number, TAB, score."); }
            var student=book.students().stream().filter(s->s.student().studentNumber().equalsIgnoreCase(parts[0].strip())).findFirst().orElseThrow(()->new DomainValidationException("Unknown enrolled student: "+parts[0]));
            validateScore(parts[1],assessment);
            var key=new GradeKey(student.enrollment().id(),assessmentId);
            if(result.containsKey(key)) { throw new DomainValidationException("Student appears twice: "+parts[0]); }
            result.put(key,parts[1].strip());
        }
        if(result.isEmpty()) { throw new DomainValidationException("Enter at least one student and score."); }
        return result;
    }
}
