package com.professoros.service;

import com.professoros.model.*;
import com.professoros.repository.StudentRepository;
import java.time.Clock;
import java.util.List;

public final class StudentService {
    private final StudentRepository repository;
    private final Clock clock;
    public StudentService(StudentRepository repository) { this(repository, Clock.systemUTC()); }
    public StudentService(StudentRepository repository, Clock clock) { this.repository = repository; this.clock = clock; }
    public Student create(StudentInput input) {
        StudentInput valid = validate(input);
        duplicate(valid.studentNumber(), 0);
        return repository.create(valid, AcademicValidation.now(clock));
    }
    public Student update(long id, StudentInput input) {
        Student existing = findById(id);
        StudentInput valid = validate(input);
        duplicate(valid.studentNumber(), id);
        return repository.update(id, valid, AcademicValidation.updated(clock, existing.updatedAt()))
                .orElseThrow(() -> AcademicValidation.missing("Student"));
    }
    public void delete(long id) {
        if (repository.hasEnrollments(id)) {
            throw new DomainValidationException("This student has enrollment history and cannot be deleted. Withdraw the enrollment if needed.");
        }
        if (!repository.delete(id)) { throw AcademicValidation.missing("Student"); }
    }
    public Student findById(long id) { return repository.findById(id).orElseThrow(() -> AcademicValidation.missing("Student")); }
    public List<Student> findAll() { return repository.findAll(); }
    public List<Student> search(String query) { return repository.search(query); }
    private void duplicate(String number, long id) {
        if (repository.findByStudentNumber(number).filter(student -> student.id() != id).isPresent()) {
            throw new DomainValidationException("Student number " + number + " already exists.");
        }
    }
    public static StudentInput validate(StudentInput input) {
        AcademicValidation.required(input, "Student details");
        String number = AcademicValidation.identifier(input.studentNumber(), "Student number");
        String first = AcademicValidation.text(input.firstName(), "First name", 100, true);
        String last = AcademicValidation.text(input.lastName(), "Last name", 100, true);
        String email = AcademicValidation.text(input.email(), "Email", 254, false);
        if (!email.isEmpty() && !email.matches("[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+")) {
            throw new DomainValidationException("Enter a valid email address, such as name@example.edu, or leave it blank.");
        }
        return new StudentInput(number, first, last, email, AcademicValidation.text(input.notes(), "Notes", 5000, false));
    }
}
