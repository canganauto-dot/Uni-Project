package com.professoros.model;
import java.util.Map;
/** Empty distribution = unrestricted. Otherwise exact counts; omitted categories mean zero. */
public record GenerationRequest(Integer questionCount, Map<Difficulty,Integer> difficultyCounts,
                                Map<QuestionType,Integer> typeCounts, Map<String,Integer> topicCounts) { }
