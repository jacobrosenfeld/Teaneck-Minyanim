package com.tbdev.teaneckminyanim.service.calendar;

import com.tbdev.teaneckminyanim.enums.SettingKey;
import org.springframework.scheduling.support.CronExpression;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Converts between the calendar import cron setting and the simple
 * "daily / weekly on these days at this time" model shown on the settings page.
 *
 * The cron string ({@code calendar.import.cron}) is always what runs; the simple model is
 * only a friendlier way to edit it. A cron fits the simple model when it fires once a day
 * at a fixed second 0, minute and hour, every day of the month and every month, on either
 * every day of the week or a list (or plain range) of days.
 */
public final class CalendarImportSchedule {

    public static final String DEFAULT_CRON = SettingKey.CALENDAR_IMPORT_CRON.getDefaultValue();

    /** Sunday-first, matching the day pills on the settings page. */
    public static final List<DayOfWeek> WEEK_ORDER = List.of(
            DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);

    private static final Comparator<DayOfWeek> SUNDAY_FIRST = Comparator.comparingInt(WEEK_ORDER::indexOf);
    private static final Pattern TIME_PATTERN = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d{1,2}");
    private static final DateTimeFormatter TIME_DISPLAY = DateTimeFormatter.ofPattern("h:mm a", Locale.US);
    private static final DateTimeFormatter NEXT_RUN_DISPLAY =
            DateTimeFormatter.ofPattern("EEE, MMM d, yyyy 'at' h:mm a", Locale.US);

    private CalendarImportSchedule() {
    }

    public enum Mode {
        SIMPLE, ADVANCED;

        public static Mode fromString(String value) {
            if (value == null) {
                throw new IllegalArgumentException("Schedule mode is required.");
            }
            try {
                return Mode.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Schedule mode must be SIMPLE or ADVANCED.");
            }
        }

        /** Lenient variant for stored values: anything unrecognised reads as SIMPLE. */
        public static Mode fromStored(String value) {
            try {
                return fromString(value);
            } catch (IllegalArgumentException e) {
                return SIMPLE;
            }
        }
    }

    public enum Frequency {
        DAILY, WEEKLY
    }

    /**
     * A schedule the simple picker can show. {@code days} is sorted Sunday-first and is empty
     * for {@link Frequency#DAILY}.
     */
    public record SimpleSchedule(Frequency frequency, List<DayOfWeek> days, LocalTime time) {
        public SimpleSchedule {
            if (frequency == null || time == null) {
                throw new IllegalArgumentException("Frequency and time are required.");
            }
            if (frequency == Frequency.DAILY) {
                days = List.of();
            } else {
                if (days == null || days.isEmpty()) {
                    throw new IllegalArgumentException("Choose at least one day for a weekly schedule.");
                }
                days = sortedDistinct(days);
            }
            time = time.withSecond(0).withNano(0);
        }

        public static SimpleSchedule daily(LocalTime time) {
            return new SimpleSchedule(Frequency.DAILY, List.of(), time);
        }

        public static SimpleSchedule weekly(LocalTime time, DayOfWeek... days) {
            return new SimpleSchedule(Frequency.WEEKLY, List.of(days), time);
        }

        /** Three-letter cron day codes (SUN, MON, ...), Sunday-first. */
        public List<String> dayCodes() {
            return days.stream().map(CalendarImportSchedule::cronDayCode).toList();
        }

        /** Time as HH:mm for an {@code <input type="time">}. */
        public String timeValue() {
            return String.format(Locale.ROOT, "%02d:%02d", time.getHour(), time.getMinute());
        }
    }

    /**
     * What the settings page needs to render the schedule editor and its preview.
     *
     * @param mode           mode the page should open in (ADVANCED when the cron can't be shown simply)
     * @param cron           cron expression as stored / entered
     * @param simple         the cron as a simple schedule, or the default simple schedule when it doesn't fit
     * @param simpleCompatible whether {@code cron} fits the simple model
     * @param valid          whether {@code cron} is a valid Spring cron expression
     * @param summary        readable description, e.g. "Runs every Sunday at 2:00 AM (America/New_York)"
     * @param nextRun        next run in the site timezone, formatted for display, or null
     * @param timezone       site timezone id
     */
    public record View(
            Mode mode,
            String cron,
            SimpleSchedule simple,
            boolean simpleCompatible,
            boolean valid,
            String summary,
            String nextRun,
            String timezone) {

        public boolean advanced() {
            return mode == Mode.ADVANCED;
        }

        public boolean weekly() {
            return simple.frequency() == Frequency.WEEKLY;
        }

        public boolean hasDay(String code) {
            return simple.dayCodes().contains(code);
        }
    }

