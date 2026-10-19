package com.professoros.service;

import com.professoros.model.ThemeMode;
import java.io.*;
import java.nio.file.*;
import java.util.Properties;
import java.util.logging.*;

/** Local appearance preference, independent of academic data and JavaFX. */
public final class ThemePreferences {
    private final Path file;
    public ThemePreferences(Path file) { this.file=file.toAbsolutePath().normalize(); }
    public ThemeMode load() {
        if(!Files.exists(file)) { return ThemeMode.SYSTEM; }
        try(var input=Files.newInputStream(file)) {
            var properties=new Properties(); properties.load(input);
            return ThemeMode.valueOf(properties.getProperty("theme","SYSTEM"));
        } catch(IOException | IllegalArgumentException failure) {
            Logger.getLogger(getClass().getName()).log(Level.WARNING,"Cannot read appearance preference; using System",failure);
            return ThemeMode.SYSTEM;
        }
    }
    public void save(ThemeMode mode) throws IOException {
        java.util.Objects.requireNonNull(mode); Files.createDirectories(file.getParent());
        Path temporary=Files.createTempFile(file.getParent(),"appearance-",".tmp");
        try {
            var properties=new Properties(); properties.setProperty("theme",mode.name());
            try(var out=Files.newOutputStream(temporary)) { properties.store(out,"Professor OS appearance"); }
            try { Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException unsupported) { Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
