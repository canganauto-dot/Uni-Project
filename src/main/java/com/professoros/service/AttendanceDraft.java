package com.professoros.service;
import com.professoros.model.*;
import java.util.*;
/** Per-lecture editing buffer, independent of JavaFX. Null means unmarked, never absent. */
public final class AttendanceDraft {
    private final AttendanceBook book;
    private final long lecture;
    private final Map<Long,AttendanceStatus> changes=new LinkedHashMap<>();
    public AttendanceDraft(AttendanceBook book,long lecture) { this.book=book;this.lecture=lecture; }
    public AttendanceStatus value(long enrollment) { return changes.containsKey(enrollment)?changes.get(enrollment):book.status(enrollment,lecture); }
    public void set(long enrollment,AttendanceStatus status) {
        if(book.students().stream().noneMatch(s->s.enrollment().id()==enrollment)) throw new DomainValidationException("Student is not in this roster.");
        if(status==book.status(enrollment,lecture)) changes.remove(enrollment); else changes.put(enrollment,status);
    }
    public Map<Long,AttendanceStatus> changes() { return Collections.unmodifiableMap(new LinkedHashMap<>(changes)); }
    public boolean dirty() { return !changes.isEmpty(); }
}
