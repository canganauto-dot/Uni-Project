package com.professoros.service;

import com.professoros.model.*;
import com.professoros.repository.*;
import java.time.Clock;
import java.util.List;

public final class CourseOfferingService {
    private final CourseOfferingRepository repository;
    private final CourseRepository courses;
    private final AcademicTermRepository terms;
    private final Clock clock;
    public CourseOfferingService(CourseOfferingRepository repository, CourseRepository courses, AcademicTermRepository terms) {
        this(repository, courses, terms, Clock.systemUTC());
    }
    public CourseOfferingService(CourseOfferingRepository repository, CourseRepository courses, AcademicTermRepository terms, Clock clock) {
        this.repository = repository; this.courses = courses; this.terms = terms; this.clock = clock;
    }
    public CourseOffering create(CourseOfferingInput input) {
        CourseOfferingInput valid = validate(input);
        duplicate(valid, 0);
        return repository.create(valid, AcademicValidation.now(clock));
    }
    public CourseOffering update(long id, CourseOfferingInput input) {
        CourseOffering existing = findById(id);
        CourseOfferingInput valid = validate(input);
        if ((existing.courseId() != valid.courseId() || existing.academicTermId() != valid.academicTermId())
                && (repository.hasEnrollments(id) || repository.hasLectures(id) || repository.hasExams(id))) {
            throw new DomainValidationException("An offering with academic history cannot be moved to another course or term.");
        }
        duplicate(valid, id);
        return repository.update(id, valid, AcademicValidation.updated(clock, existing.updatedAt()))
                .orElseThrow(() -> AcademicValidation.missing("Offering"));
    }
    public void delete(long id) {
        if (repository.hasExams(id)) {
            throw new DomainValidationException("This offering has exams and cannot be deleted. Archive it instead.");
        }
        if (repository.hasLectures(id)) {
            throw new DomainValidationException("This offering has lectures and cannot be deleted. Archive it instead.");
        }
        if (repository.hasEnrollments(id)) {
            throw new DomainValidationException("This offering has enrollment history and cannot be deleted. Archive it instead.");
        }
        if (!repository.delete(id)) { throw AcademicValidation.missing("Offering"); }
    }
    public CourseOffering findById(long id) { return repository.findById(id).orElseThrow(() -> AcademicValidation.missing("Offering")); }
    public List<CourseOffering> findAll() { return repository.findAll(); }
    public List<CourseOffering> findByCourse(long id) { return repository.findByCourse(id); }
    public List<CourseOffering> findByTerm(long id) { return repository.findByTerm(id); }
    public List<OfferingSummary> summaries() { return repository.summaries(); }
    public OfferingSummary summary(long id) { return repository.summary(id).orElseThrow(() -> AcademicValidation.missing("Offering")); }
    private void duplicate(CourseOfferingInput input, long id) {
        if (repository.findByCourse(input.courseId()).stream().anyMatch(offering -> offering.id() != id
                && offering.academicTermId() == input.academicTermId() && offering.section().equalsIgnoreCase(input.section()))) {
            throw new DomainValidationException("An offering for this course, term and section already exists.");
        }
    }
    private CourseOfferingInput validate(CourseOfferingInput input) {
        AcademicValidation.required(input, "Offering details");
        if (courses.findById(input.courseId()).isEmpty()) { throw new DomainValidationException("Select an existing course."); }
        if (terms.findById(input.academicTermId()).isEmpty()) { throw new DomainValidationException("Select an existing academic term."); }
        return new CourseOfferingInput(input.courseId(), input.academicTermId(),
                AcademicValidation.identifier(input.section(), "Section"),
                AcademicValidation.text(input.notes(), "Notes", 5000, false), AcademicValidation.required(input.status(), "Status"));
    }
}
