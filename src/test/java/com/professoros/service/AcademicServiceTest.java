package com.professoros.service;

import com.professoros.database.Database;
import com.professoros.model.*;
import com.professoros.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class AcademicServiceTest {
    @TempDir Path directory;
    AcademicServices services;
    CourseService courses;
    AcademicTerm term; Course course; CourseOffering offering;
    @BeforeEach void setup() throws Exception {
        Database database = new Database(directory.resolve("services.db")); database.initialize();
        services = AcademicServices.create(database); courses = new CourseService(new CourseRepository(database));
        term = services.terms().create(new AcademicTermInput(" Fall ", " 2026–2027 ", LocalDate.of(2026,9,15), LocalDate.of(2027,1,20), AcademicStatus.ACTIVE));
        course = courses.create(new CourseInput("CS201", "Object Oriented Programming", "", 3, ""));
        offering = services.offerings().create(new CourseOfferingInput(course.id(), term.id(), " a ", "", AcademicStatus.ACTIVE));
    }
    @Test void normalizesTermAndOfferingAndRejectsDuplicates() {
        assertEquals("Fall", term.name()); assertEquals("2026–2027", term.academicYear()); assertEquals("A", offering.section());
        assertThrows(DomainValidationException.class, () -> services.terms().create(new AcademicTermInput("fall", "2026–2027", term.startDate(), term.endDate(), term.status())));
        assertThrows(DomainValidationException.class, () -> services.offerings().create(new CourseOfferingInput(course.id(), term.id(), "a", "", AcademicStatus.ACTIVE)));
        assertDoesNotThrow(() -> services.offerings().create(new CourseOfferingInput(course.id(), term.id(), "B", "", AcademicStatus.ACTIVE)));
    }
    @Test void validatesTermRequiredFieldsAndDateOrder() {
        for (AcademicTermInput invalid : java.util.List.of(
                new AcademicTermInput("", "2026", term.startDate(), term.endDate(), term.status()),
                new AcademicTermInput("Fall", "", term.startDate(), term.endDate(), term.status()),
                new AcademicTermInput("Fall", "2026", null, term.endDate(), term.status()),
                new AcademicTermInput("Fall", "2026", term.startDate(), null, term.status()),
                new AcademicTermInput("Fall", "2026", term.endDate(), term.startDate(), term.status()),
                new AcademicTermInput("Fall", "2026", term.startDate(), term.startDate(), term.status()),
                new AcademicTermInput("Fall", "2026", term.startDate(), term.endDate(), null))) {
            assertThrows(DomainValidationException.class, () -> services.terms().create(invalid));
        }
    }
    @Test void validatesOfferingRelationshipsSectionAndStatus() {
        for (CourseOfferingInput invalid : java.util.List.of(
                new CourseOfferingInput(999, term.id(), "A", "", AcademicStatus.PLANNED),
                new CourseOfferingInput(course.id(), 999, "A", "", AcademicStatus.PLANNED),
                new CourseOfferingInput(course.id(), term.id(), " ", "", AcademicStatus.PLANNED),
                new CourseOfferingInput(course.id(), term.id(), "A", "", null))) {
            assertThrows(DomainValidationException.class, () -> services.offerings().create(invalid));
        }
    }
    @Test void validatesRequiredStudentFieldsAndNormalizesOptionalText() {
        for (StudentInput invalid : java.util.List.of(new StudentInput("", "Ahmed","Ali","",""),
                new StudentInput("1","","Ali","",""), new StudentInput("1","Ahmed","","",""))) {
            assertThrows(DomainValidationException.class, () -> services.students().create(invalid));
        }
        Student student = services.students().create(new StudentInput(" ab-1 ", " Ahmed ", " Ali ", null, null));
        assertEquals("AB-1", student.studentNumber()); assertEquals("Ahmed Ali", student.fullName());
        assertEquals("", student.email()); assertEquals("", student.notes());
        assertThrows(DomainValidationException.class, () -> services.students().create(new StudentInput("ab-1", "Other","Name","","")));
        var edited = services.students().update(student.id(), new StudentInput("ab-1","Ahmed","Ali","name+class@example.edu",""));
        assertEquals(student.createdAt(), edited.createdAt());
    }
    @ParameterizedTest @ValueSource(strings={"missing-at", "a@@b.edu", "a b@x.edu", "a@.edu", "a@b."})
    void rejectsMalformedEmail(String email) {
        assertThrows(DomainValidationException.class, () -> services.students().create(new StudentInput("1","Ahmed","Ali",email,"")));
    }
    @Test void rejectsDuplicateEnrollmentIncludingWithdrawnAndAllowsReactivation() {
        Student student = services.students().create(new StudentInput("2026001","Ahmed","Ali","",""));
        Enrollment enrollment = services.enrollments().enroll(student.id(), offering.id());
        assertThrows(DomainValidationException.class, () -> services.enrollments().enroll(student.id(), offering.id()));
        services.enrollments().updateStatus(enrollment.id(), EnrollmentStatus.WITHDRAWN);
        assertThrows(DomainValidationException.class, () -> services.enrollments().enroll(student.id(), offering.id()));
        assertEquals(EnrollmentStatus.ACTIVE, services.enrollments().updateStatus(enrollment.id(), EnrollmentStatus.ACTIVE).status());
        assertThrows(DomainValidationException.class, () -> services.enrollments().enroll(999, offering.id()));
        assertThrows(DomainValidationException.class, () -> services.enrollments().enroll(student.id(), 999));
    }
    @Test void preventsDeletionAndMovingAnOfferingWithAcademicHistory() {
        Student student = services.students().create(new StudentInput("2026001","Ahmed","Ali","",""));
        Enrollment enrollment = services.enrollments().enroll(student.id(), offering.id());
        services.enrollments().updateStatus(enrollment.id(), EnrollmentStatus.WITHDRAWN);
        assertThrows(DomainValidationException.class, () -> courses.delete(course.id()));
        assertThrows(DomainValidationException.class, () -> services.terms().delete(term.id()));
        assertThrows(DomainValidationException.class, () -> services.students().delete(student.id()));
        assertThrows(DomainValidationException.class, () -> services.offerings().delete(offering.id()));
        Course other = courses.create(new CourseInput("CS202","Other","",3,""));
        assertThrows(DomainValidationException.class, () -> services.offerings().update(offering.id(),
                new CourseOfferingInput(other.id(), term.id(), "A","",AcademicStatus.ACTIVE)));
    }
    @Test void closedTermsAndOfferingsBlockNewAndReactivatedEnrollment() {
        Student student = services.students().create(new StudentInput("1","Ahmed","Ali","",""));
        Enrollment enrollment = services.enrollments().enroll(student.id(), offering.id());
        services.enrollments().updateStatus(enrollment.id(), EnrollmentStatus.WITHDRAWN);
        services.terms().update(term.id(), new AcademicTermInput(term.name(), term.academicYear(),term.startDate(),term.endDate(),AcademicStatus.ARCHIVED));
        assertThrows(DomainValidationException.class, () -> services.enrollments().updateStatus(enrollment.id(),EnrollmentStatus.ACTIVE));
        Student second = services.students().create(new StudentInput("2","Sara","Hassan","",""));
        assertThrows(DomainValidationException.class, () -> services.enrollments().enroll(second.id(), offering.id()));
        services.terms().update(term.id(), new AcademicTermInput(term.name(), term.academicYear(),term.startDate(),term.endDate(),AcademicStatus.ACTIVE));
        services.offerings().update(offering.id(), new CourseOfferingInput(course.id(), term.id(), "A","",AcademicStatus.COMPLETED));
        assertThrows(DomainValidationException.class, () -> services.enrollments().enroll(second.id(), offering.id()));
    }
    @Test void sessionContextIsPerInstanceAndCanBeCleared() {
        SessionContext one = new SessionContext(), two = new SessionContext();
        one.select(services.offerings().summary(offering.id()));
        assertEquals(offering.id(), one.activeOffering().orElseThrow().offering().id());
        assertTrue(two.activeOffering().isEmpty());
        one.clear(); assertTrue(one.activeOffering().isEmpty());
    }
}
