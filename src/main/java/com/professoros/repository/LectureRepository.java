package com.professoros.repository;

import com.professoros.database.Database;
import com.professoros.model.*;
import java.sql.*;
import java.time.*;
import java.util.*;

public final class LectureRepository {
    private final JdbcOperations jdbc;
    public LectureRepository(Database database) {
        jdbc = new JdbcOperations(database, "This lecture number already exists in this offering.");
    }
    public Lecture create(long offeringId, LectureInput input, Instant now) {
        return jdbc.one("""
                INSERT INTO lectures(course_offering_id, lecture_number, title, planned_date, topics, description,
                                     duration_minutes, status, professor_notes, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING *
                """, LectureRepository::map, offeringId, input.lectureNumber(), input.title(), input.plannedDate(),
                input.topics(), input.description(), input.durationMinutes(), input.status().name(), input.professorNotes(), now, now).orElseThrow();
    }
    public Optional<Lecture> update(long offeringId, long id, LectureInput input, Instant now) {
        return jdbc.one("""
                UPDATE lectures SET lecture_number=?, title=?, planned_date=?, topics=?, description=?,
                                    duration_minutes=?, status=?, professor_notes=?, updated_at=?
                WHERE id=? AND course_offering_id=? RETURNING *
                """, LectureRepository::map, input.lectureNumber(), input.title(), input.plannedDate(), input.topics(),
                input.description(), input.durationMinutes(), input.status().name(), input.professorNotes(), now, id, offeringId);
    }
    public boolean delete(long offeringId, long id) {
        return jdbc.execute("DELETE FROM lectures WHERE id=? AND course_offering_id=?", id, offeringId) == 1;
    }
    public Optional<Lecture> findById(long offeringId, long id) {
        return jdbc.one("SELECT * FROM lectures WHERE id=? AND course_offering_id=?", LectureRepository::map, id, offeringId);
    }
    public Optional<Lecture> findByNumber(long offeringId, int number) {
        return jdbc.one("SELECT * FROM lectures WHERE course_offering_id=? AND lecture_number=?", LectureRepository::map, offeringId, number);
    }
    public List<Lecture> findByOffering(long offeringId) {
        return jdbc.query("SELECT * FROM lectures WHERE course_offering_id=? ORDER BY planned_date, lecture_number, id", LectureRepository::map, offeringId);
    }
    public List<Lecture> search(long offeringId, String query) {
        return findByOffering(offeringId).stream().filter(lecture -> lecture.matches(query)).toList();
    }
    private static Lecture map(ResultSet row) throws SQLException {
        return new Lecture(row.getLong("id"), row.getLong("course_offering_id"), row.getInt("lecture_number"),
                row.getString("title"), LocalDate.parse(row.getString("planned_date")), row.getString("topics"),
                row.getString("description"), row.getInt("duration_minutes"), LectureStatus.valueOf(row.getString("status")),
                row.getString("professor_notes"), Instant.parse(row.getString("created_at")), Instant.parse(row.getString("updated_at")));
    }
}
