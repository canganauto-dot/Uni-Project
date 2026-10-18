package com.professoros.service;

import com.professoros.model.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExamGeneratorTest {
    private static Question q(long id,Difficulty difficulty,QuestionType type,String topic) {
        return new Question(id,1,type,"Question "+id,topic,difficulty,BigDecimal.ONE,"","","",List.of(),0,null,Instant.EPOCH,Instant.EPOCH);
    }
    private static GenerationRequest request(int n,Map<Difficulty,Integer> d,Map<QuestionType,Integer> t,Map<String,Integer> topics) {
        return new GenerationRequest(n,d,t,topics);
    }
    @Test void deterministicSelectionIgnoresInputOrderAndNeverDuplicates() {
        var bank=List.of(q(3,Difficulty.HARD,QuestionType.ESSAY,"B"),q(1,Difficulty.EASY,QuestionType.MCQ,"A"),q(2,Difficulty.EASY,QuestionType.MCQ,"A"));
        var constraints=request(2,Map.of(),Map.of(),Map.of());
        var generator=new ExamGenerator();
        assertEquals(List.of(1L,2L),generator.generate(bank,constraints).stream().map(Question::id).toList());
        var reversed=new ArrayList<>(bank); Collections.reverse(reversed);
        assertEquals(generator.generate(bank,constraints),generator.generate(reversed,constraints));
    }
    @Test void backtracksPastAnEarlyQuestionThatWouldBlockTheJointSolution() {
        var bank=List.of(q(1,Difficulty.EASY,QuestionType.MCQ,"a"),
                q(2,Difficulty.EASY,QuestionType.ESSAY,"a"),q(3,Difficulty.HARD,QuestionType.MCQ,"b"));
        var result=new ExamGenerator().generate(bank,request(2,Map.of(Difficulty.EASY,1,Difficulty.HARD,1),
                Map.of(QuestionType.MCQ,1,QuestionType.ESSAY,1),Map.of(" A ",1,"B",1)));
        assertEquals(List.of(2L,3L),result.stream().map(Question::id).toList());
    }
    @Test void reportsJointInfeasibilityEvenWhenEachIndividualQuotaHasEnoughQuestions() {
        var bank=List.of(q(1,Difficulty.EASY,QuestionType.MCQ,"a"),q(2,Difficulty.EASY,QuestionType.ESSAY,"b"),
                q(3,Difficulty.HARD,QuestionType.MCQ,"b"),q(4,Difficulty.HARD,QuestionType.ESSAY,"a"));
        var failure=assertThrows(DomainValidationException.class,() -> new ExamGenerator().generate(bank,
                request(2,Map.of(Difficulty.EASY,1,Difficulty.HARD,1),Map.of(QuestionType.MCQ,1,QuestionType.ESSAY,1),Map.of("a",1,"b",1))));
        assertTrue(failure.getMessage().contains("together"));
    }
    @Test void validatesCountsSumsAndUnavailableTopics() {
        var bank=List.of(q(1,Difficulty.EASY,QuestionType.MCQ,""));
        for(Integer n:Arrays.asList(null,0,101)) {
            assertThrows(DomainValidationException.class,() -> new ExamGenerator().generate(bank,new GenerationRequest(n,null,null,null)));
        }
        assertThrows(DomainValidationException.class,() -> new ExamGenerator().generate(bank,null));
        assertThrows(DomainValidationException.class,() -> new ExamGenerator().generate(bank,request(1,Map.of(Difficulty.EASY,0),null,null)));
        assertThrows(DomainValidationException.class,() -> new ExamGenerator().generate(bank,request(1,null,Map.of(QuestionType.MCQ,-1),null)));
        assertThrows(DomainValidationException.class,() -> new ExamGenerator().generate(bank,request(1,null,null,Map.of("missing",1))));
        assertEquals(1,new ExamGenerator().generate(bank,request(1,null,null,Map.of("",1))).size());
        assertThrows(DomainValidationException.class,() -> new ExamGenerator().generate(bank,request(1,null,null,Map.of("a",1," A ",0))));
    }
    @Test void searchBudgetReportsUnknownRatherThanFalseInfeasibility() {
        var failure=assertThrows(DomainValidationException.class,() -> new ExamGenerator(1).generate(
                List.of(q(1,Difficulty.EASY,QuestionType.MCQ,"a")),request(1,null,null,null)));
        assertTrue(failure.getMessage().contains("feasibility is unknown"));
    }
    @Test void matchesAnIndependentBruteForceOracleAcrossAllSmallDistributions() {
        var bank=List.of(q(1,Difficulty.EASY,QuestionType.MCQ,"a"),q(2,Difficulty.EASY,QuestionType.ESSAY,"a"),
                q(3,Difficulty.HARD,QuestionType.MCQ,"b"),q(4,Difficulty.HARD,QuestionType.ESSAY,"a"),
                q(5,Difficulty.EASY,QuestionType.MCQ,"b"),q(6,Difficulty.HARD,QuestionType.ESSAY,"b"));
        for(int easy=0;easy<=3;easy++) for(int mcq=0;mcq<=3;mcq++) for(int topicA=0;topicA<=3;topicA++) {
            var r=request(3,Map.of(Difficulty.EASY,easy,Difficulty.HARD,3-easy),
                    Map.of(QuestionType.MCQ,mcq,QuestionType.ESSAY,3-mcq),Map.of("a",topicA,"b",3-topicA));
            boolean possible=false;
            for(int mask=0;mask<(1<<bank.size());mask++) {
                List<Question> subset=new ArrayList<>();
                for(int i=0;i<bank.size();i++) { if((mask&(1<<i))!=0) { subset.add(bank.get(i)); } }
                if(subset.size()==3 && matches(subset,r)) { possible=true; break; }
            }
            if(possible) { var result=new ExamGenerator().generate(bank,r); assertEquals(3,result.size()); assertTrue(matches(result,r)); }
            else { assertThrows(DomainValidationException.class,() -> new ExamGenerator().generate(bank,r)); }
        }
    }
    private static boolean matches(List<Question> rows,GenerationRequest request) {
        return request.difficultyCounts().entrySet().stream().allMatch(e -> rows.stream().filter(q -> q.difficulty()==e.getKey()).count()==e.getValue())
                && request.typeCounts().entrySet().stream().allMatch(e -> rows.stream().filter(q -> q.type()==e.getKey()).count()==e.getValue())
                && request.topicCounts().entrySet().stream().allMatch(e -> rows.stream().filter(q -> q.topic().equals(e.getKey())).count()==e.getValue());
    }
}
