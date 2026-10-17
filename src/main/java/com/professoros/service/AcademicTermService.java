package com.professoros.service;

import com.professoros.model.*;
import com.professoros.repository.AcademicTermRepository;
import java.time.Clock;
import java.util.List;

public final class AcademicTermService {
    private final AcademicTermRepository repository;
    private final Clock clock;
    public AcademicTermService(AcademicTermRepository repository) { this(repository, Clock.systemUTC()); }
    public AcademicTermService(AcademicTermRepository repository, Clock clock) { this.repository = repository; this.clock = clock; }

    public AcademicTerm create(AcademicTermInput input) {
        AcademicTermInput valid = validate(input);
        duplicate(valid, 0);
        return repository.create(valid, AcademicValidation.now(clock));
    }
    public AcademicTerm update(long id, AcademicTermInput input) {
        AcademicTerm existing = findById(id);
        AcademicTermInput valid = validate(input);
        duplicate(valid, id);
        return repository.update(id, valid, AcademicValidation.updated(clock, existing.updatedAt()))
                .orElseThrow(() -> AcademicValidation.missing("Term"));
    }
    public void delete(long id) {
        if (repository.hasOfferings(id)) {
            throw new DomainValidationException("This term has course offerings and cannot be deleted. Archive it to preserve history.");
        }
        if (!repository.delete(id)) { throw AcademicValidation.missing("Term"); }
    }
    public AcademicTerm findById(long id) { return repository.findById(id).orElseThrow(() -> AcademicValidation.missing("Term")); }
    public List<AcademicTerm> findAll() { return repository.findAll(); }
    private void duplicate(AcademicTermInput input, long id) {
        if (repository.findAll().stream().anyMatch(term -> term.id() != id && term.name().equalsIgnoreCase(input.name())
                && term.academicYear().equalsIgnoreCase(input.academicYear()))) {
            throw new DomainValidationException("A term with this name and academic year already exists.");
        }
    }
    private AcademicTermInput validate(AcademicTermInput input) {
        AcademicValidation.required(input, "Term details");
        String name = AcademicValidation.text(input.name(), "Term name", 80, true);
        String year = AcademicValidation.text(input.academicYear(), "Academic year", 40, true);
        var start = AcademicValidation.required(input.startDate(), "Start date");
        var end = AcademicValidation.required(input.endDate(), "End date");
        if (start.getYear() < 1 || end.getYear() > 9999 || !end.isAfter(start)) {
            throw new DomainValidationException("End date must be after start date, with years between 0001 and 9999.");
        }
        return new AcademicTermInput(name, year, start, end, AcademicValidation.required(input.status(), "Status"));
    }
}
