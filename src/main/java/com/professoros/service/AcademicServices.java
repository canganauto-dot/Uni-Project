package com.professoros.service;

import com.professoros.database.Database;
import com.professoros.repository.*;

/** Explicit composition of the academic services; shared by application startup and desktop tests. */
public record AcademicServices(AcademicTermService terms, CourseOfferingService offerings,
                               StudentService students, EnrollmentService enrollments, LectureService lectures,
                               QuestionService questions, ExamService exams, GradebookService grades, StudentImportService imports, AttendanceService attendance) {
    public static AcademicServices create(Database database) {
        var terms = new AcademicTermRepository(database);
        var offerings = new CourseOfferingRepository(database);
        var students = new StudentRepository(database);
        return new AcademicServices(new AcademicTermService(terms),
                new CourseOfferingService(offerings, new CourseRepository(database), terms),
                new StudentService(students), new EnrollmentService(new EnrollmentRepository(database), students, offerings, terms),
                new LectureService(new LectureRepository(database), offerings),
                new QuestionService(new QuestionRepository(database), new CourseRepository(database)),
                new ExamService(new ExamRepository(database), offerings, new QuestionRepository(database)),
                new GradebookService(new GradebookRepository(database),offerings,new EnrollmentRepository(database)),
                new StudentImportService(new StudentImportRepository(database),students,new EnrollmentRepository(database),offerings,terms),
                new AttendanceService(new AttendanceRepository(database),new LectureRepository(database),new EnrollmentRepository(database),offerings));
    }
}
