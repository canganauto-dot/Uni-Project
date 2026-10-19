package com.professoros.model;
public record StudentImportResult(int created,int enrolled,int skipped,int failed) {
    @Override public String toString() { return "Created: "+created+" · Enrolled: "+enrolled+" · Skipped: "+skipped+" · Failed validation: "+failed; }
}
