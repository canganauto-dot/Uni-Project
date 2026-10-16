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
}
