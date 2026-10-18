package com.professoros.service;

import com.professoros.model.*;
import com.professoros.repository.*;
import com.professoros.support.LectureFixture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExamServiceTest {
    @TempDir Path directory;
    LectureFixture f; ExamService service; Question first,second;
    long offering;
    static ExamInput input() { return new ExamInput("Midterm",LocalDate.of(2026,11,1),90,new BigDecimal("5.00"),"Answer all questions."); }
    @BeforeEach void setup() throws Exception {
        f=LectureFixture.create(directory.resolve("exams.db")); offering=f.first().id(); service=f.academic().exams();
        first=f.academic().questions().create(question(f.course().id(),"First",Difficulty.EASY,new BigDecimal("2.50")));
        second=f.academic().questions().create(question(f.course().id(),"Second",Difficulty.HARD,new BigDecimal("2.50")));
    }
    private QuestionInput question(long courseId,String text,Difficulty difficulty,BigDecimal marks) {
        return new QuestionInput(courseId,QuestionType.MCQ,text,"OOP",difficulty,marks,"","Explanation","",
                List.of(new AnswerChoice("A",true),new AnswerChoice("B",false)));
    }
    @Test void manuallyBuildsReordersRemovesAndPersistsExactMarks() {
        var exam=service.save(offering,null,input(),List.of(first.id(),second.id()));
        assertEquals(new BigDecimal("5.00"),exam.calculatedMarks()); assertFalse(exam.marksDiffer());
        var reopened=new ExamRepository(f.database()).findById(offering,exam.id()).orElseThrow();
        assertEquals(exam,reopened);
        var reversed=service.save(offering,exam.id(),input(),List.of(second.id(),first.id()));
        assertEquals(List.of(second.id(),first.id()),reversed.questions().stream().map(ExamQuestion::questionId).toList());
        assertEquals(List.of(1,2),reversed.questions().stream().map(ExamQuestion::position).toList());
        var smaller=service.save(offering,exam.id(),input(),List.of(first.id()));
        assertTrue(smaller.marksDiffer()); assertEquals(new BigDecimal("2.50"),smaller.calculatedMarks());
        assertEquals(exam.createdAt(),smaller.createdAt());
        service.delete(offering,exam.id()); assertTrue(service.findByOffering(offering).isEmpty());
    }
    @Test void preservesSnapshotsWhenBankTextChoicesMarksAndDifficultyChange() {
        var exam=service.save(offering,null,input(),List.of(first.id()));
        f.academic().questions().update(first.id(),question(f.course().id(),"Changed",Difficulty.HARD,new BigDecimal("99")));
        var saved=service.save(offering,exam.id(),input(),List.of(first.id(),second.id()));
        assertEquals(exam.questions().getFirst(),saved.questions().getFirst());
        assertEquals("First",saved.questions().getFirst().text());
        var generated=service.generate(offering,exam.id(),new GenerationRequest(1,Map.of(Difficulty.EASY,1),null,null));
        assertEquals(first.id(),generated.getFirst().id()); assertEquals(Difficulty.EASY,generated.getFirst().difficulty());
        assertEquals(0,f.academic().questions().findById(first.id()).usageCount());
    }
    @Test void rejectsCrossCourseAndWrongOfferingOperationsWithoutChangingData() {
        var course=new CourseService(new CourseRepository(f.database())).create(new CourseInput("CS202","Other","",3,""));
        var other=f.academic().questions().create(question(course.id(),"Other",Difficulty.EASY,BigDecimal.ONE));
        var exam=service.save(offering,null,input(),List.of(first.id()));
        assertThrows(DomainValidationException.class,() -> service.save(offering,exam.id(),input(),List.of(other.id())));
        assertEquals(exam,service.findById(offering,exam.id()));
        assertThrows(DomainValidationException.class,() -> service.findById(f.second().id(),exam.id()));
        assertThrows(DomainValidationException.class,() -> service.delete(f.second().id(),exam.id()));
        assertThrows(DomainValidationException.class,() -> service.save(f.second().id(),exam.id(),input(),List.of(first.id())));
        assertTrue(service.bank(offering).stream().noneMatch(q -> q.id()==other.id()));
        assertThrows(DomainValidationException.class,() -> f.academic().questions().update(first.id(),question(course.id(),"Moved",Difficulty.EASY,BigDecimal.ONE)));
        assertThrows(DomainValidationException.class,() -> f.academic().questions().delete(first.id()));
        assertThrows(DomainValidationException.class,() -> f.academic().offerings().delete(offering));
        assertThrows(DomainValidationException.class,() -> f.academic().offerings().update(offering,
                new CourseOfferingInput(course.id(),f.term().id(),"A","",AcademicStatus.ACTIVE)));
    }
    @Test void finalizationRecordsUsageExactlyOnceAndLocksContent() {
        var exam=service.save(offering,null,input(),List.of(first.id(),second.id()));
        var finalExam=service.finalizeExam(offering,exam.id());
        assertEquals(ExamStatus.FINALIZED,finalExam.status());
        assertEquals(1,f.academic().questions().findById(first.id()).usageCount());
        assertNotNull(f.academic().questions().findById(first.id()).lastUsedDate());
        assertThrows(DomainValidationException.class,() -> service.finalizeExam(offering,exam.id()));
        assertThrows(DomainValidationException.class,() -> service.save(offering,exam.id(),input(),List.of(first.id())));
        assertThrows(DomainValidationException.class,() -> service.delete(offering,exam.id()));
        assertEquals(1,f.academic().questions().findById(first.id()).usageCount());
        assertEquals(ExamStatus.ARCHIVED,service.archive(offering,exam.id()).status());
        assertEquals(2,service.findById(offering,exam.id()).questions().size());
    }
    @Test void emptyDraftAllowedButCannotFinalizeAndFailedGenerationDoesNotWrite() {
        var exam=service.save(offering,null,input(),List.of());
        assertTrue(exam.marksDiffer());
        assertThrows(DomainValidationException.class,() -> service.finalizeExam(offering,exam.id()));
        assertThrows(DomainValidationException.class,() -> service.generate(offering,new GenerationRequest(3,null,null,null)));
        assertEquals(exam,service.findById(offering,exam.id()));
        var generated=service.generate(offering,new GenerationRequest(2,Map.of(Difficulty.EASY,1,Difficulty.HARD,1),Map.of(QuestionType.MCQ,2),Map.of("oop",2)));
        var built=service.save(offering,exam.id(),input(),generated.stream().map(Question::id).toList());
        assertEquals(2,built.questions().size()); assertFalse(built.marksDiffer());
    }
    @Test void duplicateInsertRollsBackHeaderAndSnapshotReplacement() {
        var exam=service.save(offering,null,input(),List.of(first.id(),second.id()));
        var changed=new ExamInput("Must roll back",input().date(),10,BigDecimal.ONE,"");
        assertThrows(DomainValidationException.class,() -> new ExamRepository(f.database()).save(offering,exam.id(),changed,List.of(first.id(),first.id()),Instant.now()));
        assertEquals(exam,service.findById(offering,exam.id()));
    }
    @Test void databaseRejectsCrossCourseRelationshipAndInvalidExamHeader() throws Exception {
        var exam=service.save(offering,null,input(),List.of(first.id()));
        var course=new CourseService(new CourseRepository(f.database())).create(new CourseInput("CS202","Other","",3,""));
        var other=f.academic().questions().create(question(course.id(),"Other",Difficulty.EASY,BigDecimal.ONE));
        try(var c=f.database().openConnection();var s=c.prepareStatement("UPDATE exam_questions SET question_id=? WHERE exam_id=?")) {
            s.setLong(1,other.id()); s.setLong(2,exam.id()); assertThrows(SQLException.class,s::executeUpdate);
        }
        try(var c=f.database().openConnection();var s=c.createStatement()) {
            assertThrows(SQLException.class,() -> s.executeUpdate("UPDATE exams SET duration_minutes=0"));
            assertThrows(SQLException.class,() -> s.executeUpdate("UPDATE exams SET course_offering_id="+f.second().id()));
        }
        assertEquals(exam,service.findById(offering,exam.id()));
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","100000.01","0.001"})
    void rejectsInvalidIntendedMarks(String value) {
        assertThrows(DomainValidationException.class,() -> service.save(offering,null,
                new ExamInput("Exam",input().date(),60,new BigDecimal(value),""),List.of()));
    }
    @Test void rejectsRequiredFieldsAndInvalidSelection() {
        assertThrows(DomainValidationException.class,() -> service.save(offering,null,null,List.of()));
        for(ExamInput invalid:List.of(new ExamInput(" ",input().date(),60,BigDecimal.ONE,""),
                new ExamInput("Exam",null,60,BigDecimal.ONE,""),new ExamInput("Exam",input().date(),0,BigDecimal.ONE,""),
                new ExamInput("Exam",input().date(),1441,BigDecimal.ONE,""),new ExamInput("Exam",input().date(),60,null,""))) {
            assertThrows(DomainValidationException.class,() -> service.save(offering,null,invalid,List.of()));
        }
        assertThrows(DomainValidationException.class,() -> service.save(offering,null,input(),List.of(first.id(),first.id())));
        assertThrows(DomainValidationException.class,() -> service.save(offering,null,input(),null));
        assertThrows(DomainValidationException.class,() -> service.save(offering,null,input(),List.of(999L)));
    }
}
