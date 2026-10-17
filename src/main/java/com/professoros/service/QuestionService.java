package com.professoros.service;

import com.professoros.model.*;
import com.professoros.repository.*;
import java.math.*;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;

public final class QuestionService {
    private final QuestionRepository repository;
    private final CourseRepository courses;
    private final Clock clock;
    public QuestionService(QuestionRepository repository, CourseRepository courses) {
        this(repository, courses, Clock.systemUTC());
    }
    public QuestionService(QuestionRepository repository, CourseRepository courses, Clock clock) {
        this.repository = repository; this.courses = courses; this.clock = clock;
    }
    public Question create(QuestionInput input) { return repository.create(validate(input), AcademicValidation.now(clock)); }
    public Question update(long id, QuestionInput input) {
        Question existing = findById(id);
        QuestionInput valid = validate(input);
        return repository.update(id, valid, AcademicValidation.updated(clock, existing.updatedAt()))
                .orElseThrow(() -> AcademicValidation.missing("Question"));
    }
    public void delete(long id) {
        if (!repository.delete(id)) { throw AcademicValidation.missing("Question"); }
    }
    public Question findById(long id) { return repository.findById(id).orElseThrow(() -> AcademicValidation.missing("Question")); }
    public List<Question> findAll() { return repository.findAll(); }
    public List<Question> search(String query, Long courseId, QuestionType type, Difficulty difficulty) {
        return repository.search(query, courseId, type, difficulty);
    }
    private QuestionInput validate(QuestionInput input) {
        AcademicValidation.required(input, "Question details");
        if (input.courseId() == null || courses.findById(input.courseId()).isEmpty()) {
            throw new DomainValidationException("Select an existing catalog course.");
        }
        QuestionType type = AcademicValidation.required(input.type(), "Question type");
        Difficulty difficulty = AcademicValidation.required(input.difficulty(), "Difficulty");
        String text = AcademicValidation.text(input.text(), "Question text", 20000, true);
        BigDecimal marks = input.marks();
        if (marks == null || marks.signum() <= 0 || marks.compareTo(new BigDecimal("1000")) > 0
                || marks.stripTrailingZeros().scale() > 2) {
            throw new DomainValidationException("Marks must be between 0.01 and 1000, with at most two decimal places.");
        }
        marks = marks.setScale(2, RoundingMode.UNNECESSARY);
        String answer = AcademicValidation.text(input.answer(), "Answer", 20000, false);
        if (type == QuestionType.TRUE_FALSE) {
            answer = answer.toUpperCase(Locale.ROOT);
            if (!answer.equals("TRUE") && !answer.equals("FALSE")) {
                throw new DomainValidationException("Choose TRUE or FALSE as the answer.");
            }
        }
        List<AnswerChoice> choices = input.choices();
        if (choices == null) { choices = List.of(); }
        List<AnswerChoice> normalized = new ArrayList<>();
        if (type == QuestionType.MCQ) {
            if (choices.size() < 2 || choices.size() > 10) {
                throw new DomainValidationException("MCQ questions require 2 to 10 answer choices.");
            }
            Set<String> seen = new HashSet<>();
            int correct = 0;
            for (AnswerChoice choice : choices) {
                AcademicValidation.required(choice, "Answer choice");
                String value = AcademicValidation.text(choice.text(), "Each answer choice", 2000, true);
                if (!seen.add(value.toLowerCase(Locale.ROOT))) { throw new DomainValidationException("Answer choices must be distinct."); }
                if (choice.correct()) { correct++; }
                normalized.add(new AnswerChoice(value, choice.correct()));
            }
            if (correct != 1) { throw new DomainValidationException("Select exactly one correct MCQ answer."); }
            answer = ""; // The selected choice is the single authoritative MCQ answer.
        } else if (!choices.isEmpty()) {
            throw new DomainValidationException("Only MCQ questions may contain answer choices.");
        }
        String imagePath = AcademicValidation.text(input.imagePath(), "Image path", 2000, false);
        if (!imagePath.isEmpty()) {
            try {
                if (!Path.of(imagePath).isAbsolute()) { throw new DomainValidationException("Use an absolute local image path."); }
            } catch (InvalidPathException invalid) { throw new DomainValidationException("Image path is not a valid local path."); }
        }
        return new QuestionInput(input.courseId(), type, text, AcademicValidation.text(input.topic(), "Topic", 200, false),
                difficulty, marks, answer, AcademicValidation.text(input.explanation(), "Explanation", 20000, false),
                imagePath, List.copyOf(normalized));
    }
}
