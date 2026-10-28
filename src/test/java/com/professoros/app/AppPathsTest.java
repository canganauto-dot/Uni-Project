package com.professoros.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppPathsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void usesPredictableDevelopmentLocationWhenUnconfigured() {
        Path expected = Path.of("data/professor-os.db").toAbsolutePath().normalize();
        assertEquals(expected, AppPaths.databaseFile(null));
        assertEquals(expected, AppPaths.databaseFile("  "));
    }

    @Test
    void acceptsAnAlternativeStorageDirectory() {
        assertEquals(temporaryDirectory.resolve("professor-os.db"),
                AppPaths.databaseFile(temporaryDirectory.toString()));
    }
    @Test
    void packagedDataLivesOutsideInstallationAndExplicitOverrideWins() {
        var local = temporaryDirectory.resolve("local");
        var home = temporaryDirectory.resolve("home");
        assertEquals(local.resolve("ProfessorOS/data/professor-os.db"),
                AppPaths.databaseFile(null, true, local.toString(), home.toString()));
        assertEquals(home.resolve("AppData/Local/ProfessorOS/data/professor-os.db"),
                AppPaths.databaseFile(" ", true, null, home.toString()));
        assertEquals(temporaryDirectory.resolve("professor-os.db"),
                AppPaths.databaseFile(temporaryDirectory.toString(), true, local.toString(), home.toString()));
    }
}
