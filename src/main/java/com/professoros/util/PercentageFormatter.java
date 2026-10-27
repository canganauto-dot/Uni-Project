package com.professoros.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Presentation only: input is already in percentage points (36.3636 means 36.36%). */
public final class PercentageFormatter {
    private PercentageFormatter() { }

    public static String number(BigDecimal percentage) {
        return percentage.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    public static String format(BigDecimal percentage) {
        return number(percentage) + "%";
    }

    public static String format(double percentage) {
        return format(BigDecimal.valueOf(percentage));
    }
}
