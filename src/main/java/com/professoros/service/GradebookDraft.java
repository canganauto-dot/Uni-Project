package com.professoros.service;
import com.professoros.model.*;
import java.math.BigDecimal;
import java.util.*;
/** Session edits, reusable by UI and future import adapters. Blank means clear, never zero. */
public final class GradebookDraft {
    private final Gradebook book;
    private final Map<GradeKey,String> changes=new LinkedHashMap<>();
    private final Map<GradeKey,BigDecimal> original;
    public GradebookDraft(Gradebook book) { this.book=book; original=GradeCalculator.scores(book); }
    public Gradebook book() { return book; }
    public boolean dirty() { return !changes.isEmpty(); }
    public Map<GradeKey,String> changes() { return Map.copyOf(changes); }
    public String value(GradeKey key) { return changes.containsKey(key)?changes.get(key):original.containsKey(key)?original.get(key).toPlainString():""; }
    public void set(GradeKey key,String value) {
        String normalized=value==null?"":value.strip();
        String previous=original.containsKey(key)?original.get(key).toPlainString():"";
        if(normalized.equals(previous)) { changes.remove(key); } else { changes.put(key,normalized); }
    }
    public Map<GradeKey,BigDecimal> scores() {
        Map<GradeKey,BigDecimal> result=new HashMap<>(original);
        for(var entry:changes.entrySet()) {
            var assessment=book.assessments().stream().filter(a->a.id()==entry.getKey().assessmentId()).findFirst().orElseThrow();
            var score=GradebookService.validateScore(entry.getValue(),assessment);
            if(score==null) { result.remove(entry.getKey()); } else { result.put(entry.getKey(),score); }
        }
        return result;
    }
}
