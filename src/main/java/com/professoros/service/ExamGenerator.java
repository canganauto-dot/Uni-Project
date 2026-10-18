package com.professoros.service;

import com.professoros.model.*;
import java.util.*;

/** Deterministic bounded backtracking over unique questions with exact marginal quotas. */
public final class ExamGenerator {
    private final int nodeLimit;
    public ExamGenerator() { this(100_000); }
    ExamGenerator(int nodeLimit) { this.nodeLimit=nodeLimit; }
    public List<Question> generate(List<Question> bank, GenerationRequest request) {
        AcademicValidation.required(request,"Generation constraints");
        Integer count=request.questionCount();
        if(count==null || count<1 || count>100) { throw new DomainValidationException("Question count must be between 1 and 100."); }
        Map<Difficulty,Integer> difficulties=quotas(request.difficultyCounts(),count,"Difficulty");
        Map<QuestionType,Integer> types=quotas(request.typeCounts(),count,"Type");
        Map<String,Integer> normalizedTopics=new TreeMap<>();
        if(request.topicCounts()!=null) {
            request.topicCounts().forEach((key,value) -> {
                if(key==null) { throw new DomainValidationException("Topic names cannot be null. Use an empty label for untagged questions."); }
                if(normalizedTopics.putIfAbsent(topicKey(key),value)!=null) { throw new DomainValidationException("Topic labels must be distinct ignoring case."); }
            });
        }
        Map<String,Integer> topics=quotas(normalizedTopics,count,"Topic");
        final Map<String,Integer> topicQuotas=topics;
        List<Question> candidates=bank.stream()
                .filter(q -> (difficulties.isEmpty() || difficulties.getOrDefault(q.difficulty(),0)>0)
                        && (types.isEmpty() || types.getOrDefault(q.type(),0)>0)
                        && (topicQuotas.isEmpty() || topicQuotas.getOrDefault(topicKey(q.topic()),0)>0))
                .sorted(Comparator.comparingLong(Question::usageCount).thenComparingLong(Question::id)).toList();
        if(candidates.stream().map(Question::id).distinct().count()!=candidates.size()) {
            throw new DomainValidationException("The candidate bank contains duplicate question identities. Refresh and retry.");
        }
        if(candidates.size()<count) { throw new DomainValidationException("The bank cannot satisfy this request: only "+candidates.size()+" eligible questions for "+count+" requested."); }
        if(candidates.size()>1000) { throw new DomainValidationException("Generation supports up to 1000 eligible questions. Narrow the topic/type distributions or build manually."); }
        Search search=new Search(candidates,count,difficulties,types,topics,nodeLimit);
        if(!search.choose(0)) { throw new DomainValidationException("The bank cannot satisfy all requested counts together. Review overlapping difficulty, type and topic distributions."); }
        return List.copyOf(search.selected);
    }
    public static String topicKey(String topic) { return topic.strip().toLowerCase(Locale.ROOT); }
    private static <T> Map<T,Integer> quotas(Map<T,Integer> values,int count,String label) {
        if(values==null || values.isEmpty()) { return new LinkedHashMap<>(); }
        long sum=0;
        for(var entry:values.entrySet()) {
            if(entry.getKey()==null || entry.getValue()==null || entry.getValue()<0 || entry.getValue()>count) {
                throw new DomainValidationException(label+" counts must be whole numbers from 0 to "+count+".");
            }
            sum+=entry.getValue();
        }
        if(sum!=count) { throw new DomainValidationException(label+" counts must sum to the requested question count ("+count+")."); }
        return new LinkedHashMap<>(values);
    }
    private static final class Search {
        final List<Question> bank,selected=new ArrayList<>();
        final int count;
        final Map<Difficulty,Integer> difficulties;
        final Map<QuestionType,Integer> types;
        final Map<String,Integer> topics;
        int nodes;
        final int nodeLimit;
        Search(List<Question> bank,int count,Map<Difficulty,Integer> difficulties,Map<QuestionType,Integer> types,Map<String,Integer> topics,int nodeLimit) {
            this.bank=bank; this.count=count; this.difficulties=difficulties; this.types=types; this.topics=topics;
            this.nodeLimit=nodeLimit;
        }
        boolean choose(int start) {
            if(++nodes>nodeLimit) { throw new DomainValidationException("Generation search limit reached; feasibility is unknown. Simplify constraints or build manually. No exam was changed."); }
            int needed=count-selected.size();
            if(needed==0) { return true; }
            if(bank.size()-start<needed || !available(start)) { return false; }
            for(int i=start;i<=bank.size()-needed;i++) {
                Question q=bank.get(i);
                if(!fits(difficulties,q.difficulty()) || !fits(types,q.type()) || !fits(topics,topicKey(q.topic()))) { continue; }
                change(q,-1); selected.add(q);
                if(choose(i+1)) { return true; }
                selected.removeLast(); change(q,1);
            }
            return false;
        }
        boolean available(int start) {
            Map<Difficulty,Integer> ds=new EnumMap<>(Difficulty.class);
            Map<QuestionType,Integer> ts=new EnumMap<>(QuestionType.class);
            Map<String,Integer> ps=new HashMap<>();
            int eligible=0;
            for(int i=start;i<bank.size();i++) {
                Question q=bank.get(i); String topic=topicKey(q.topic());
                if(!fits(difficulties,q.difficulty()) || !fits(types,q.type()) || !fits(topics,topic)) { continue; }
                eligible++; ds.merge(q.difficulty(),1,Integer::sum); ts.merge(q.type(),1,Integer::sum); ps.merge(topic,1,Integer::sum);
            }
            return eligible>=count-selected.size() && enough(difficulties,ds) && enough(types,ts) && enough(topics,ps);
        }
        <T> boolean enough(Map<T,Integer> quotas,Map<T,Integer> available) {
            return quotas.entrySet().stream().allMatch(e -> available.getOrDefault(e.getKey(),0)>=e.getValue());
        }
        <T> boolean fits(Map<T,Integer> values,T key) { return values.isEmpty() || values.getOrDefault(key,0)>0; }
        <T> void adjust(Map<T,Integer> values,T key,int delta) { if(!values.isEmpty()) { values.compute(key,(k,n)->n+delta); } }
        void change(Question q,int delta) { adjust(difficulties,q.difficulty(),delta); adjust(types,q.type(),delta); adjust(topics,topicKey(q.topic()),delta); }
    }
}
