package com.professoros.app;

import java.nio.file.Path;

/** Centralizes storage policy; no UI or repository needs to know the default location. */
public final class AppPaths {
    private AppPaths() {
    }

    public static Path databaseFile() {
        return databaseFile(System.getenv("PROFESSOR_OS_DATA_DIR"),
                Boolean.getBoolean("professoros.packaged"),
                System.getenv("LOCALAPPDATA"), System.getProperty("user.home"));
    }

    static Path databaseFile(String configuredDirectory) {
        return databaseFile(configuredDirectory, false, null, System.getProperty("user.home"));
    }

    static Path databaseFile(String configuredDirectory, boolean packaged, String localAppData, String userHome) {
        Path directory;
        if (configuredDirectory != null && !configuredDirectory.isBlank()) {
            directory = Path.of(configuredDirectory);
        } else if (packaged) {
            Path local = localAppData == null || localAppData.isBlank()
                    ? Path.of(userHome, "AppData", "Local") : Path.of(localAppData);
            directory = local.resolve("ProfessorOS").resolve("data");
        } else {
            directory = Path.of("data");
        }
        return directory.toAbsolutePath().normalize().resolve("professor-os.db");
    }
}
