package com.professoros.service;

import com.professoros.model.*;
import com.professoros.repository.*;
import java.math.*;
import java.time.*;
import java.util.*;

public final class ExamService {
    private final ExamRepository repository;
    private final CourseOfferingRepository offerings;
    private final QuestionRepository questions;
    private final Clock clock;
    private final ExamGenerator generator=new ExamGenerator();
    public ExamService(ExamRepository repository,CourseOfferingRepository offerings,QuestionRepository questions) {
        this(repository,offerings,questions,Clock.systemDefaultZone());
    }
    public ExamService(ExamRepository repository,CourseOfferingRepository offerings,QuestionRepository questions,Clock clock) {
        this.repository=repository; this.offerings=offerings; this.questions=questions; this.clock=clock;
    }
    public Exam save(long offeringId,Long id,ExamInput input,List<Long> questionIds) {
        return save(offeringId,id,input,questionIds,Map.of());
    }
    public Exam save(long offeringId,Long id,ExamInput input,List<Long> questionIds,Map<Long,BigDecimal> questionMarks) {
        offering(offeringId);
        AcademicValidation.required(input,"Exam details");
        String title=AcademicValidation.text(input.title(),"Exam title",200,true);
        LocalDate date=AcademicValidation.required(input.date(),"Exam date (yyyy-MM-dd)");
        if(date.getYear()<1 || date.getYear()>9999) { throw new DomainValidationException("Exam date year must be between 0001 and 9999."); }
        if(input.durationMinutes()==null || input.durationMinutes()<1 || input.durationMinutes()>1440) {
            throw new DomainValidationException("Duration must be a whole number from 1 to 1440 minutes.");
        }
        BigDecimal marks=input.intendedMarks();
        if(marks==null || marks.signum()<=0 || marks.compareTo(new BigDecimal("100000"))>0 || marks.stripTrailingZeros().scale()>2) {
            throw new DomainValidationException("Intended marks must be 0.01–100000, with at most two decimal places.");
        }
        if(questionIds==null || questionIds.size()>100 || questionIds.stream().anyMatch(Objects::isNull)
                || new HashSet<>(questionIds).size()!=questionIds.size()) {
            throw new DomainValidationException("Select at most 100 distinct questions; duplicates are not allowed.");
        }
        if(questionMarks==null || !questionIds.containsAll(questionMarks.keySet()))throw new DomainValidationException("Marks must belong to selected questions.");
        questionMarks.values().forEach(ExamService::validateQuestionMarks);
        return repository.save(offeringId,id,new ExamInput(title,date,input.durationMinutes(),marks.setScale(2),
                AcademicValidation.text(input.instructions(),"Instructions",20000,false)),List.copyOf(questionIds),Map.copyOf(questionMarks),AcademicValidation.now(clock));
    }
    public static BigDecimal validateQuestionMarks(BigDecimal value) {
        if(value==null || value.signum()<=0 || value.compareTo(new BigDecimal("100000"))>0 || value.stripTrailingZeros().scale()>2)
            throw new DomainValidationException("Question marks must be 0.01–100000, with at most two decimal places.");
        return value;
    }
    public List<Exam> findByOffering(long id) { offering(id); return repository.findByOffering(id); }
    public Exam findById(long offeringId,long id) {
        return repository.findById(offeringId,id).orElseThrow(() -> AcademicValidation.missing("Exam in this offering"));
    }
    public List<Question> bank(long offeringId) { return questions.search("",offering(offeringId).courseId(),null,null); }
    public List<Question> bankForExam(long offeringId,Long examId) {
        List<Question> bank=bank(offeringId);
        if(examId==null) { return bank; }
        Map<Long,ExamQuestion> snapshots=new HashMap<>();
        findById(offeringId,examId).questions().forEach(q -> snapshots.put(q.questionId(),q));
        return bank.stream().map(q -> {
            ExamQuestion saved=snapshots.get(q.id());
            return saved==null?q:new Question(q.id(),q.courseId(),saved.type(),saved.text(),saved.topic(),saved.difficulty(),
                    saved.marks(),saved.answer(),saved.explanation(),saved.imagePath(),saved.choices(),q.usageCount(),q.lastUsedDate(),q.createdAt(),q.updatedAt());
        }).toList();
    }
    public List<Question> generate(long offeringId,GenerationRequest request) { return generate(offeringId,null,request); }
    public List<Question> generate(long offeringId,Long examId,GenerationRequest request) {
        return generator.generate(bankForExam(offeringId,examId),request);
    }
    public void delete(long offeringId,long id) { repository.delete(offeringId,id); }
    public Exam finalizeExam(long offeringId,long id) { return repository.finalizeExam(offeringId,id,AcademicValidation.now(clock),LocalDate.now(clock)); }
    public Exam archive(long offeringId,long id) { return repository.archive(offeringId,id,AcademicValidation.now(clock)); }
    private CourseOffering offering(long id) { return offerings.findById(id).orElseThrow(() -> AcademicValidation.missing("Offering")); }
}
