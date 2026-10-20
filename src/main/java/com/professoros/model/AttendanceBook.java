package com.professoros.model;
import java.util.List;
public record AttendanceBook(long offeringId,List<Lecture> lectures,List<EnrolledStudent> students,List<Attendance> records) {
    public AttendanceBook { lectures=List.copyOf(lectures);students=List.copyOf(students);records=List.copyOf(records); }
    public AttendanceStatus status(long enrollment,long lecture) {
        return records.stream().filter(a->a.enrollmentId()==enrollment && a.lectureId()==lecture).map(Attendance::status).findFirst().orElse(null);
    }
}
