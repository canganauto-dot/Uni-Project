package com.professoros.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MigrationRunnerTest {
    @TempDir Path directory;
    private static final String V1 = "/db/migrations/V001__create_courses.sql";
    private static final String BROKEN = "/db/migrations/V002__broken.sql";

    @Test
    void appliesFirstMigrationOnceAndKeepsItsOriginalHistory() throws Exception {
        Database database = new Database(directory.resolve("courses.db"));
        database.initialize();
        String firstApplied;
        try (var connection = database.openConnection(); var query = connection.createStatement();
             var row = query.executeQuery("SELECT version, checksum, applied_at FROM schema_version WHERE version=1")) {
            assertTrue(row.next());
            assertEquals(1, row.getInt("version"));
            assertEquals(64, row.getString("checksum").length());
            firstApplied = row.getString("applied_at");
            assertFalse(row.next());
        }
        database.initialize();
        try (var connection = database.openConnection(); var query = connection.createStatement();
             var row = query.executeQuery("SELECT applied_at FROM schema_version WHERE version=1")) {
            assertTrue(row.next());
            assertEquals(firstApplied, row.getString(1));
            assertFalse(row.next());
        }
    }

    @Test
    void failureRollsBackNewSchemaAndHistory() throws Exception {
        Database database = new Database(directory.resolve("fresh.db"));
        try (var connection = database.openConnection()) {
            var runner = new MigrationRunner(List.of(V1, BROKEN));
            assertThrows(SQLException.class, () -> runner.migrate(connection));
            try (var query = connection.createStatement();
                 var row = query.executeQuery("SELECT count(*) FROM sqlite_master WHERE type = 'table'")) {
                assertTrue(row.next());
                assertEquals(0, row.getInt(1));
            }
        }
        database.initialize(); // A failed first upgrade leaves a retryable database.
    }

    @Test
    void failedUpgradePreservesPreviouslyCommittedSchemaAndData() throws Exception {
        Database database = new Database(directory.resolve("existing.db"));
        try (var connection = database.openConnection()) {
            new MigrationRunner(List.of(V1)).migrate(connection);
        }
        try (var connection = database.openConnection(); var query = connection.createStatement()) {
            query.execute("CREATE TABLE existing_data(value TEXT)");
            query.execute("INSERT INTO existing_data VALUES ('preserve me')");
            assertThrows(SQLException.class, () -> new MigrationRunner(List.of(V1, BROKEN)).migrate(connection));
            try (var row = query.executeQuery("SELECT value FROM existing_data")) {
                assertTrue(row.next());
                assertEquals("preserve me", row.getString(1));
            }
            try (var row = query.executeQuery("SELECT max(version) FROM schema_version")) {
                assertTrue(row.next());
                assertEquals(1, row.getInt(1));
            }
            try (var row = query.executeQuery("SELECT count(*) FROM sqlite_master WHERE name = 'migration_scratch'")) {
                assertTrue(row.next());
                assertEquals(0, row.getInt(1));
            }
        }
    }

    @Test
    void refusesChangedHistoryAndNewerDatabaseVersions() throws Exception {
        Database database = new Database(directory.resolve("history.db"));
        database.initialize();
        try (var connection = database.openConnection(); var query = connection.createStatement()) {
            query.execute("UPDATE schema_version SET checksum = 'changed' WHERE version=1");
            SQLException altered = assertThrows(SQLException.class, database::initialize);
            assertTrue(altered.getMessage().contains("Applied migration has changed"));
            query.execute("UPDATE schema_version SET version = 99 WHERE version=1");
            SQLException newer = assertThrows(SQLException.class, database::initialize);
            assertTrue(newer.getMessage().contains("Unsupported or incomplete"));
        }
    }

    @Test
    void missingResourceFailsBeforeWritingAnything() throws Exception {
        Database database = new Database(directory.resolve("missing.db"));
        try (var connection = database.openConnection()) {
            SQLException error = assertThrows(SQLException.class,
                    () -> new MigrationRunner(List.of("/missing.sql")).migrate(connection));
            assertTrue(error.getMessage().contains("Missing migration"));
        }
    }
}
