package com.professoros.service;

import com.professoros.model.DomainValidationException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Shared field rules used by the academic services. */
final class AcademicValidation {
    private AcademicValidation() { }
    static String text(String value, String label, int maximum, boolean required) {
        String result = value == null ? "" : value.strip();
        if (required && result.isEmpty()) { throw new DomainValidationException(label + " is required."); }
        if (result.codePointCount(0, result.length()) > maximum || result.indexOf('\0') >= 0) {
            throw new DomainValidationException(label + " must contain at most " + maximum + " characters and no null characters.");
        }
        return result;
    }
    static String identifier(String value, String label) {
        String result = text(value, label, 40, true).toUpperCase(Locale.ROOT);
        if (!result.matches("[A-Z0-9][A-Z0-9 ._-]{0,39}")) {
            throw new DomainValidationException(label + " must use letters A–Z, numbers, spaces, dots, hyphens or underscores (maximum 40 characters).");
        }
        return result;
    }
    static <T> T required(T value, String label) {
        if (value == null) { throw new DomainValidationException(label + " is required."); }
        return value;
    }
    static Instant now(Clock clock) { return clock.instant().truncatedTo(ChronoUnit.MILLIS); }
    static Instant updated(Clock clock, Instant previous) {
        Instant now = now(clock);
        return now.isBefore(previous) ? previous : now;
    }
    static DomainValidationException missing(String entity) {
        return new DomainValidationException(entity + " no longer exists. Refresh and try again.");
    }
}
