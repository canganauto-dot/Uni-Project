package com.professoros.model;

/** Joined roster row, including withdrawn/completed history. */
public record EnrolledStudent(Enrollment enrollment, Student student) { }
