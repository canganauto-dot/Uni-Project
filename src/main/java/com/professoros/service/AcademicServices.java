package com.professoros.service;

import com.professoros.database.Database;
import com.professoros.repository.*;

/** Explicit composition of the academic services; shared by application startup and desktop tests. */
public record AcademicServices(AcademicTermService terms, CourseOfferingService offerings,
                               StudentService students, EnrollmentService enrollments, LectureService lectures) {
    public static AcademicServices create(Database database) {
        var terms = new AcademicTermRepository(database);
        var offerings = new CourseOfferingRepository(database);
        var students = new StudentRepository(database);
        return new AcademicServices(new AcademicTermService(terms),
                new CourseOfferingService(offerings, new CourseRepository(database), terms),
                new StudentService(students), new EnrollmentService(new EnrollmentRepository(database), students, offerings, terms),
                new LectureService(new LectureRepository(database), offerings));
    }
}