    /** The simple schedule equivalent to {@link #DEFAULT_CRON}: weekly on Sunday at 2:00 AM. */
    public static SimpleSchedule defaultSimpleSchedule() {
        return SimpleSchedule.weekly(LocalTime.of(2, 0), DayOfWeek.SUNDAY);
    }

    /** Builds the 6-field Spring cron for a simple schedule, e.g. {@code 0 0 2 * * SUN,WED}. */
    public static String toCron(SimpleSchedule schedule) {
        String dayOfWeek = schedule.frequency() == Frequency.DAILY
                ? "*"
                : String.join(",", schedule.dayCodes());
        return String.format(Locale.ROOT, "0 %d %d * * %s",
                schedule.time().getMinute(), schedule.time().getHour(), dayOfWeek);
    }

    /**
     * Parses a cron back into the simple model, or returns empty when the cron can't be shown
     * with the simple picker (or is not a valid cron at all).
     */
    public static Optional<SimpleSchedule> fromCron(String cron) {
        if (!isValidCron(cron)) {
            return Optional.empty();
        }
        String[] fields = cron.trim().split("\\s+");
        if (fields.length != 6) {
            return Optional.empty();
        }
        Integer second = parseFixed(fields[0], 59);
        Integer minute = parseFixed(fields[1], 59);
        Integer hour = parseFixed(fields[2], 23);
        if (second == null || second != 0 || minute == null || hour == null) {
            return Optional.empty();
        }
        if (!isEvery(fields[3]) || !isEvery(fields[4])) {
            return Optional.empty();
        }
        LocalTime time = LocalTime.of(hour, minute);
        if (isEvery(fields[5])) {
            return Optional.of(SimpleSchedule.daily(time));
        }
        Set<DayOfWeek> days = parseDayList(fields[5]);
        if (days == null || days.isEmpty()) {
            return Optional.empty();
        }
        if (days.size() == 7) {
            return Optional.of(SimpleSchedule.daily(time));
        }
        return Optional.of(new SimpleSchedule(Frequency.WEEKLY, new ArrayList<>(days), time));
    }

