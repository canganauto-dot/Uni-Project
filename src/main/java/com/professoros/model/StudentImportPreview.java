package com.professoros.model;
import java.util.List;
public record StudentImportPreview(Long offeringId,String section,List<StudentImportRow> rows) {
    public StudentImportPreview { rows=List.copyOf(rows); }
    public long actionable() { return rows.stream().filter(r->r.action()==StudentImportRow.Action.CREATE || r.action()==StudentImportRow.Action.ENROLL).count(); }
}
