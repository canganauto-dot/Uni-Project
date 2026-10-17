package com.professoros.support;

import com.professoros.database.Database;
import com.professoros.model.*;
import com.professoros.repository.*;
import com.professoros.service.*;
import java.nio.file.Path;
import java.time.LocalDate;

public record LectureFixture(Database database, AcademicServices academic, Course course, AcademicTerm term,
                             CourseOffering first, CourseOffering second) {
    public static LectureFixture create(Path file) throws Exception {
        Database database = new Database(file); database.initialize();
        AcademicServices academic = AcademicServices.create(database);
        Course course = new CourseService(new CourseRepository(database)).create(new CourseInput("CS201", "Object Oriented Programming", "", 3, ""));
        AcademicTerm term = academic.terms().create(new AcademicTermInput("Fall", "2026–2027",
                LocalDate.of(2026,9,15), LocalDate.of(2027,1,20), AcademicStatus.ACTIVE));
        CourseOffering first = academic.offerings().create(new CourseOfferingInput(course.id(), term.id(), "A", "", AcademicStatus.ACTIVE));
        CourseOffering second = academic.offerings().create(new CourseOfferingInput(course.id(), term.id(), "B", "", AcademicStatus.ACTIVE));
        return new LectureFixture(database, academic, course, term, first, second);
    }
    public static LectureInput input(int number, LocalDate date, LectureStatus status) {
        return new LectureInput(number, "Lecture " + number, date, "Classes and objects", "Examples", 60, status, "Professor's notes");
    }
}
