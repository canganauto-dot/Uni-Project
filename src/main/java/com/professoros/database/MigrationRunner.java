package com.professoros.database;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Explicit, append-only registry. All pending changes and their history commit together. */
public final class MigrationRunner {
    private final List<String> resources;

    static final List<String> RESOURCES = List.of("/db/migrations/V001__create_courses.sql",
                "/db/migrations/V002__academic_terms.sql",
                "/db/migrations/V003__course_offerings.sql",
                "/db/migrations/V004__students.sql",
                "/db/migrations/V005__enrollments.sql",
                "/db/migrations/V006__lectures.sql",
                "/db/migrations/V007__question_bank.sql",
                "/db/migrations/V008__exam_builder.sql",
                "/db/migrations/V009__grade_management.sql",
                "/db/migrations/V010__attendance.sql");

    public MigrationRunner() { this(RESOURCES); }

    MigrationRunner(List<String> resources) {
        this.resources = List.copyOf(resources);
    }

    public void migrate(Connection connection) throws SQLException {
        if (!connection.getAutoCommit()) {
            throw new SQLException("Migrations require a connection without an active transaction");
        }
        List<String> scripts = new ArrayList<>();
        for (String resource : resources) {
            scripts.add(readScript(resource));
        }
        try (var statement = connection.createStatement()) {
            // Obtain the write lock before reading history, so concurrent startups cannot race.
            statement.execute("BEGIN IMMEDIATE");
            try {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS schema_version (
                            version INTEGER PRIMARY KEY CHECK (version > 0),
                            resource TEXT NOT NULL,
                            checksum TEXT NOT NULL,
                            applied_at TEXT NOT NULL
                        )
                        """);
                int currentVersion = 0;
                try (var history = statement.executeQuery(
                        "SELECT version, resource, checksum FROM schema_version ORDER BY version")) {
                    while (history.next()) {
                        int version = history.getInt("version");
                        if (version != currentVersion + 1 || version > resources.size()) {
                            throw new SQLException("Unsupported or incomplete database migration history at version " + version);
                        }
                        if (!resources.get(version - 1).equals(history.getString("resource"))
                                || !checksum(scripts.get(version - 1)).equals(history.getString("checksum"))) {
                            throw new SQLException("Applied migration has changed: version " + version
                                    + "; stored resource=" + history.getString("resource")
                                    + "; expected resource=" + resources.get(version - 1)
                                    + "; stored SHA-256=" + history.getString("checksum")
                                    + "; computed SHA-256=" + checksum(scripts.get(version - 1))
                                    + ". Preserve the database and investigate before repairing migration history.");
                        }
                        currentVersion = version;
                    }
                }
                for (int index = currentVersion; index < scripts.size(); index++) {
                    // Explicit delimiter avoids breaking quoted semicolons and trigger bodies.
                    for (String sql : scripts.get(index).split("(?m)^-- @statement\\s*$")) {
                        if (!sql.isBlank()) {
                            statement.execute(sql);
                        }
                    }
                    try (var insert = connection.prepareStatement(
                            "INSERT INTO schema_version(version, resource, checksum, applied_at) VALUES (?, ?, ?, ?)")) {
                        insert.setInt(1, index + 1);
                        insert.setString(2, resources.get(index));
                        insert.setString(3, checksum(scripts.get(index)));
                        insert.setString(4, Instant.now().toString());
                        insert.executeUpdate();
                    }
                }
                statement.execute("COMMIT");
            } catch (SQLException | RuntimeException failure) {
                try {
                    statement.execute("ROLLBACK");
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        }
    }

    static String readScript(String resource) throws SQLException {
        try (var stream = MigrationRunner.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new SQLException("Missing migration resource: " + resource);
            }
            // Normalize Git/platform line endings before hashing.
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException failure) {
            throw new SQLException("Cannot read migration resource: " + resource, failure);
        }
    }

    static String checksum(String script) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(script.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("JDK SHA-256 implementation unavailable", failure);
        }
    }
}
