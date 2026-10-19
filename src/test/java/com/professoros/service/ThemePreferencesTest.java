package com.professoros.service;
import com.professoros.model.ThemeMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class ThemePreferencesTest {
    @TempDir Path directory;
    @Test void defaultsToSystemAndRoundTripsEveryModeAcrossInstances() throws Exception {
        Path file=directory.resolve("settings/appearance.properties");
        assertEquals(ThemeMode.SYSTEM,new ThemePreferences(file).load());
        for(var mode:ThemeMode.values()) { new ThemePreferences(file).save(mode); assertEquals(mode,new ThemePreferences(file).load()); }
    }
    @Test void invalidPreferenceFallsBackWithoutTouchingOriginal() throws Exception {
        Path file=directory.resolve("appearance.properties"); Files.writeString(file,"theme=unknown");
        assertEquals(ThemeMode.SYSTEM,new ThemePreferences(file).load()); assertEquals("theme=unknown",Files.readString(file));
    }
    @Test void writeFailureIsReportedAndDoesNotDestroyOtherFiles() throws Exception {
        Path parent=directory.resolve("blocked"); Files.writeString(parent,"keep");
        assertThrows(java.io.IOException.class,()->new ThemePreferences(parent.resolve("appearance.properties")).save(ThemeMode.DARK));
        assertEquals("keep",Files.readString(parent));
    }
    @Test void systemResolutionAndExplicitOverridesAreDeterministic() {
        assertTrue(ThemeMode.SYSTEM.dark(true)); assertFalse(ThemeMode.SYSTEM.dark(false));
        assertTrue(ThemeMode.DARK.dark(false)); assertFalse(ThemeMode.LIGHT.dark(true));
        assertEquals(ThemeMode.SYSTEM,ThemeMode.SYSTEM.next().next().next());
    }
}
