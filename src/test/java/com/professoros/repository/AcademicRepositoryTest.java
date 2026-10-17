package com.professoros.repository;

import com.professoros.database.Database;
import com.professoros.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class AcademicRepositoryTest {
    @TempDir Path directory;
    Database database;
    CourseRepository courses;
    AcademicTermRepository terms;
    CourseOfferingRepository offerings;
    StudentRepository students;
    EnrollmentRepository enrollments;
    final Instant now = Instant.parse("2026-10-16T10:00:00Z");
    Course course; AcademicTerm term; CourseOffering offering; Student student;
    @BeforeEach void setup() throws Exception {
        database = new Database(directory.resolve("academic.db")); database.initialize();
        courses = new CourseRepository(database); terms = new AcademicTermRepository(database);
        offerings = new CourseOfferingRepository(database); students = new StudentRepository(database); enrollments = new EnrollmentRepository(database);
        course = courses.create(new CourseInput("CS201", "Object Oriented Programming", "", 3, ""), now);
        term = terms.create(new AcademicTermInput("Fall", "2026–2027", LocalDate.of(2026,9,15), LocalDate.of(2027,1,20), AcademicStatus.ACTIVE), now);
        offering = offerings.create(new CourseOfferingInput(course.id(), term.id(), "A", "", AcademicStatus.ACTIVE), now);
        student = students.create(new StudentInput("2026001", "Ahmed", "Ali", "Ahmed@example.edu", ""), now);
    }
    @Test void termCreateUpdateReadAndDeletePreserveIdentityAndDates() {
        var summer = terms.create(new AcademicTermInput("Summer", "2027", LocalDate.of(2027,6,1), LocalDate.of(2027,8,1), AcademicStatus.PLANNED), now);
        var updated = terms.update(summer.id(), new AcademicTermInput("Summer", "2027", summer.startDate(), summer.endDate(), AcademicStatus.ACTIVE), now.plusSeconds(5)).orElseThrow();
        assertEquals(summer.createdAt(), updated.createdAt()); assertEquals(AcademicStatus.ACTIVE, updated.status());
        assertEquals(updated, terms.findById(updated.id()).orElseThrow()); assertEquals(2, terms.findAll().size());
        assertTrue(terms.delete(updated.id())); assertFalse(terms.delete(updated.id()));
    }
    @Test void offeringsAreUniquePerCourseTermSectionAndLookupsJoinCatalog() {
        assertThrows(DomainValidationException.class, () -> offerings.create(new CourseOfferingInput(course.id(), term.id(), "a", "", AcademicStatus.ACTIVE), now));
        var sectionB = offerings.create(new CourseOfferingInput(course.id(), term.id(), "B", "", AcademicStatus.PLANNED), now);
        assertEquals(2, offerings.findByCourse(course.id()).size()); assertEquals(2, offerings.findByTerm(term.id()).size());
        var changed = offerings.update(sectionB.id(), new CourseOfferingInput(course.id(), term.id(), "C", "Monday", AcademicStatus.ACTIVE), now.plusSeconds(5)).orElseThrow();
        assertEquals("C", changed.section()); assertEquals("Monday", changed.notes()); assertEquals(sectionB.createdAt(), changed.createdAt());
        courses.update(course.id(), new CourseInput("CS201", "Renamed catalog course", "", 3, ""), now);
        assertEquals("Renamed catalog course", offerings.summary(offering.id()).orElseThrow().courseName());
        assertTrue(offerings.delete(sectionB.id()));
    }
    @Test void studentCrudSearchAndUniqueness() {
        assertEquals(student, students.findByStudentNumber("2026001").orElseThrow());
        assertThrows(DomainValidationException.class, () -> students.create(new StudentInput("2026001","Other","Name","",""), now));
        for (String query : new String[]{"2026001", "AHMED", "ali", "Ahmed Ali", "EXAMPLE.EDU"}) {
            assertEquals(java.util.List.of(student), students.search(query), query);
        }
        assertTrue(students.search("' OR 1=1 --").isEmpty());
        var updated = students.update(student.id(), new StudentInput("2026003", "Sara", "Hassan", "", "Changed"), now.plusSeconds(1)).orElseThrow();
        assertEquals(student.createdAt(), updated.createdAt()); assertEquals("Sara Hassan", updated.fullName());
        assertTrue(students.delete(student.id())); assertTrue(students.findAll().isEmpty());
    }
    @Test void enrollmentListsBothDirectionsAndRetainsWithdrawnHistory() {
        var enrolled = enrollments.enroll(student.id(), offering.id(), now);
        assertTrue(enrollments.exists(student.id(), offering.id()));
        assertThrows(DomainValidationException.class, () -> enrollments.enroll(student.id(), offering.id(), now));
        assertEquals(student, enrollments.findStudentsForOffering(offering.id()).getFirst().student());
        assertEquals(offering, enrollments.findOfferingsForStudent(student.id()).getFirst().offering().offering());
        var withdrawn = enrollments.updateStatus(enrolled.id(), EnrollmentStatus.WITHDRAWN).orElseThrow();
        assertEquals(enrolled.enrolledAt(), withdrawn.enrolledAt());
        assertEquals(EnrollmentStatus.WITHDRAWN, enrollments.findStudentsForOffering(offering.id()).getFirst().enrollment().status());
        assertThrows(DomainValidationException.class, () -> enrollments.enroll(student.id(), offering.id(), now));
    }
    @Test void foreignKeysRejectMissingParentsAndRestrictDeletionEvenWithoutServices() {
        assertThrows(DomainValidationException.class, () -> offerings.create(new CourseOfferingInput(999, term.id(), "X", "", AcademicStatus.ACTIVE), now));
        assertThrows(DomainValidationException.class, () -> offerings.create(new CourseOfferingInput(course.id(), 999, "X", "", AcademicStatus.ACTIVE), now));
        assertThrows(DomainValidationException.class, () -> enrollments.enroll(999, offering.id(), now));
        assertThrows(DomainValidationException.class, () -> enrollments.enroll(student.id(), 999, now));
        assertThrows(CourseValidationException.class, () -> courses.delete(course.id()));
        assertThrows(DomainValidationException.class, () -> terms.delete(term.id()));
        enrollments.enroll(student.id(), offering.id(), now);
        assertThrows(DomainValidationException.class, () -> students.delete(student.id()));
        assertThrows(DomainValidationException.class, () -> offerings.delete(offering.id()));
        assertEquals(1, enrollments.findStudentsForOffering(offering.id()).size());
    }
    @Test void databasePreventsMovingOfferingWithEnrollmentHistory() {
        enrollments.enroll(student.id(), offering.id(), now);
        var other = courses.create(new CourseInput("CS202", "Other", "", 3, ""), now);
        assertThrows(DomainValidationException.class, () -> offerings.update(offering.id(),
                new CourseOfferingInput(other.id(), term.id(), "A", "", AcademicStatus.ACTIVE), now));
        assertEquals(course.id(), offerings.findById(offering.id()).orElseThrow().courseId());
    }
    @Test void entireHierarchySurvivesIndependentDatabaseReopening() throws Exception {
        var enrolled = enrollments.enroll(student.id(), offering.id(), now);
        var reopened = new Database(directory.resolve("academic.db")); reopened.initialize();
        assertEquals(course, new CourseRepository(reopened).findById(course.id()).orElseThrow());
        assertEquals(term, new AcademicTermRepository(reopened).findById(term.id()).orElseThrow());
        assertEquals(student, new StudentRepository(reopened).findById(student.id()).orElseThrow());
        assertEquals(offering, new CourseOfferingRepository(reopened).findById(offering.id()).orElseThrow());
        assertEquals(enrolled, new EnrollmentRepository(reopened).findById(enrolled.id()).orElseThrow());
    }
}
