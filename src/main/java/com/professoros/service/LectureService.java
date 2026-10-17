package com.professoros.service;

import com.professoros.model.*;
import com.professoros.repository.*;
import java.time.*;
import java.util.List;

public final class LectureService {
    private final LectureRepository repository;
    private final CourseOfferingRepository offerings;
    private final Clock clock;
    public LectureService(LectureRepository repository, CourseOfferingRepository offerings) {
        this(repository, offerings, Clock.systemDefaultZone());
    }
    public LectureService(LectureRepository repository, CourseOfferingRepository offerings, Clock clock) {
        this.repository = repository; this.offerings = offerings; this.clock = clock;
    }
    public Lecture create(long offeringId, LectureInput input) {
        requireOffering(offeringId);
        LectureInput valid = validate(input);
        duplicate(offeringId, valid.lectureNumber(), 0);
        return repository.create(offeringId, valid, AcademicValidation.now(clock));
    }
    public Lecture update(long offeringId, long id, LectureInput input) {
        Lecture existing = findById(offeringId, id);
        LectureInput valid = validate(input);
        duplicate(offeringId, valid.lectureNumber(), id);
        return repository.update(offeringId, id, valid, AcademicValidation.updated(clock, existing.updatedAt()))
                .orElseThrow(() -> AcademicValidation.missing("Lecture in this offering"));
    }
    public void delete(long offeringId, long id) {
        requireOffering(offeringId);
        if (!repository.delete(offeringId, id)) { throw AcademicValidation.missing("Lecture in this offering"); }
    }
    public Lecture findById(long offeringId, long id) {
        requireOffering(offeringId);
        return repository.findById(offeringId, id).orElseThrow(() -> AcademicValidation.missing("Lecture in this offering"));
    }
    public List<Lecture> search(long offeringId, String query) {
        requireOffering(offeringId);
        return repository.search(offeringId, query);
    }
    public LectureOverview overview(long offeringId) {
        requireOffering(offeringId);
        List<Lecture> rows = repository.findByOffering(offeringId);
        LocalDate today = LocalDate.now(clock);
        int completed = 0, partial = 0, cancelled = 0, overdue = 0, due = 0;
        LocalDate next = null;
        for (Lecture lecture : rows) {
            switch (lecture.status()) {
                case COMPLETED -> completed++;
                case PARTIALLY_COMPLETED -> partial++;
                case CANCELLED -> cancelled++;
                default -> { }
            }
            if (lecture.status().unfinished()) {
                if (lecture.plannedDate().isBefore(today)) { overdue++; }
                else if (lecture.plannedDate().equals(today)) { due++; }
                if (!lecture.plannedDate().isBefore(today) && (next == null || lecture.plannedDate().isBefore(next))) {
                    next = lecture.plannedDate();
                }
            }
        }
        return new LectureOverview(rows, new LectureProgress(rows.size(), completed, partial, cancelled, overdue, due, next, today));
    }
    private void requireOffering(long id) {
        if (offerings.findById(id).isEmpty()) { throw new DomainValidationException("Select an existing course offering before managing lectures."); }
    }
    private void duplicate(long offeringId, int number, long exceptId) {
        if (repository.findByNumber(offeringId, number).filter(lecture -> lecture.id() != exceptId).isPresent()) {
            throw new DomainValidationException("Lecture number " + number + " already exists in this offering.");
        }
    }
    private static LectureInput validate(LectureInput input) {
        AcademicValidation.required(input, "Lecture details");
        Integer number = input.lectureNumber(), duration = input.durationMinutes();
        if (number == null || number < 1 || number > 9999) {
            throw new DomainValidationException("Lecture number must be a whole number between 1 and 9999.");
        }
        if (duration == null || duration < 1 || duration > 1440) {
            throw new DomainValidationException("Duration must be a whole number between 1 and 1440 minutes.");
        }
        String title = AcademicValidation.text(input.title(), "Title", 200, true);
        LocalDate date = AcademicValidation.required(input.plannedDate(), "A valid planned date (yyyy-MM-dd)");
        if (date.getYear() < 1 || date.getYear() > 9999) { throw new DomainValidationException("Planned date year must be between 0001 and 9999."); }
        return new LectureInput(number, title, date,
                AcademicValidation.text(input.topics(), "Topics", 5000, false),
                AcademicValidation.text(input.description(), "Description", 10000, false), duration,
                AcademicValidation.required(input.status(), "Lecture status"),
                AcademicValidation.text(input.professorNotes(), "Professor notes", 10000, false));
    }
}
