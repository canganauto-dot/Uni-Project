package com.professoros.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Logger;

/** Opens short-lived connections. Callers own and close each returned connection. */
public final class Database {
    private static final Logger LOGGER = Logger.getLogger(Database.class.getName());
    private final Path file;

    public Database(Path file) {
        this.file = Objects.requireNonNull(file, "Database path is required")
                .toAbsolutePath().normalize();
    }

    public void initialize() throws SQLException {
        try (Connection connection = openConnection()) {
            if (!connection.isValid(2)) {
                throw new SQLException("Database connection validation failed: " + file);
            }
            new MigrationRunner().migrate(connection);
        } catch (SQLException failure) {
            throw new SQLException("Database initialization failed at " + file + ": " + failure.getMessage(), failure);
        }
        LOGGER.info(() -> "Database initialized at " + file);
    }

    public Connection openConnection() throws SQLException {
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException exception) {
            throw new SQLException("Cannot create database directory: " + file.getParent(), exception);
        }

        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
        try {
            // Static configuration only. Future queries with values must use prepared statements.
            try (var statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA busy_timeout = 5000");
                try (var result = statement.executeQuery("PRAGMA foreign_keys")) {
                    if (!result.next() || result.getInt(1) != 1) {
                        throw new SQLException("SQLite foreign key enforcement could not be enabled");
                    }
                }
            }
            return connection;
        } catch (SQLException exception) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
    }
}
