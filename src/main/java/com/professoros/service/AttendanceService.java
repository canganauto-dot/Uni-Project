package com.professoros.service;
import com.professoros.model.*;
import com.professoros.repository.*;
import java.time.*;
import java.util.*;
public final class AttendanceService {
    private final AttendanceRepository repository;
    private final LectureRepository lectures;
    private final EnrollmentRepository enrollments;
    private final CourseOfferingRepository offerings;
    public AttendanceService(AttendanceRepository repository,LectureRepository lectures,EnrollmentRepository enrollments,CourseOfferingRepository offerings) {
        this.repository=repository;this.lectures=lectures;this.enrollments=enrollments;this.offerings=offerings;
    }
    public AttendanceBook load(long offering) {
        requireOffering(offering);
        return new AttendanceBook(offering,lectures.findByOffering(offering),enrollments.findStudentsForOffering(offering),repository.findByOffering(offering));
    }
    public void save(long offering,long lecture,Map<Long,AttendanceStatus> changes) {
        requireOffering(offering);
        var selected=lectures.findById(offering,lecture).orElseThrow(()->new DomainValidationException("Choose a lecture in this offering."));
        if(selected.status()==LectureStatus.CANCELLED) throw new DomainValidationException("Cancelled lectures are read-only. Restore the lecture before editing attendance.");
        if(changes==null) throw new DomainValidationException("Attendance changes are required.");
        var copy=new LinkedHashMap<Long,AttendanceStatus>(changes);
        for(Long id:copy.keySet()) if(id==null || enrollments.findById(id).filter(e->e.courseOfferingId()==offering).isEmpty()) throw new DomainValidationException("Every student must be enrolled in the lecture's offering. Refresh the roster.");
        repository.save(offering,lecture,copy,Instant.now());
    }
    private void requireOffering(long offering) { if(offerings.findById(offering).isEmpty()) throw new DomainValidationException("Course offering no longer exists. Refresh and select an offering."); }
}
