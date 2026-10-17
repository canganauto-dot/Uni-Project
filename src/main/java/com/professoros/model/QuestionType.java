package com.professoros.model;
public enum QuestionType {
    MCQ("MCQ"), TRUE_FALSE("True / False"), SHORT_ANSWER("Short answer"), ESSAY("Essay"), CODING("Coding");
    private final String label;
    QuestionType(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
