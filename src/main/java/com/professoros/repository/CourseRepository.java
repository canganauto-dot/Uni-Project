package com.professoros.repository;

import com.professoros.database.Database;
import com.professoros.model.Course;
import com.professoros.model.CourseInput;
import com.professoros.model.DuplicateCourseCodeException;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CourseRepository {
    private static final String COLUMNS = "id, course_code, course_name, department, credit_hours, description, created_at, updated_at";
    private final Database database;

    public CourseRepository(Database database) {
        this.database = database;
    }

    public Course create(CourseInput input, Instant timestamp) {
        String sql = "INSERT INTO courses(course_code, course_name, department, credit_hours, description, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING " + COLUMNS;
        try (var connection = database.openConnection(); var statement = connection.prepareStatement(sql)) {
            bindInput(statement, input);
            statement.setString(6, timestamp.toString());
            statement.setString(7, timestamp.toString());
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("Course insert returned no row");
                }
                return map(rows);
            }
        } catch (SQLException failure) {
            throw writeFailure("Could not save the course.", input.courseCode(), failure);
        }
    }

    public Optional<Course> update(long id, CourseInput input, Instant timestamp) {
        String sql = "UPDATE courses SET course_code = ?, course_name = ?, department = ?, credit_hours = ?, "
                + "description = ?, updated_at = ? WHERE id = ? RETURNING " + COLUMNS;
        try (var connection = database.openConnection(); var statement = connection.prepareStatement(sql)) {
            bindInput(statement, input);
            statement.setString(6, timestamp.toString());
            statement.setLong(7, id);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException failure) {
            throw writeFailure("Could not update the course.", input.courseCode(), failure);
        }
    }

    public boolean delete(long id) {
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement("DELETE FROM courses WHERE id = ?")) {
            statement.setLong(1, id);
            return statement.executeUpdate() == 1;
        } catch (SQLException failure) {
            if (failure instanceof SQLiteException sqlite
                    && (sqlite.getResultCode() == SQLiteErrorCode.SQLITE_CONSTRAINT_FOREIGNKEY
                    || sqlite.getResultCode() == SQLiteErrorCode.SQLITE_CONSTRAINT_TRIGGER)) {
                throw new com.professoros.model.CourseValidationException(
                        "This course has offerings or bank questions and cannot be deleted. Preserve its academic records.");
            }
            throw new CourseStorageException("Could not delete the course. It may be in use.", failure);
        }
    }

    public boolean hasOfferings(long id) {
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement("SELECT EXISTS(SELECT 1 FROM course_offerings WHERE course_id=?)")) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) { return rows.next() && rows.getBoolean(1); }
        } catch (SQLException failure) {
            throw new CourseStorageException("Could not check this course's offerings.", failure);
        }
    }

    public Optional<Course> findById(long id) {
        return findOne("SELECT " + COLUMNS + " FROM courses WHERE id = ?", id);
    }

    public Optional<Course> findByCourseCode(String code) {
        return findOne("SELECT " + COLUMNS + " FROM courses WHERE course_code = ? COLLATE NOCASE", code);
    }

    private Optional<Course> findOne(String sql, Object value) {
        try (var connection = database.openConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, value);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException failure) {
            throw new CourseStorageException("Could not load the course.", failure);
        }
    }

    public List<Course> findAll() {
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement("SELECT " + COLUMNS + " FROM courses ORDER BY course_code COLLATE NOCASE");
             var rows = statement.executeQuery()) {
            List<Course> courses = new ArrayList<>();
            while (rows.next()) {
                courses.add(map(rows));
            }
            return List.copyOf(courses);
        } catch (SQLException failure) {
            throw new CourseStorageException("Could not load courses.", failure);
        }
    }

    public List<Course> search(String query) {
        // At this scale a shared Java predicate also provides Unicode-aware matching.
        return findAll().stream().filter(course -> course.matches(query)).toList();
    }

    private static void bindInput(PreparedStatement statement, CourseInput input) throws SQLException {
        statement.setString(1, input.courseCode());
        statement.setString(2, input.courseName());
        statement.setString(3, input.department());
        statement.setInt(4, input.creditHours());
        statement.setString(5, input.description());
    }

    private static Course map(ResultSet row) throws SQLException {
        return new Course(row.getLong("id"), row.getString("course_code"), row.getString("course_name"),
                row.getString("department"), row.getInt("credit_hours"), row.getString("description"),
                Instant.parse(row.getString("created_at")), Instant.parse(row.getString("updated_at")));
    }

    private static RuntimeException writeFailure(String message, String code, SQLException failure) {
        if (failure instanceof SQLiteException sqlite
                && sqlite.getResultCode() == SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE) {
            return new DuplicateCourseCodeException(code);
        }
        return new CourseStorageException(message, failure);
    }
}
