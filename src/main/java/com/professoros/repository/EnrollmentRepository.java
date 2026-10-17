package com.professoros.repository;

import com.professoros.database.Database;
import com.professoros.model.*;
import java.sql.*;
import java.time.*;
import java.util.*;

public final class EnrollmentRepository {
    private final JdbcOperations jdbc;
    public EnrollmentRepository(Database database) {
        jdbc = new JdbcOperations(database, "This student already has an enrollment in this offering. Update the existing enrollment status instead.");
    }
    public Enrollment enroll(long studentId, long offeringId, Instant now) {
        return jdbc.one("INSERT INTO enrollments(student_id, course_offering_id, enrolled_at, status) VALUES (?, ?, ?, 'ACTIVE') RETURNING *",
                EnrollmentRepository::map, studentId, offeringId, now).orElseThrow();
    }
    public Optional<Enrollment> updateStatus(long id, EnrollmentStatus status) {
        return jdbc.one("UPDATE enrollments SET status=? WHERE id=? RETURNING *", EnrollmentRepository::map, status, id);
    }
    public Optional<Enrollment> findById(long id) {
        return jdbc.one("SELECT * FROM enrollments WHERE id=?", EnrollmentRepository::map, id);
    }
    public boolean exists(long studentId, long offeringId) {
        return jdbc.exists("SELECT EXISTS(SELECT 1 FROM enrollments WHERE student_id=? AND course_offering_id=?)", studentId, offeringId);
    }
    public List<EnrolledStudent> findStudentsForOffering(long offeringId) {
        return jdbc.query("""
                SELECT s.*, e.id AS enrollment_id, e.student_id, e.course_offering_id, e.enrolled_at, e.status AS enrollment_status
                FROM enrollments e JOIN students s ON s.id=e.student_id
                WHERE e.course_offering_id=? ORDER BY s.last_name, s.first_name, s.student_number
                """, row -> new EnrolledStudent(joinedEnrollment(row), StudentRepository.map(row)), offeringId);
    }
    public List<StudentOffering> findOfferingsForStudent(long studentId) {
        return jdbc.query("""
                SELECT o.*, c.course_code, c.course_name, t.name AS term_name, t.academic_year,
                       e.id AS enrollment_id, e.student_id, e.course_offering_id, e.enrolled_at, e.status AS enrollment_status
                FROM enrollments e JOIN course_offerings o ON o.id=e.course_offering_id
                JOIN courses c ON c.id=o.course_id JOIN academic_terms t ON t.id=o.academic_term_id
                WHERE e.student_id=? ORDER BY t.start_date DESC, c.course_code, o.section
                """, row -> new StudentOffering(joinedEnrollment(row), CourseOfferingRepository.summary(row)), studentId);
    }
    private static Enrollment map(ResultSet row) throws SQLException {
        return new Enrollment(row.getLong("id"), row.getLong("student_id"), row.getLong("course_offering_id"),
                Instant.parse(row.getString("enrolled_at")), EnrollmentStatus.valueOf(row.getString("status")));
    }
    private static Enrollment joinedEnrollment(ResultSet row) throws SQLException {
        return new Enrollment(row.getLong("enrollment_id"), row.getLong("student_id"), row.getLong("course_offering_id"),
                Instant.parse(row.getString("enrolled_at")), EnrollmentStatus.valueOf(row.getString("enrollment_status")));
    }
}
