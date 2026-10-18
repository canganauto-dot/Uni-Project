package com.professoros.pdf;
public enum PdfKind {
    STUDENT("Student Exam"), ANSWER_KEY("Answer Key");
    private final String label;
    PdfKind(String label) { this.label=label; }
    @Override public String toString() { return label; }
}
