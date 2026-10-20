package com.professoros.service;
import com.professoros.model.*;
import java.math.*;
import java.util.*;
/** Present and Late receive full credit. Excused, unmarked and cancelled lectures are excluded. */
public final class AttendanceCalculator {
    public static final String POLICY="Attendance % = (Present + Late) / (Present + Late + Absent). Excused, unmarked and cancelled lectures are excluded. Late counts fully and is reported separately.";
    private AttendanceCalculator() { }
    public static AttendanceSummary student(AttendanceBook book,long enrollment) { return calculate(book,Set.of(enrollment)); }
    public static AttendanceSummary course(AttendanceBook book) {
        return calculate(book,book.students().stream().filter(s->s.enrollment().status()!=EnrollmentStatus.WITHDRAWN).map(s->s.enrollment().id()).collect(java.util.stream.Collectors.toSet()));
    }
    private static AttendanceSummary calculate(AttendanceBook book,Set<Long> enrollments) {
        Set<Long> lectures=book.lectures().stream().filter(l->l.status()!=LectureStatus.CANCELLED).map(Lecture::id).collect(java.util.stream.Collectors.toSet());
        int p=0,a=0,l=0,e=0;
        for(var record:book.records()) if(enrollments.contains(record.enrollmentId()) && lectures.contains(record.lectureId())) {
            switch(record.status()) { case PRESENT->p++; case ABSENT->a++; case LATE->l++; case EXCUSED->e++; }
        }
        int denominator=p+a+l;
        return new AttendanceSummary(p,a,l,e,Math.toIntExact((long)enrollments.size()*lectures.size()-p-a-l-e),denominator==0?null:BigDecimal.valueOf(100L*(p+l)).divide(BigDecimal.valueOf(denominator),2,RoundingMode.HALF_UP));
    }
}
