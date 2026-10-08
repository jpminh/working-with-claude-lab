package com.marlowefinch.ops;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks the query parameters of one request and collects every problem.
 *
 * Call {@link #range} and/or {@link #limit}, then {@link #throwIfInvalid} before using
 * the results.
 */
public final class RequestValidator {

    public static final int MIN_LIMIT = 1;
    public static final int MAX_LIMIT = 500;
    public static final String LIMIT_RANGE = "limit must be an integer between 1 and 500";

    private final Clock clock;
    private final List<String> errors = new ArrayList<>();

    public RequestValidator(Clock clock) {
        this.clock = clock;
    }

    /** The date range, or {@code null} when a problem was recorded. */
    public DateRange range(String from, String to) {
        return DateRange.resolve(from, to, clock, errors);
    }

    /** The limit, or {@code defaultLimit} when missing, blank or invalid (a problem is recorded if invalid). */
    public int limit(String limit, int defaultLimit) {
        if (limit == null || limit.isBlank()) {
            return defaultLimit;
        }
        try {
            int value = Integer.parseInt(limit.trim());
            if (value >= MIN_LIMIT && value <= MAX_LIMIT) {
                return value;
            }
        } catch (NumberFormatException e) {
            // reported below
        }
        errors.add(LIMIT_RANGE);
        return defaultLimit;
    }

    public List<String> errors() {
        return List.copyOf(errors);
    }

    public void throwIfInvalid() {
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(errors);
        }
    }
}
