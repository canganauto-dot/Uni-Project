package com.professoros.model;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public record Question(long id, long courseId, QuestionType type, String text, String topic,
                       Difficulty difficulty, BigDecimal marks, String answer, String explanation,
                       String imagePath, List<AnswerChoice> choices, long usageCount, LocalDate lastUsedDate,
                       Instant createdAt, Instant updatedAt) {
    public Question { choices = List.copyOf(choices); }
    public boolean matches(String query) {
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return List.of(text, topic, answer, explanation).stream().anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(needle))
                || choices.stream().anyMatch(choice -> choice.text().toLowerCase(Locale.ROOT).contains(needle));
    }
}
