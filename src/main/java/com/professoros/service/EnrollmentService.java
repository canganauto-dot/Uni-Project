package com.professoros.service;

import com.professoros.model.*;
import com.professoros.repository.*;
import java.time.Clock;
import java.util.List;

public final class EnrollmentService {
    private final EnrollmentRepository repository;
    private final StudentRepository students;
    private final CourseOfferingRepository offerings;
    private final AcademicTermRepository terms;
    private final Clock clock;
    public EnrollmentService(EnrollmentRepository repository, StudentRepository students, CourseOfferingRepository offerings, AcademicTermRepository terms) {
        this(repository, students, offerings, terms, Clock.systemUTC());
    }
    public EnrollmentService(EnrollmentRepository repository, StudentRepository students, CourseOfferingRepository offerings, AcademicTermRepository terms, Clock clock) {
        this.repository = repository; this.students = students; this.offerings = offerings; this.terms = terms; this.clock = clock;
    }
    public Enrollment enroll(long studentId, long offeringId) {
        if (students.findById(studentId).isEmpty()) { throw new DomainValidationException("Select an existing student."); }
        if (repository.exists(studentId, offeringId)) {
            throw new DomainValidationException("This student already has an enrollment in this offering. Update its status instead.");
        }
        requireOpen(offeringId);
        return repository.enroll(studentId, offeringId, AcademicValidation.now(clock));
    }
    public Enrollment updateStatus(long id, EnrollmentStatus status) {
        AcademicValidation.required(status, "Enrollment status");
        Enrollment existing = repository.findById(id).orElseThrow(() -> AcademicValidation.missing("Enrollment"));
        if (status == EnrollmentStatus.ACTIVE && existing.status() != EnrollmentStatus.ACTIVE) { requireOpen(existing.courseOfferingId()); }
        return repository.updateStatus(id, status).orElseThrow(() -> AcademicValidation.missing("Enrollment"));
    }
    public List<EnrolledStudent> findStudentsForOffering(long id) { return repository.findStudentsForOffering(id); }
    public List<StudentOffering> findOfferingsForStudent(long id) { return repository.findOfferingsForStudent(id); }
    private void requireOpen(long id) {
        CourseOffering offering = offerings.findById(id).orElseThrow(() -> AcademicValidation.missing("Offering"));
        AcademicTerm term = terms.findById(offering.academicTermId()).orElseThrow(() -> AcademicValidation.missing("Term"));
        if (closed(offering.status()) || closed(term.status())) {
            throw new DomainValidationException("New or reactivated enrollment requires a planned or active offering and term.");
        }
    }
    private static boolean closed(AcademicStatus status) { return status == AcademicStatus.COMPLETED || status == AcademicStatus.ARCHIVED; }
}
