package io.github.gjuton.internal.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneOffset;

/**
 * Date utilities for generation.
 */
public final class DateUtil {

    private static final int ANCHOR_YEAR = 2024;

    private DateUtil() {
    }

    /**
     * Returns a leap day (February 29) that falls within {@code [min, max]},
     * preferring one close to the present day, or {@code null} if no leap day
     * exists in the range.
     */
    public static LocalDate leapDayInRange(Instant min, Instant max) {
        var minDate = LocalDate.ofInstant(min, ZoneOffset.UTC);
        var maxDate = LocalDate.ofInstant(max, ZoneOffset.UTC);
        int minYear = minDate.getYear();
        int maxYear = maxDate.getYear();
        for (int year = ANCHOR_YEAR; year <= maxYear; year += 4) {
            if (Year.isLeap(year)) {
                var leapDay = LocalDate.of(year, 2, 29);
                if (!leapDay.isBefore(minDate) && !leapDay.isAfter(maxDate)) {
                    return leapDay;
                }
            }
        }
        for (int year = ANCHOR_YEAR - 4; year >= minYear; year -= 4) {
            if (Year.isLeap(year)) {
                var leapDay = LocalDate.of(year, 2, 29);
                if (!leapDay.isBefore(minDate) && !leapDay.isAfter(maxDate)) {
                    return leapDay;
                }
            }
        }
        return null;
    }
}
