package com.professoros.pdf;
import com.professoros.model.Exam;
/** Loaded saved exam plus catalog labels. Never contains a live question-bank repository. */
public record ExamPrintSource(Exam exam, String courseLabel, String department, String offeringLabel) { }
