package com.professoros.repository;

import com.professoros.database.Database;
import com.professoros.model.*;
import java.sql.*;
import java.time.*;
import java.util.*;

public final class AcademicTermRepository {
    private final JdbcOperations jdbc;
    public AcademicTermRepository(Database database) {
        jdbc = new JdbcOperations(database, "A term with this name and academic year already exists.");
    }
    public AcademicTerm create(AcademicTermInput input, Instant now) {
        return jdbc.one("INSERT INTO academic_terms(name, academic_year, start_date, end_date, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING *",
                AcademicTermRepository::map, input.name(), input.academicYear(), input.startDate(), input.endDate(), input.status(), now, now).orElseThrow();
    }
    public Optional<AcademicTerm> update(long id, AcademicTermInput input, Instant now) {
        return jdbc.one("UPDATE academic_terms SET name=?, academic_year=?, start_date=?, end_date=?, status=?, updated_at=? WHERE id=? RETURNING *",
                AcademicTermRepository::map, input.name(), input.academicYear(), input.startDate(), input.endDate(), input.status(), now, id);
    }
    public boolean delete(long id) { return jdbc.execute("DELETE FROM academic_terms WHERE id=?", id) == 1; }
    public Optional<AcademicTerm> findById(long id) {
        return jdbc.one("SELECT * FROM academic_terms WHERE id=?", AcademicTermRepository::map, id);
    }
    public List<AcademicTerm> findAll() {
        return jdbc.query("SELECT * FROM academic_terms ORDER BY start_date DESC, name", AcademicTermRepository::map);
    }
    public boolean hasOfferings(long id) { return jdbc.exists("SELECT EXISTS(SELECT 1 FROM course_offerings WHERE academic_term_id=?)", id); }
    static AcademicTerm map(ResultSet row) throws SQLException {
        return new AcademicTerm(row.getLong("id"), row.getString("name"), row.getString("academic_year"),
                LocalDate.parse(row.getString("start_date")), LocalDate.parse(row.getString("end_date")),
                AcademicStatus.valueOf(row.getString("status")), Instant.parse(row.getString("created_at")), Instant.parse(row.getString("updated_at")));
    }
}
