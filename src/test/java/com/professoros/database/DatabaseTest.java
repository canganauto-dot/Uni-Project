package com.professoros.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void initializesNestedDirectoryWithCoursesAndMigrationHistory() throws Exception {
        Path file = temporaryDirectory.resolve("nested/professor-os.db");
        Database database = new Database(file);
        database.initialize();
        assertTrue(Files.exists(file));
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement("SELECT count(*) FROM sqlite_master WHERE type = ?")) {
            statement.setString(1, "table");
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(17, rows.getInt(1));
            }
        }
    }

    @Test
    void enforcesForeignKeysOnEveryConnection() throws Exception {
        Database database = new Database(temporaryDirectory.resolve("foreign-keys.db"));
        database.initialize();
        try (var connection = database.openConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE parent (id INTEGER PRIMARY KEY)");
            statement.execute("CREATE TABLE child (parent_id INTEGER REFERENCES parent(id))");
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            try (var connection = database.openConnection();
                 var insert = connection.prepareStatement("INSERT INTO child(parent_id) VALUES (?)")) {
                insert.setInt(1, 999);
                assertThrows(SQLException.class, insert::executeUpdate);
            }
        }
    }

    @Test
    void repeatedInitializationPreservesExistingDataAndConnectionsClose() throws Exception {
        Database database = new Database(temporaryDirectory.resolve("existing.db"));
        var connection = database.openConnection();
        try (connection; var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE fixture (name TEXT NOT NULL)");
            try (var insert = connection.prepareStatement("INSERT INTO fixture(name) VALUES (?)")) {
                insert.setString(1, "Professor's saved work");
                insert.executeUpdate();
            }
        }
        assertTrue(connection.isClosed());
        database.initialize();
        database.initialize();
        try (var reopened = database.openConnection();
             var statement = reopened.prepareStatement("SELECT name FROM fixture");
             var rows = statement.executeQuery()) {
            assertTrue(rows.next());
            assertEquals("Professor's saved work", rows.getString(1));
            assertFalse(rows.next());
        }
    }

    @Test
    void reportsUnusableStorageDirectory() throws Exception {
        Path blockedDirectory = temporaryDirectory.resolve("not-a-directory");
        Files.writeString(blockedDirectory, "existing file must remain untouched");
        Database database = new Database(blockedDirectory.resolve("professor-os.db"));
        SQLException failure = assertThrows(SQLException.class, database::initialize);
        assertTrue(failure.getMessage().contains("Cannot create database directory"));
        assertNotNull(failure.getCause());
        assertEquals("existing file must remain untouched", Files.readString(blockedDirectory));
    }
}
