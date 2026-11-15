package com.tbdev.teaneckminyanim.service.calendar;

import com.tbdev.teaneckminyanim.service.calendar.CalendarImportSchedule.Frequency;
import com.tbdev.teaneckminyanim.service.calendar.CalendarImportSchedule.Mode;
import com.tbdev.teaneckminyanim.service.calendar.CalendarImportSchedule.SimpleSchedule;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalendarImportScheduleTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    @Test
    void weeklyMultiDayScheduleBuildsSundayFirstCron() {
        SimpleSchedule schedule = SimpleSchedule.weekly(LocalTime.of(2, 0), DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY);

        assertEquals("0 0 2 * * SUN,WED", CalendarImportSchedule.toCron(schedule));
    }

    @Test
    void dailyScheduleBuildsEveryDayCron() {
        assertEquals("0 30 3 * * *", CalendarImportSchedule.toCron(SimpleSchedule.daily(LocalTime.of(3, 30))));
    }

    @Test
    void defaultCronParsesToWeeklySundayAtTwo() {
        SimpleSchedule parsed = CalendarImportSchedule.fromCron("0 0 2 * * SUN").orElseThrow();

        assertEquals(CalendarImportSchedule.defaultSimpleSchedule(), parsed);
        assertEquals(List.of("SUN"), parsed.dayCodes());
        assertEquals("02:00", parsed.timeValue());
    }

    @Test
    void cronParsesNumericLowercaseAndRangeDays() {
        assertEquals(List.of(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY),
                CalendarImportSchedule.fromCron("0 15 4 * * 0,3").orElseThrow().days());
        assertEquals(List.of(DayOfWeek.SUNDAY, DayOfWeek.FRIDAY),
                CalendarImportSchedule.fromCron("0 15 4 ? * fri,7").orElseThrow().days());
        assertEquals(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                CalendarImportSchedule.fromCron("0 30 3 * * MON-FRI").orElseThrow().days());
    }

    @Test
    void everyDayOfWeekParsesAsDaily() {
        assertEquals(Frequency.DAILY, CalendarImportSchedule.fromCron("0 30 3 * * *").orElseThrow().frequency());
        assertEquals(Frequency.DAILY, CalendarImportSchedule.fromCron("0 30 3 * * ?").orElseThrow().frequency());
        assertEquals(Frequency.DAILY,
                CalendarImportSchedule.fromCron("0 30 3 * * SUN,MON,TUE,WED,THU,FRI,SAT").orElseThrow().frequency());
    }

    @Test
    void cronsOutsideTheSimpleModelDoNotParse() {
        List<String> crons = List.of(
                "0 */15 * * * *",     // every 15 minutes
                "0 0 */6 * * *",      // every 6 hours
                "30 0 2 * * SUN",     // non-zero second
                "0 0 2 1 * *",        // day of month
                "0 0 2 * 1 SUN",      // month
                "0 0 2 * * MON#1",    // nth weekday
                "0 0 2 * * MON-FRI/2",
                "0 0 2,14 * * SUN",   // two hours
                "not a cron",
                "");
        for (String cron : crons) {
            assertTrue(CalendarImportSchedule.fromCron(cron).isEmpty(), cron);
        }
        assertTrue(CalendarImportSchedule.fromCron(null).isEmpty());
    }

    @Test
    void simpleSchedulesRoundTripThroughCron() {
        List<SimpleSchedule> schedules = List.of(
                SimpleSchedule.daily(LocalTime.of(0, 0)),
                SimpleSchedule.daily(LocalTime.of(23, 59)),
                SimpleSchedule.weekly(LocalTime.of(2, 0), DayOfWeek.SUNDAY),
                SimpleSchedule.weekly(LocalTime.of(5, 5), DayOfWeek.SATURDAY, DayOfWeek.MONDAY, DayOfWeek.THURSDAY));

        for (SimpleSchedule schedule : schedules) {
            String cron = CalendarImportSchedule.toCron(schedule);
            assertTrue(CalendarImportSchedule.isValidCron(cron), cron);
            assertEquals(Optional.of(schedule), CalendarImportSchedule.fromCron(cron), cron);
        }
    }

    @Test
    void formInputBuildsSchedule() {
        SimpleSchedule schedule = CalendarImportSchedule.fromForm("weekly", List.of("WED", "sun"), "02:00");

        assertEquals("0 0 2 * * SUN,WED", CalendarImportSchedule.toCron(schedule));
        assertEquals("0 30 3 * * *",
                CalendarImportSchedule.toCron(CalendarImportSchedule.fromForm("DAILY", null, "03:30")));
    }

    @Test
    void formInputIsValidated() {
        IllegalArgumentException noDays = assertThrows(IllegalArgumentException.class,
                () -> CalendarImportSchedule.fromForm("WEEKLY", List.of(), "02:00"));
        assertTrue(noDays.getMessage().contains("at least one day"));

        assertThrows(IllegalArgumentException.class,
                () -> CalendarImportSchedule.fromForm("WEEKLY", null, "02:00"));
        assertThrows(IllegalArgumentException.class,
                () -> CalendarImportSchedule.fromForm("WEEKLY", List.of("FUNDAY"), "02:00"));
        assertThrows(IllegalArgumentException.class,
                () -> CalendarImportSchedule.fromForm("DAILY", null, "25:00"));
        assertThrows(IllegalArgumentException.class,
                () -> CalendarImportSchedule.fromForm("DAILY", null, "2am"));
        assertThrows(IllegalArgumentException.class,
                () -> CalendarImportSchedule.fromForm("DAILY", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> CalendarImportSchedule.fromForm("MONTHLY", null, "02:00"));
        assertThrows(IllegalArgumentException.class,
                () -> CalendarImportSchedule.fromForm(null, null, "02:00"));
    }

    @Test
    void describeReadsNaturally() {
        assertEquals("Runs every Sunday at 2:00 AM (America/New_York)",
                CalendarImportSchedule.describe(CalendarImportSchedule.defaultSimpleSchedule(), NEW_YORK));
        assertEquals("Runs every Sunday and Wednesday at 2:00 AM (America/New_York)",
                CalendarImportSchedule.describe(
                        SimpleSchedule.weekly(LocalTime.of(2, 0), DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY), NEW_YORK));
        assertEquals("Runs every Monday, Wednesday and Friday at 3:30 PM (America/New_York)",
                CalendarImportSchedule.describe(SimpleSchedule.weekly(LocalTime.of(15, 30),
                        DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), NEW_YORK));
        assertEquals("Runs every day at 12:05 AM (America/New_York)",
                CalendarImportSchedule.describe(SimpleSchedule.daily(LocalTime.of(0, 5)), NEW_YORK));
    }

    @Test
    void nextRunUsesSiteTimezone() {
        ZonedDateTime wednesdayNoon = ZonedDateTime.of(2026, 11, 18, 12, 0, 0, 0, NEW_YORK);

        assertEquals(ZonedDateTime.of(2026, 11, 22, 2, 0, 0, 0, NEW_YORK),
                CalendarImportSchedule.nextRun("0 0 2 * * SUN", NEW_YORK, wednesdayNoon).orElseThrow());
        assertTrue(CalendarImportSchedule.nextRun("bad", NEW_YORK, wednesdayNoon).isEmpty());
    }

    @Test
    void viewKeepsSimpleModeForSimpleCron() {
        ZonedDateTime now = ZonedDateTime.of(2026, 11, 18, 12, 0, 0, 0, NEW_YORK);

        CalendarImportSchedule.View view = CalendarImportSchedule.view("0 0 2 * * SUN,WED", Mode.SIMPLE, NEW_YORK, now);

        assertEquals(Mode.SIMPLE, view.mode());
        assertTrue(view.simpleCompatible());
        assertTrue(view.weekly());
        assertTrue(view.hasDay("SUN"));
        assertTrue(view.hasDay("WED"));
        assertFalse(view.hasDay("MON"));
        assertEquals("Runs every Sunday and Wednesday at 2:00 AM (America/New_York)", view.summary());
        assertEquals("Sun, Nov 22, 2026 at 2:00 AM (America/New_York)", view.nextRun());
    }

    @Test
    void viewHonoursAdvancedPreferenceForSimpleCron() {
        ZonedDateTime now = ZonedDateTime.of(2026, 11, 18, 12, 0, 0, 0, NEW_YORK);

        CalendarImportSchedule.View view = CalendarImportSchedule.view("0 0 2 * * SUN", Mode.ADVANCED, NEW_YORK, now);

        assertTrue(view.advanced());
        assertTrue(view.simpleCompatible());
    }

    @Test
    void viewOpensInAdvancedWhenCronCannotBeShownSimply() {
        ZonedDateTime now = ZonedDateTime.of(2026, 11, 18, 12, 0, 0, 0, NEW_YORK);

        CalendarImportSchedule.View view = CalendarImportSchedule.view("0 0 */6 * * *", Mode.SIMPLE, NEW_YORK, now);

        assertEquals(Mode.ADVANCED, view.mode());
        assertFalse(view.simpleCompatible());
        assertTrue(view.valid());
        assertEquals(CalendarImportSchedule.defaultSimpleSchedule(), view.simple());
        assertEquals("Runs on a custom schedule: 0 0 */6 * * * (America/New_York)", view.summary());
        assertEquals("Wed, Nov 18, 2026 at 6:00 PM (America/New_York)", view.nextRun());
    }

    @Test
    void viewFlagsInvalidCron() {
        CalendarImportSchedule.View view = CalendarImportSchedule.view("nope", Mode.SIMPLE, NEW_YORK, ZonedDateTime.now(NEW_YORK));

        assertEquals(Mode.ADVANCED, view.mode());
        assertFalse(view.valid());
        assertNull(view.nextRun());
    }

    @Test
    void modeParsing() {
        assertEquals(Mode.ADVANCED, Mode.fromString(" advanced "));
        assertThrows(IllegalArgumentException.class, () -> Mode.fromString("EXPERT"));
        assertEquals(Mode.SIMPLE, Mode.fromStored("garbage"));
        assertEquals(Mode.SIMPLE, Mode.fromStored(null));
    }
}
