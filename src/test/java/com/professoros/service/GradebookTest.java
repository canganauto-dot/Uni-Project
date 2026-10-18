package com.professoros.service;
import com.professoros.model.*;
import com.professoros.repository.*;
import com.professoros.support.LectureFixture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GradebookTest {
    @TempDir Path directory;
    LectureFixture f; GradebookService service; long offering,enrollment; AssessmentCategory category; Assessment assessment;
    static BigDecimal n(String s) { return new BigDecimal(s); }
    @BeforeEach void setup() throws Exception {
        f=LectureFixture.create(directory.resolve("grades.db")); service=f.academic().grades(); offering=f.first().id();
        var student=f.academic().students().create(new StudentInput("2026001","Ahmed","Ali","",""));
        enrollment=f.academic().enrollments().enroll(student.id(),offering).id();
        category=service.saveCategory(offering,null," Exams ",n("100"));
        assessment=service.saveAssessment(offering,null,category.id(),"Midterm",n("20"),LocalDate.of(2026,11,1));
    }
    GradeKey key() { return new GradeKey(enrollment,assessment.id()); }
    GradeResult result() { var b=service.load(offering); return GradeCalculator.calculate(b,enrollment,GradeCalculator.scores(b)); }
    @Test void persistsCreatesUpdatesAndClearsWithoutConfusingZeroWithMissing() throws Exception {
        assertEquals("Exams",category.name()); assertFalse(result().complete());
        service.saveGrades(offering,Map.of(key(),"0"));
        assertTrue(result().complete()); assertEquals("FAIL",result().outcome()); assertEquals(n("0.00"),result().weightedPoints());
        var before=service.load(offering).grades().getFirst();
        service.saveGrades(offering,Map.of(key(),"18.5"));
        var reopened=AcademicServices.create(new com.professoros.database.Database(directory.resolve("grades.db"))).grades().load(offering);
        assertEquals(n("18.50"),reopened.grades().getFirst().score()); assertEquals(before.createdAt(),reopened.grades().getFirst().createdAt());
        assertEquals("A",result().letter()); assertEquals(n("92.50"),result().weightedPoints());
        service.saveGrades(offering,Map.of(key(),""));
        assertTrue(service.load(offering).grades().isEmpty()); assertEquals("INCOMPLETE",result().outcome());
    }
    @Test void usesPointsWithinCategoryAndWeightsAcrossCategories() {
        service.saveCategory(offering,category.id(),"Exams",n("60"));
        var finalExam=service.saveAssessment(offering,null,category.id(),"Final",n("80"),LocalDate.of(2026,12,1));
        var homework=service.saveCategory(offering,null,"Assignments",n("40"));
        var assignment=service.saveAssessment(offering,null,homework.id(),"Project",n("10"),LocalDate.of(2026,12,2));
        service.saveGrades(offering,Map.of(key(),"10",new GradeKey(enrollment,finalExam.id()),"70",new GradeKey(enrollment,assignment.id()),"5"));
        assertEquals(n("68.00"),result().weightedPoints()); assertEquals("D",result().letter()); assertEquals("PASS",result().outcome());
    }
    @Test void partialScoresAndEmptyWeightedCategoriesRemainIncomplete() {
        service.saveGrades(offering,Map.of(key(),"20"));
        var second=service.saveAssessment(offering,null,category.id(),"Final",n("20"),LocalDate.of(2026,12,1));
        assertEquals(n("50.00"),result().weightedPoints()); assertFalse(result().complete()); assertEquals("—",result().letter());
        service.saveGrades(offering,Map.of(new GradeKey(enrollment,second.id()),"20"));
        service.saveCategory(offering,category.id(),"Exams",n("90")); service.saveCategory(offering,null,"Empty",n("10"));
        assertFalse(result().complete()); assertEquals(n("90.00"),result().weightedPoints());
    }
    @Test void weightsMustTotalHundredForFinalResultsAndZeroWeightDoesNotBlock() {
        service.saveGrades(offering,Map.of(key(),"20")); service.saveCategory(offering,category.id(),"Exams",n("99.99"));
        assertFalse(result().complete());
        service.saveCategory(offering,category.id(),"Exams",n("100")); service.saveCategory(offering,null,"Practice",BigDecimal.ZERO);
        assertTrue(result().complete());
        service.saveCategory(offering,null,"Extra",BigDecimal.ONE); assertFalse(result().complete());
    }
    @Test void gradingScalePersistsAndClassificationPrecedesRounding() {
        service.saveAssessment(offering,assessment.id(),category.id(),"Exam",n("1000"),assessment.date());
        service.saveGrades(offering,Map.of(key(),"899.99"));
        assertEquals(n("90.00"),result().weightedPoints()); assertEquals("B",result().letter());
        service.saveScale(offering,new GradingScale(n("85"),n("75"),n("65"),n("50"),n("95")));
        assertEquals("A",result().letter()); assertEquals("FAIL",result().outcome());
        assertEquals(n("85.00"),service.load(offering).scale().a());
    }
    @Test void validatesRangesDatesAndRequiredFields() {
        for(String invalid:List.of("-1","20.01","1.234","NaN","abc")) {
            assertThrows(DomainValidationException.class,()->service.saveGrades(offering,Map.of(key(),invalid)));
        }
        assertThrows(DomainValidationException.class,()->service.saveCategory(offering,null," ",n("100")));
        for(String invalid:List.of("-1","100.01","1.111")) { assertThrows(DomainValidationException.class,()->service.saveCategory(offering,null,"Bad",n(invalid))); }
        assertThrows(DomainValidationException.class,()->service.saveAssessment(offering,null,category.id(),"",n("10"),assessment.date()));
        assertThrows(DomainValidationException.class,()->service.saveAssessment(offering,null,category.id(),"X",n("0"),assessment.date()));
        assertThrows(DomainValidationException.class,()->service.saveAssessment(offering,null,category.id(),"X",n("10"),null));
        assertThrows(DomainValidationException.class,()->service.saveScale(offering,new GradingScale(n("90"),n("90"),n("70"),n("60"),n("60"))));
        assertThrows(DomainValidationException.class,()->service.saveCategory(offering,null,"exams",n("20")));
    }
    @Test void validatesRelationshipsAndPreservesOtherOffering() {
        var otherCategory=service.saveCategory(f.second().id(),null,"Exams",n("100"));
        var other=service.saveAssessment(f.second().id(),null,otherCategory.id(),"Exam",n("100"),assessment.date());
        assertThrows(DomainValidationException.class,()->service.saveGrades(offering,Map.of(new GradeKey(enrollment,other.id()),"10")));
        assertThrows(DomainValidationException.class,()->service.saveGrades(f.second().id(),Map.of(new GradeKey(enrollment,other.id()),"10")));
        assertThrows(DomainValidationException.class,()->service.saveAssessment(offering,null,otherCategory.id(),"Bad",n("10"),assessment.date()));
        assertThrows(DomainValidationException.class,()->service.saveCategory(f.second().id(),category.id(),"Bad",n("50")));
        assertTrue(service.load(offering).grades().isEmpty());
    }
    @Test void maximumCannotInvalidateGradesAndConfirmedAssessmentDeletionRemovesOwnedCells() {
        service.saveGrades(offering,Map.of(key(),"18"));
        assertThrows(DomainValidationException.class,()->service.saveAssessment(offering,assessment.id(),category.id(),"Exam",n("17"),assessment.date()));
        var updated=service.saveAssessment(offering,assessment.id(),category.id(),"Updated",n("30"),assessment.date().plusDays(1));
        assertEquals("Updated",updated.title()); assertEquals(assessment.createdAt(),updated.createdAt());
        assertThrows(DomainValidationException.class,()->service.deleteCategory(offering,category.id()));
        service.deleteAssessment(offering,assessment.id()); assertTrue(service.load(offering).grades().isEmpty());
        service.deleteCategory(offering,category.id()); assertTrue(service.load(offering).categories().isEmpty());
    }
    @Test void repositoryGuardsRangesAndCrossOfferingWritesAndRollsBackBatch() {
        var repository=new GradebookRepository(f.database());
        var second=service.saveAssessment(offering,null,category.id(),"Final",n("10"),assessment.date());
        Map<GradeKey,BigDecimal> batch=new LinkedHashMap<>(); batch.put(key(),n("10")); batch.put(new GradeKey(enrollment,second.id()),n("11"));
        assertThrows(DomainValidationException.class,()->repository.saveGrades(offering,batch,Instant.now()));
        assertTrue(repository.grades(offering).isEmpty());
        assertThrows(DomainValidationException.class,()->repository.saveGrades(f.second().id(),Map.of(new GradeKey(enrollment,second.id()),n("5")),Instant.now()));
        repository.saveGrades(offering,Map.of(key(),n("10")),Instant.now());
        assertThrows(DomainValidationException.class,()->repository.saveGrades(f.second().id(),Map.of(key(),n("5")),Instant.now()));
        assertEquals(n("10.00"),repository.grades(offering).getFirst().score());
        assertThrows(DomainValidationException.class,()->repository.saveAssessment(offering,assessment.id(),category.id(),"Lower",n("9"),assessment.date(),Instant.now()));
        assertThrows(DomainValidationException.class,()->f.academic().offerings().delete(offering));
    }
    @Test void bulkParsingIsAtomicAndSupportsClearAndRejectsDuplicatesUnknowns() {
        var b=service.load(offering);
        var entries=service.parseBulk(b,assessment.id(),"2026001\t0");
        service.saveGrades(offering,entries); assertTrue(result().complete());
        service.saveGrades(offering,service.parseBulk(b,assessment.id(),"2026001\t")); assertFalse(result().complete());
        assertThrows(DomainValidationException.class,()->service.parseBulk(b,assessment.id(),"2026001\t1\n2026001\t2"));
        assertThrows(DomainValidationException.class,()->service.parseBulk(b,assessment.id(),"unknown\t1"));
        assertThrows(DomainValidationException.class,()->service.parseBulk(b,assessment.id(),"2026001 1"));
        assertThrows(DomainValidationException.class,()->service.parseBulk(b,assessment.id(),"2026001\t99"));
    }
    @Test void draftPreservesInvalidEditsAndStatisticsExcludeWithdrawn() {
        var draft=new GradebookDraft(service.load(offering)); draft.set(key(),"bad"); assertTrue(draft.dirty());
        assertThrows(DomainValidationException.class,draft::scores);
        draft.set(key(),""); assertFalse(draft.dirty());
        draft.set(key(),"20"); assertTrue(draft.dirty()); assertEquals("20",draft.changes().get(key()));
        assertTrue(GradeCalculator.statistics(draft.book(),draft.scores()).contains("Mean: 100.00"));
        f.academic().enrollments().updateStatus(enrollment,EnrollmentStatus.WITHDRAWN);
        var b=service.load(offering); assertTrue(GradeCalculator.statistics(b,draft.scores()).contains("No complete results"));
    }
    @Test void timestampsAdvanceWithinASecondAndNeverMoveBackwards() {
        Instant start=Instant.parse("2026-10-18T00:00:00Z"),later=start.plusMillis(500);
        var repository=new GradebookRepository(f.database());
        var early=new GradebookService(repository,new CourseOfferingRepository(f.database()),new EnrollmentRepository(f.database()),Clock.fixed(start,ZoneOffset.UTC));
        var next=new GradebookService(repository,new CourseOfferingRepository(f.database()),new EnrollmentRepository(f.database()),Clock.fixed(later,ZoneOffset.UTC));
        var c=early.saveCategory(offering,null,"Timestamp",n("0"));
        var a=early.saveAssessment(offering,null,c.id(),"Practice",n("20"),assessment.date());
        var key=new GradeKey(enrollment,a.id()); early.saveGrades(offering,Map.of(key,"1"));
        next.saveCategory(offering,c.id(),"Timestamp",n("0")); next.saveAssessment(offering,a.id(),c.id(),"Practice",n("20"),a.date()); next.saveGrades(offering,Map.of(key,"2"));
        early.saveCategory(offering,c.id(),"Timestamp",n("0")); early.saveAssessment(offering,a.id(),c.id(),"Practice",n("20"),a.date()); early.saveGrades(offering,Map.of(key,"3"));
        assertEquals(later,repository.categories(offering).stream().filter(x->x.id()==c.id()).findFirst().orElseThrow().updatedAt());
        assertEquals(later,repository.assessments(offering).stream().filter(x->x.id()==a.id()).findFirst().orElseThrow().updatedAt());
        assertEquals(later,repository.grades(offering).getFirst().updatedAt()); assertEquals(start,repository.grades(offering).getFirst().createdAt());
    }
}
