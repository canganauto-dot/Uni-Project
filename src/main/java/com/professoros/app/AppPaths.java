package com.professoros.app;

import java.nio.file.Path;

/** Centralizes storage policy; no UI or repository needs to know the default location. */
public final class AppPaths {
    private AppPaths() {
    }

    public static Path databaseFile() {
        return databaseFile(System.getenv("PROFESSOR_OS_DATA_DIR"));
    }

    static Path databaseFile(String configuredDirectory) {
        Path directory = configuredDirectory == null || configuredDirectory.isBlank()
                ? Path.of("data") : Path.of(configuredDirectory);
        return directory.toAbsolutePath().normalize().resolve("professor-os.db");
    }
}
