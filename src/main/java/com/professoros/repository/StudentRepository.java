package com.professoros.repository;

import com.professoros.database.Database;
import com.professoros.model.*;
import java.sql.*;
import java.time.*;
import java.util.*;

public final class StudentRepository {
    private final JdbcOperations jdbc;
    public StudentRepository(Database database) { jdbc = new JdbcOperations(database, "This student number already exists."); }
    public Student create(StudentInput input, Instant now) {
        return jdbc.one("INSERT INTO students(student_number, first_name, last_name, email, notes, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING *",
                StudentRepository::map, input.studentNumber(), input.firstName(), input.lastName(), input.email(), input.notes(), now, now).orElseThrow();
    }
    public Optional<Student> update(long id, StudentInput input, Instant now) {
        return jdbc.one("UPDATE students SET student_number=?, first_name=?, last_name=?, email=?, notes=?, updated_at=? WHERE id=? RETURNING *",
                StudentRepository::map, input.studentNumber(), input.firstName(), input.lastName(), input.email(), input.notes(), now, id);
    }
    public boolean delete(long id) { return jdbc.execute("DELETE FROM students WHERE id=?", id) == 1; }
    public Optional<Student> findById(long id) { return jdbc.one("SELECT * FROM students WHERE id=?", StudentRepository::map, id); }
    public Optional<Student> findByStudentNumber(String number) {
        return jdbc.one("SELECT * FROM students WHERE student_number=? COLLATE NOCASE", StudentRepository::map, number);
    }
    public List<Student> findAll() {
        return jdbc.query("SELECT * FROM students ORDER BY last_name, first_name, student_number", StudentRepository::map);
    }
    public List<Student> search(String query) { return findAll().stream().filter(student -> student.matches(query)).toList(); }
    public boolean hasEnrollments(long id) { return jdbc.exists("SELECT EXISTS(SELECT 1 FROM enrollments WHERE student_id=?)", id); }
    static Student map(ResultSet row) throws SQLException {
        return new Student(row.getLong("id"), row.getString("student_number"), row.getString("first_name"), row.getString("last_name"),
                row.getString("email"), row.getString("notes"), Instant.parse(row.getString("created_at")), Instant.parse(row.getString("updated_at")));
    }
}
