package com.professoros.database;

import com.professoros.model.CourseInput;
import com.professoros.repository.CourseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AcademicMigrationTest {
    @TempDir Path directory;
    @Test void upgradesPhaseTwoWithoutLosingCoursesAndDoesNotReapplyVersions() throws Exception {
        Database database = new Database(directory.resolve("upgrade.db"));
        try (var connection = database.openConnection()) {
            new MigrationRunner(List.of("/db/migrations/V001__create_courses.sql")).migrate(connection);
        }
        var courses = new CourseRepository(database);
        var saved = courses.create(new CourseInput("CS201", "Object Oriented Programming", "", 3, ""), Instant.now());
        database.initialize();
        String history;
        try (var connection = database.openConnection(); var query = connection.createStatement();
             var rows = query.executeQuery("SELECT group_concat(version || ':' || checksum || ':' || applied_at) FROM (SELECT * FROM schema_version ORDER BY version)")) {
            assertTrue(rows.next()); history = rows.getString(1);
            assertEquals(8, history.split(",").length);
        }
        database.initialize();
        assertEquals(saved, courses.findById(saved.id()).orElseThrow());
        try (var connection = database.openConnection(); var query = connection.createStatement();
             var rows = query.executeQuery("SELECT group_concat(version || ':' || checksum || ':' || applied_at) FROM (SELECT * FROM schema_version ORDER BY version)")) {
            assertTrue(rows.next()); assertEquals(history, rows.getString(1));
        }
    }
    @Test void databaseRejectsImpossibleDatesAndInvalidStatuses() throws Exception {
        Database database = new Database(directory.resolve("checks.db")); database.initialize();
        try (var connection = database.openConnection(); var insert = connection.prepareStatement(
                "INSERT INTO academic_terms(name, academic_year, start_date, end_date, status, created_at, updated_at) VALUES ('Fall', '2026', ?, ?, ?, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')")) {
            for (String[] values : new String[][]{{"2026-02-30","2026-05-01","ACTIVE"},{"2026-05-01","2026-04-01","ACTIVE"},{"2026-01-01","2026-05-01","INVALID"}}) {
                insert.setString(1, values[0]); insert.setString(2, values[1]); insert.setString(3, values[2]);
                assertThrows(java.sql.SQLException.class, insert::executeUpdate);
            }
        }
    }
}
