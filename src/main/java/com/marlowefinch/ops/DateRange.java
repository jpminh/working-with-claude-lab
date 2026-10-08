package com.marlowefinch.ops;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * A closed date range for the query endpoints.
 *
 * Both bounds default to "the last 30 days ending today". {@link #resolve} validates
 * request input (ISO dates, {@code from} on or before {@code to}, at most
 * {@value #MAX_DAYS} days) and reports problems as an {@link InvalidRequestException},
 * which becomes a 400. The constructor itself does not validate. See TODO-232.
 */
public record DateRange(LocalDate from, LocalDate to) {

    public static final int DEFAULT_DAYS = 30;
    public static final int MAX_DAYS = 366;

    public static final String FROM_FORMAT = "from must be an ISO date (YYYY-MM-DD)";
    public static final String TO_FORMAT = "to must be an ISO date (YYYY-MM-DD)";
    public static final String ORDER = "from must be on or before to";
    public static final String SPAN = "the range may span at most 366 days";

    /** Resolves and validates; throws {@link InvalidRequestException} listing every problem. */
    public static DateRange resolve(String from, String to, Clock clock) {
        List<String> errors = new ArrayList<>();
        DateRange range = resolve(from, to, clock, errors);
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(errors);
        }
        return range;
    }

    /** Resolves and validates, adding problems to {@code errors}; returns {@code null} if any were found. */
    public static DateRange resolve(String from, String to, Clock clock, List<String> errors) {
        int before = errors.size();
        LocalDate today = LocalDate.now(clock);
        LocalDate start = parse(from, today.minusDays(DEFAULT_DAYS), FROM_FORMAT, errors);
        LocalDate end = parse(to, today, TO_FORMAT, errors);
        if (start != null && end != null) {
            if (start.isAfter(end)) {
                errors.add(ORDER);
            } else if (ChronoUnit.DAYS.between(start, end) > MAX_DAYS) {
                errors.add(SPAN);
            }
        }
        return errors.size() == before ? new DateRange(start, end) : null;
    }

    private static LocalDate parse(String value, LocalDate fallback, String message, List<String> errors) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            errors.add(message);
            return null;
        }
    }
}
