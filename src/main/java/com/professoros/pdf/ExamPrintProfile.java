package com.professoros.pdf;
/** Export-only institutional details; does not mutate the saved exam. */
public record ExamPrintProfile(String university, String department, String professor, String logoPath) { }