    /**
     * Validates the simple picker's raw form values and builds the schedule.
     *
     * @throws IllegalArgumentException with an admin-readable message when a field is invalid
     */
    public static SimpleSchedule fromForm(String frequency, Collection<String> days, String time) {
        if (frequency == null || frequency.isBlank()) {
            throw new IllegalArgumentException("Choose how often the import runs (daily or weekly).");
        }
        Frequency parsedFrequency;
        try {
            parsedFrequency = Frequency.valueOf(frequency.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Frequency must be daily or weekly.");
        }

        if (time == null || !TIME_PATTERN.matcher(time.trim()).matches()) {
            throw new IllegalArgumentException("Time must be a valid time like 02:00.");
        }
        LocalTime parsedTime;
        try {
            parsedTime = LocalTime.parse(time.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Time must be a valid time like 02:00.");
        }

        if (parsedFrequency == Frequency.DAILY) {
            return SimpleSchedule.daily(parsedTime);
        }

        List<DayOfWeek> parsedDays = new ArrayList<>();
        if (days != null) {
            for (String day : days) {
                if (day == null || day.isBlank()) {
                    continue;
                }
                DayOfWeek parsed = parseDayToken(day.trim());
                if (parsed == null) {
                    throw new IllegalArgumentException("Unknown day of week: " + day.trim());
                }
                parsedDays.add(parsed);
            }
        }
        if (parsedDays.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one day for a weekly schedule.");
        }
        return new SimpleSchedule(Frequency.WEEKLY, parsedDays, parsedTime);
    }

    public static boolean isValidCron(String cron) {
        if (cron == null || cron.isBlank()) {
            return false;
        }
        try {
            CronExpression.parse(cron.trim());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** "Runs every Sunday and Wednesday at 2:00 AM (America/New_York)". */
    public static String describe(SimpleSchedule schedule, ZoneId zone) {
        String when;
        if (schedule.frequency() == Frequency.DAILY || schedule.days().size() == 7) {
            when = "every day";
        } else {
            List<String> names = schedule.days().stream()
                    .map(day -> day.getDisplayName(TextStyle.FULL, Locale.US))
                    .toList();
            when = "every " + joinWithAnd(names);
        }
        return "Runs " + when + " at " + schedule.time().format(TIME_DISPLAY) + " (" + zone.getId() + ")";
    }

    /** Next run strictly after {@code from}, in {@code zone}, or empty for an invalid cron. */
    public static Optional<ZonedDateTime> nextRun(String cron, ZoneId zone, ZonedDateTime from) {
        if (!isValidCron(cron)) {
            return Optional.empty();
        }
        return Optional.ofNullable(CronExpression.parse(cron.trim()).next(from.withZoneSameInstant(zone)));
    }

    /**
     * Builds the page view for a cron and the mode the admin last chose. The page opens in
     * ADVANCED when the cron can't be shown in the simple picker.
     */
    public static View view(String cron, Mode preferredMode, ZoneId zone, ZonedDateTime now) {
        String trimmed = cron == null ? "" : cron.trim();
        boolean valid = isValidCron(trimmed);
        Optional<SimpleSchedule> simple = fromCron(trimmed);
        Mode mode = simple.isEmpty() ? Mode.ADVANCED : preferredMode;

        String summary;
        if (simple.isPresent()) {
            summary = describe(simple.get(), zone);
        } else if (valid) {
            summary = "Runs on a custom schedule: " + trimmed + " (" + zone.getId() + ")";
        } else {
            summary = "Not a valid cron expression.";
        }
        String nextRun = nextRun(trimmed, zone, now)
                .map(next -> next.format(NEXT_RUN_DISPLAY) + " (" + zone.getId() + ")")
                .orElse(null);

        return new View(mode, trimmed, simple.orElseGet(CalendarImportSchedule::defaultSimpleSchedule),
                simple.isPresent(), valid, summary, nextRun, zone.getId());
    }

    static String cronDayCode(DayOfWeek day) {
        return day.getDisplayName(TextStyle.SHORT, Locale.US).toUpperCase(Locale.ROOT);
    }

    private static List<DayOfWeek> sortedDistinct(Collection<DayOfWeek> days) {
        EnumSet<DayOfWeek> distinct = EnumSet.copyOf(days);
        List<DayOfWeek> sorted = new ArrayList<>(distinct);
        sorted.sort(SUNDAY_FIRST);
        return List.copyOf(sorted);
    }

    private static Integer parseFixed(String field, int max) {
        if (!NUMBER_PATTERN.matcher(field).matches()) {
            return null;
        }
        int value = Integer.parseInt(field);
        return value <= max ? value : null;
    }

    private static boolean isEvery(String field) {
        return "*".equals(field) || "?".equals(field);
    }

    /** Parses "SUN,WED", "1,3", "MON-FRI"; returns null for anything else (steps, #, L, wrap-around ranges). */
    private static Set<DayOfWeek> parseDayList(String field) {
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String part : field.split(",", -1)) {
            if (part.isEmpty()) {
                return null;
            }
            int dash = part.indexOf('-');
            if (dash < 0) {
                DayOfWeek day = parseDayToken(part);
                if (day == null) {
                    return null;
                }
                days.add(day);
                continue;
            }
            Integer start = dayIndex(part.substring(0, dash));
            Integer end = dayIndex(part.substring(dash + 1));
            if (start == null || end == null || start > end) {
                return null;
            }
            for (int i = start; i <= end; i++) {
                days.add(fromCronIndex(i));
            }
        }
        return days;
    }

    private static DayOfWeek parseDayToken(String token) {
        Integer index = dayIndex(token);
        return index == null ? null : fromCronIndex(index);
    }

    /**
     * Spring cron day index: 0 or 7 = Sunday, 1 = Monday ... 6 = Saturday. Names map
     * to 1-7 with Sunday = 7, as Spring does, so name ranges like MON-SUN work.
     */
    private static Integer dayIndex(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        if (NUMBER_PATTERN.matcher(token).matches()) {
            int value = Integer.parseInt(token);
            return value <= 7 ? value : null;
        }
        String upper = token.toUpperCase(Locale.ROOT);
        for (DayOfWeek day : DayOfWeek.values()) {
            if (cronDayCode(day).equals(upper)) {
                return day.getValue();
            }
        }
        return null;
    }

    private static DayOfWeek fromCronIndex(int index) {
        return index == 0 || index == 7 ? DayOfWeek.SUNDAY : DayOfWeek.of(index);
    }

    private static String joinWithAnd(List<String> items) {
        if (items.size() == 1) {
            return items.get(0);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.get(items.size() - 1);
    }
}
