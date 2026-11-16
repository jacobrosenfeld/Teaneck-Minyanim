package com.tbdev.teaneckminyanim.service.schedule;

import com.tbdev.teaneckminyanim.service.schedule.CronSchedule.Frequency;
import com.tbdev.teaneckminyanim.service.schedule.CronSchedule.Mode;
import com.tbdev.teaneckminyanim.service.schedule.CronSchedule.Profile;
import com.tbdev.teaneckminyanim.service.schedule.CronSchedule.SimpleSchedule;
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

class CronScheduleTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final SimpleSchedule SUNDAY_TWO_AM = SimpleSchedule.weekly(LocalTime.of(2, 0), DayOfWeek.SUNDAY);
    private static final Profile RUNS = new Profile("Runs", SUNDAY_TWO_AM);

    @Test
    void weeklyMultiDayScheduleBuildsSundayFirstCron() {
        SimpleSchedule schedule = SimpleSchedule.weekly(LocalTime.of(2, 0), DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY);

        assertEquals("0 0 2 * * SUN,WED", CronSchedule.toCron(schedule));
    }

    @Test
    void dailyScheduleBuildsEveryDayCron() {
        assertEquals("0 30 3 * * *", CronSchedule.toCron(SimpleSchedule.daily(LocalTime.of(3, 30))));
    }

    @Test
    void defaultCronParsesToWeeklySundayAtTwo() {
        SimpleSchedule parsed = CronSchedule.fromCron("0 0 2 * * SUN").orElseThrow();

        assertEquals(SUNDAY_TWO_AM, parsed);
        assertEquals(List.of("SUN"), parsed.dayCodes());
        assertEquals("02:00", parsed.timeValue());
    }

    @Test
    void cronParsesNumericLowercaseAndRangeDays() {
        assertEquals(List.of(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY),
                CronSchedule.fromCron("0 15 4 * * 0,3").orElseThrow().days());
        assertEquals(List.of(DayOfWeek.SUNDAY, DayOfWeek.FRIDAY),
                CronSchedule.fromCron("0 15 4 ? * fri,7").orElseThrow().days());
        assertEquals(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                CronSchedule.fromCron("0 30 3 * * MON-FRI").orElseThrow().days());
    }

    @Test
    void everyDayOfWeekParsesAsDaily() {
        assertEquals(Frequency.DAILY, CronSchedule.fromCron("0 30 3 * * *").orElseThrow().frequency());
        assertEquals(Frequency.DAILY, CronSchedule.fromCron("0 30 3 * * ?").orElseThrow().frequency());
        assertEquals(Frequency.DAILY,
                CronSchedule.fromCron("0 30 3 * * SUN,MON,TUE,WED,THU,FRI,SAT").orElseThrow().frequency());
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
            assertTrue(CronSchedule.fromCron(cron).isEmpty(), cron);
        }
        assertTrue(CronSchedule.fromCron(null).isEmpty());
    }

    @Test
    void simpleSchedulesRoundTripThroughCron() {
        List<SimpleSchedule> schedules = List.of(
                SimpleSchedule.daily(LocalTime.of(0, 0)),
                SimpleSchedule.daily(LocalTime.of(23, 59)),
                SimpleSchedule.weekly(LocalTime.of(2, 0), DayOfWeek.SUNDAY),
                SimpleSchedule.weekly(LocalTime.of(5, 5), DayOfWeek.SATURDAY, DayOfWeek.MONDAY, DayOfWeek.THURSDAY));

        for (SimpleSchedule schedule : schedules) {
            String cron = CronSchedule.toCron(schedule);
            assertTrue(CronSchedule.isValidCron(cron), cron);
            assertEquals(Optional.of(schedule), CronSchedule.fromCron(cron), cron);
        }
    }

    @Test
    void formInputBuildsSchedule() {
        SimpleSchedule schedule = CronSchedule.fromForm("weekly", List.of("WED", "sun"), "02:00");

        assertEquals("0 0 2 * * SUN,WED", CronSchedule.toCron(schedule));
        assertEquals("0 30 3 * * *",
                CronSchedule.toCron(CronSchedule.fromForm("DAILY", null, "03:30")));
    }

    @Test
    void formInputIsValidated() {
        IllegalArgumentException noDays = assertThrows(IllegalArgumentException.class,
                () -> CronSchedule.fromForm("WEEKLY", List.of(), "02:00"));
        assertTrue(noDays.getMessage().contains("at least one day"));

        assertThrows(IllegalArgumentException.class,
                () -> CronSchedule.fromForm("WEEKLY", null, "02:00"));
        assertThrows(IllegalArgumentException.class,
                () -> CronSchedule.fromForm("WEEKLY", List.of("FUNDAY"), "02:00"));
        assertThrows(IllegalArgumentException.class,
                () -> CronSchedule.fromForm("DAILY", null, "25:00"));
        assertThrows(IllegalArgumentException.class,
                () -> CronSchedule.fromForm("DAILY", null, "2am"));
        assertThrows(IllegalArgumentException.class,
                () -> CronSchedule.fromForm("DAILY", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> CronSchedule.fromForm("MONTHLY", null, "02:00"));
        assertThrows(IllegalArgumentException.class,
                () -> CronSchedule.fromForm(null, null, "02:00"));
    }

    @Test
    void describeReadsNaturally() {
        assertEquals("Runs every Sunday at 2:00 AM (America/New_York)",
                CronSchedule.describe(SUNDAY_TWO_AM, NEW_YORK, "Runs"));
        assertEquals("Runs every Sunday and Wednesday at 2:00 AM (America/New_York)",
                CronSchedule.describe(
                        SimpleSchedule.weekly(LocalTime.of(2, 0), DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY), NEW_YORK, "Runs"));
        assertEquals("Runs every Monday, Wednesday and Friday at 3:30 PM (America/New_York)",
                CronSchedule.describe(SimpleSchedule.weekly(LocalTime.of(15, 30),
                        DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), NEW_YORK, "Runs"));
        assertEquals("Runs every day at 12:05 AM (America/New_York)",
                CronSchedule.describe(SimpleSchedule.daily(LocalTime.of(0, 5)), NEW_YORK, "Runs"));
    }

    @Test
    void nextRunUsesSiteTimezone() {
        ZonedDateTime wednesdayNoon = ZonedDateTime.of(2026, 11, 18, 12, 0, 0, 0, NEW_YORK);

        assertEquals(ZonedDateTime.of(2026, 11, 22, 2, 0, 0, 0, NEW_YORK),
                CronSchedule.nextRun("0 0 2 * * SUN", NEW_YORK, wednesdayNoon).orElseThrow());
        assertTrue(CronSchedule.nextRun("bad", NEW_YORK, wednesdayNoon).isEmpty());
    }

    @Test
    void viewKeepsSimpleModeForSimpleCron() {
        ZonedDateTime now = ZonedDateTime.of(2026, 11, 18, 12, 0, 0, 0, NEW_YORK);

        CronSchedule.View view = CronSchedule.view("0 0 2 * * SUN,WED", Mode.SIMPLE, NEW_YORK, now, RUNS);

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

        CronSchedule.View view = CronSchedule.view("0 0 2 * * SUN", Mode.ADVANCED, NEW_YORK, now, RUNS);

        assertTrue(view.advanced());
        assertTrue(view.simpleCompatible());
    }

    @Test
    void viewOpensInAdvancedWhenCronCannotBeShownSimply() {
        ZonedDateTime now = ZonedDateTime.of(2026, 11, 18, 12, 0, 0, 0, NEW_YORK);

        CronSchedule.View view = CronSchedule.view("0 0 */6 * * *", Mode.SIMPLE, NEW_YORK, now, RUNS);

        assertEquals(Mode.ADVANCED, view.mode());
        assertFalse(view.simpleCompatible());
        assertTrue(view.valid());
        assertEquals(SUNDAY_TWO_AM, view.simple());
        assertEquals("Runs on a custom schedule: 0 0 */6 * * * (America/New_York)", view.summary());
        assertEquals("Wed, Nov 18, 2026 at 6:00 PM (America/New_York)", view.nextRun());
    }

    @Test
    void viewFlagsInvalidCron() {
        CronSchedule.View view = CronSchedule.view("nope", Mode.SIMPLE, NEW_YORK, ZonedDateTime.now(NEW_YORK), RUNS);

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

    @Test
    void weeklyCronMatchesLegacyDayAndTime() {
        assertEquals("0 0 22 * * SAT", CronSchedule.weeklyCron(DayOfWeek.SATURDAY, LocalTime.of(22, 0)));
        assertEquals("0 30 8 * * SUN", CronSchedule.weeklyCron(DayOfWeek.SUNDAY, LocalTime.of(8, 30)));
    }

    @Test
    void summaryVerbComesFromProfile() {
        ZonedDateTime now = ZonedDateTime.of(2026, 11, 18, 12, 0, 0, 0, NEW_YORK);
        Profile sends = new Profile("Sends", SimpleSchedule.weekly(LocalTime.of(22, 0), DayOfWeek.SATURDAY));

        assertEquals("Sends every Saturday at 10:00 PM (America/New_York)",
                CronSchedule.view("0 0 22 * * SAT", Mode.SIMPLE, NEW_YORK, now, sends).summary());
        assertEquals("Sends on a custom schedule: 0 0 */6 * * * (America/New_York)",
                CronSchedule.view("0 0 */6 * * *", Mode.SIMPLE, NEW_YORK, now, sends).summary());
        // A cron that doesn't fit the picker shows the profile's fallback in the simple fields.
        assertEquals(sends.fallbackSimple(),
                CronSchedule.view("0 0 */6 * * *", Mode.SIMPLE, NEW_YORK, now, sends).simple());
    }

    @Test
    void validationAcceptsHourlyOrSlowerSchedules() {
        for (String cron : List.of("0 0 2 * * SUN", "0 0 * * * *", "0 0 */6 * * *", "0 30 3 * * MON-FRI",
                "0 0 9,10 * * *", "0 0 0 1 * *")) {
            assertNull(CronSchedule.validationError(cron), cron);
            assertTrue(CronSchedule.isUsable(cron), cron);
        }
    }

    @Test
    void validationRejectsSchedulesMoreFrequentThanTheMinimumInterval() {
        assertEquals(60, CronSchedule.MIN_INTERVAL.toMinutes());
        for (String cron : List.of("* * * * * *", "0 * * * * *", "0 */5 * * * *", "0 0,30 * * * *",
                "0 0,59 2 * * SUN", "0 */10 * 1 * *", "*/30 * * * * *")) {
            String error = CronSchedule.validationError(cron);
            assertEquals(CronSchedule.tooFrequentMessage(), error, cron);
            assertTrue(error.contains("60 minutes"), error);
        }
    }

    @Test
    void validationExplainsSyntaxErrors() {
        assertEquals("Enter a cron expression.", CronSchedule.validationError(null));
        assertEquals("Enter a cron expression.", CronSchedule.validationError("   "));

        String wrongFieldCount = CronSchedule.validationError("0 0 2 * *");
        assertTrue(wrongFieldCount.startsWith("Not a valid cron expression"), wrongFieldCount);
        assertTrue(wrongFieldCount.contains("6 fields"), wrongFieldCount);

        assertTrue(CronSchedule.validationError("0 0 25 * * *").startsWith("Not a valid cron expression"));
        assertTrue(CronSchedule.validationError("every sunday please at two").startsWith("Not a valid cron expression"));
        assertEquals("This cron expression never runs.", CronSchedule.validationError("0 0 0 30 2 *"));
    }

    @Test
    void viewOfTooFrequentCronIsInvalidWithReason() {
        CronSchedule.View view = CronSchedule.view("0 */5 * * * *", Mode.ADVANCED, NEW_YORK, ZonedDateTime.now(NEW_YORK), RUNS);

        assertFalse(view.valid());
        assertEquals(CronSchedule.tooFrequentMessage(), view.error());
        assertEquals(CronSchedule.tooFrequentMessage(), view.summary());
        assertNull(view.nextRun());
        assertTrue(view.advanced());
    }
}
