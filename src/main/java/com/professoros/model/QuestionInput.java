package com.professoros.model;
import java.math.BigDecimal;
import java.util.List;
/** Untrusted form input; usage and timestamps cannot be edited here. */
public record QuestionInput(Long courseId, QuestionType type, String text, String topic,
                            Difficulty difficulty, BigDecimal marks, String answer, String explanation,
                            String imagePath, List<AnswerChoice> choices) { }
