package com.professoros.repository;

import com.professoros.database.Database;
import com.professoros.model.*;
import java.sql.*;
import java.time.*;
import java.util.*;

public final class CourseOfferingRepository {
    static final String SUMMARY = """
            SELECT o.*, c.course_code, c.course_name, t.name AS term_name, t.academic_year
            FROM course_offerings o JOIN courses c ON c.id=o.course_id
            JOIN academic_terms t ON t.id=o.academic_term_id
            """;
    private final JdbcOperations jdbc;
    public CourseOfferingRepository(Database database) {
        jdbc = new JdbcOperations(database, "An offering for this course, term and section already exists.");
    }
    public CourseOffering create(CourseOfferingInput input, Instant now) {
        return jdbc.one("INSERT INTO course_offerings(course_id, academic_term_id, section, notes, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING *",
                CourseOfferingRepository::map, input.courseId(), input.academicTermId(), input.section(), input.notes(), input.status(), now, now).orElseThrow();
    }
    public Optional<CourseOffering> update(long id, CourseOfferingInput input, Instant now) {
        return jdbc.one("UPDATE course_offerings SET course_id=?, academic_term_id=?, section=?, notes=?, status=?, updated_at=? WHERE id=? RETURNING *",
                CourseOfferingRepository::map, input.courseId(), input.academicTermId(), input.section(), input.notes(), input.status(), now, id);
    }
    public boolean delete(long id) { return jdbc.execute("DELETE FROM course_offerings WHERE id=?", id) == 1; }
    public Optional<CourseOffering> findById(long id) {
        return jdbc.one("SELECT * FROM course_offerings WHERE id=?", CourseOfferingRepository::map, id);
    }
    public List<CourseOffering> findAll() { return jdbc.query("SELECT * FROM course_offerings ORDER BY id", CourseOfferingRepository::map); }
    public List<CourseOffering> findByCourse(long id) {
        return jdbc.query("SELECT * FROM course_offerings WHERE course_id=? ORDER BY id", CourseOfferingRepository::map, id);
    }
    public List<CourseOffering> findByTerm(long id) {
        return jdbc.query("SELECT * FROM course_offerings WHERE academic_term_id=? ORDER BY id", CourseOfferingRepository::map, id);
    }
    public List<OfferingSummary> summaries() {
        return jdbc.query(SUMMARY + " ORDER BY t.start_date DESC, c.course_code, o.section", CourseOfferingRepository::summary);
    }
    public Optional<OfferingSummary> summary(long id) {
        return jdbc.one(SUMMARY + " WHERE o.id=?", CourseOfferingRepository::summary, id);
    }
    public boolean hasEnrollments(long id) { return jdbc.exists("SELECT EXISTS(SELECT 1 FROM enrollments WHERE course_offering_id=?)", id); }
    public boolean hasLectures(long id) { return jdbc.exists("SELECT EXISTS(SELECT 1 FROM lectures WHERE course_offering_id=?)", id); }
    static CourseOffering map(ResultSet row) throws SQLException {
        return new CourseOffering(row.getLong("id"), row.getLong("course_id"), row.getLong("academic_term_id"),
                row.getString("section"), row.getString("notes"), AcademicStatus.valueOf(row.getString("status")),
                Instant.parse(row.getString("created_at")), Instant.parse(row.getString("updated_at")));
    }
    static OfferingSummary summary(ResultSet row) throws SQLException {
        return new OfferingSummary(map(row), row.getString("course_code"), row.getString("course_name"), row.getString("term_name"), row.getString("academic_year"));
    }
}
