package com.professoros.model;
public record StudentImportRow(int rowNumber,StudentInput input,String section,Action action,String message,Student existing) {
    public enum Action { CREATE, ENROLL, SKIP, ERROR }
}
