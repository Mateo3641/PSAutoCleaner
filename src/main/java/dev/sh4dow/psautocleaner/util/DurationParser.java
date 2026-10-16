package dev.sh4dow.psautocleaner.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for parsing and formatting human-readable durations.
 * Supports units: s (seconds), m (minutes), h (hours), d (days), w (weeks), mo (months = 30d).
 * Also supports compound durations (e.g. "1d 12h 30m") and bare numbers (defaulted to days).
 */
public final class DurationParser {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("(\\d+)\\s*([a-zA-Z]+)?");

    private DurationParser() {
    }

    /**
     * Parses a duration string into a {@link Duration}.
     *
     * @param input the input string, e.g. "3m", "30d", "12h", "1d 6h", "30"
     * @return the parsed Duration
     * @throws IllegalArgumentException if the input is null, empty, or cannot be parsed
     */
    public static Duration parse(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Duration string cannot be null or empty");
        }

        String trimmed = input.trim();

        // Check if input is a bare integer (e.g. "30") -> treat as days for backwards compatibility
        if (trimmed.matches("^\\d+$")) {
            long days = Long.parseLong(trimmed);
            return Duration.ofDays(days);
        }

        Matcher matcher = TOKEN_PATTERN.matcher(trimmed);
        long totalSeconds = 0;
        int matchedEnd = 0;
        boolean foundAny = false;

        while (matcher.find()) {
            // Verify there is no unexpected garbage between matches
            String between = trimmed.substring(matchedEnd, matcher.start()).trim();
            if (!between.isEmpty()) {
                throw new IllegalArgumentException("Invalid format in duration string: '" + input + "'");
            }
            matchedEnd = matcher.end();
            foundAny = true;

            long value = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);
            if (unit == null || unit.isEmpty()) {
                // If unit is missing inside a compound string, default to days
                totalSeconds += value * 86400L;
                continue;
            }

            unit = unit.toLowerCase(Locale.ROOT);
            switch (unit) {
                case "s", "sec", "secs", "second", "seconds" -> totalSeconds += value;
                case "m", "min", "mins", "minute", "minutes" -> totalSeconds += value * 60L;
                case "h", "hr", "hrs", "hour", "hours" -> totalSeconds += value * 3600L;
                case "d", "day", "days" -> totalSeconds += value * 86400L;
                case "w", "wk", "wks", "week", "weeks" -> totalSeconds += value * 86400L * 7L;
                case "mo", "mon", "mons", "month", "months" -> totalSeconds += value * 86400L * 30L;
                case "y", "yr", "yrs", "year", "years" -> totalSeconds += value * 86400L * 365L;
                default -> throw new IllegalArgumentException("Unknown time unit '" + unit + "' in duration string: '" + input + "'");
            }
        }

        String remaining = trimmed.substring(matchedEnd).trim();
        if (!foundAny || !remaining.isEmpty()) {
            throw new IllegalArgumentException("Could not parse duration string: '" + input + "'");
        }

        return Duration.ofSeconds(totalSeconds);
    }

    /**
     * Formats a duration into a compact representation, e.g. "2d 4h", "3m 15s", "45s".
     */
    public static String formatShort(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return "0s";
        }

        long seconds = duration.getSeconds();
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainingSeconds = seconds % 60L;

        List<String> parts = new ArrayList<>();
        if (days > 0) parts.add(days + "d");
        if (hours > 0) parts.add(hours + "h");
        if (minutes > 0) parts.add(minutes + "m");
        if (remainingSeconds > 0 && days == 0) parts.add(remainingSeconds + "s");

        return parts.isEmpty() ? "0s" : String.join(" ", parts);
    }

    /**
     * Formats a duration into a verbose representation, e.g. "2 days, 4 hours", "3 minutes".
     */
    public static String formatVerbose(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return "0 seconds";
        }

        long seconds = duration.getSeconds();
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainingSeconds = seconds % 60L;

        List<String> parts = new ArrayList<>();
        if (days > 0) parts.add(days + (days == 1 ? " day" : " days"));
        if (hours > 0) parts.add(hours + (hours == 1 ? " hour" : " hours"));
        if (minutes > 0) parts.add(minutes + (minutes == 1 ? " minute" : " minutes"));
        if (remainingSeconds > 0 && days == 0) parts.add(remainingSeconds + (remainingSeconds == 1 ? " second" : " seconds"));

        return parts.isEmpty() ? "0 seconds" : String.join(", ", parts);
    }
}
