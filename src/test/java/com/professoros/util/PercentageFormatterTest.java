package com.professoros.util;

import com.professoros.model.AttendanceSummary;
import com.professoros.model.AssessmentCategory;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class PercentageFormatterTest {
    @Test void roundsHalfUpAndRemovesTrailingZeros() {
        String[][] examples = {{"0", "0%"}, {"100.000", "100%"}, {"82.500", "82.5%"},
            {"36.36363636363636", "36.36%"}, {"66.666666666", "66.67%"},
            {"12.345", "12.35%"}, {"99.999", "100%"}, {"0.005", "0.01%"},
            {"-0.004", "0%"}, {"1E+2", "100%"}};
        for (var example : examples) assertEquals(example[1], PercentageFormatter.format(new BigDecimal(example[0])));
        assertEquals("36.36%", PercentageFormatter.format(400.0 / 11));
    }
    @Test void formattingNeverMutatesFullPrecisionInputs() {
        var original = new BigDecimal("89.999999999999999999");
        assertEquals("90%", PercentageFormatter.format(original));
        assertEquals("89.999999999999999999", original.toPlainString());
        assertTrue(original.compareTo(new BigDecimal("90")) < 0);
    }
    @Test void usesStablePlainDecimalsRegardlessOfDefaultLocale() {
        var previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("12.5%", PercentageFormatter.format(new BigDecimal("12.500")));
        } finally { Locale.setDefault(previous); }
    }
    @Test void summariesShareFormattingWithoutChangingNullOrZeroSemantics() {
        var original = new BigDecimal("66.666666");
        var summary = new AttendanceSummary(2, 1, 0, 0, 0, original);
        assertTrue(summary.display().startsWith("66.67% ·"));
        assertSame(original, summary.percentage());
        assertTrue(new AttendanceSummary(0, 0, 0, 0, 1, null).display().startsWith("Not yet rated"));
        assertTrue(new AttendanceSummary(0, 1, 0, 0, 0, BigDecimal.ZERO).display().startsWith("0%"));
        assertEquals("Quiz (20%)", new AssessmentCategory(1, 1, "Quiz", new BigDecimal("20.00"), Instant.EPOCH, Instant.EPOCH).toString());
    }
}
