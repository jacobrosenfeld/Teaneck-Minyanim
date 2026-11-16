package com.tbdev.teaneckminyanim.service.email;

import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.schedule.CronSchedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeeklyMinyanReviewEmailSchedulerTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private WeeklyMinyanReviewEmailService emailService;
    private ApplicationSettingsService settings;
    private TaskScheduler taskScheduler;
    private WeeklyMinyanReviewEmailScheduler scheduler;

    @BeforeEach
    void setUp() {
        emailService = mock(WeeklyMinyanReviewEmailService.class);
        settings = mock(ApplicationSettingsService.class);
        taskScheduler = mock(TaskScheduler.class);
        when(settings.getZoneId()).thenReturn(NEW_YORK);
        when(settings.getWeeklyReviewEmailDay()).thenReturn(DayOfWeek.SATURDAY);
        when(settings.getWeeklyReviewEmailTime()).thenReturn(LocalTime.of(22, 0));
        when(settings.getWeeklyReviewEmailLegacyCron()).thenReturn("0 0 22 * * SAT");
        scheduler = new WeeklyMinyanReviewEmailScheduler(emailService, settings, taskScheduler);
    }

    @Test
    void defaultLegacyScheduleFiresSaturdayAtTenPmInSiteTimezone() {
        when(settings.getWeeklyReviewEmailCron()).thenReturn("0 0 22 * * SAT");

        scheduler.reschedule();

        CronTrigger trigger = capturedTriggers(1)[0];
        assertEquals("0 0 22 * * SAT", trigger.getExpression());
        ZonedDateTime wednesdayNoon = ZonedDateTime.of(2026, 11, 18, 12, 0, 0, 0, NEW_YORK);
        assertEquals(ZonedDateTime.of(2026, 11, 21, 22, 0, 0, 0, NEW_YORK).toInstant(), nextRun(trigger, wednesdayNoon));
    }

    @Test
    void storedCronIsUsed() {
        when(settings.getWeeklyReviewEmailCron()).thenReturn("0 30 8 * * SUN,WED");

        scheduler.reschedule();

        CronTrigger trigger = capturedTriggers(1)[0];
        assertEquals("0 30 8 * * SUN,WED", trigger.getExpression());
        ZonedDateTime wednesdayNoon = ZonedDateTime.of(2026, 11, 18, 12, 0, 0, 0, NEW_YORK);
        assertEquals(ZonedDateTime.of(2026, 11, 22, 8, 30, 0, 0, NEW_YORK).toInstant(), nextRun(trigger, wednesdayNoon));
    }

    @Test
    void invalidOrTooFrequentCronFallsBackToLegacyDayAndTime() {
        when(settings.getWeeklyReviewEmailCron()).thenReturn("not a cron", "0 */5 * * * *");
        when(settings.getWeeklyReviewEmailLegacyCron()).thenReturn("0 15 19 * * THU");

        scheduler.reschedule();
        scheduler.reschedule();

        CronTrigger[] triggers = capturedTriggers(2);
        assertEquals("0 15 19 * * THU", triggers[0].getExpression());
        assertEquals("0 15 19 * * THU", triggers[1].getExpression());
    }

    @Test
    void rescheduleCancelsPreviousRunAndUsesCurrentSettings() {
        ScheduledFuture<?> firstRun = mock(ScheduledFuture.class);
        ScheduledFuture<?> secondRun = mock(ScheduledFuture.class);
        doReturn(firstRun, secondRun).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        when(settings.getWeeklyReviewEmailCron()).thenReturn("0 0 22 * * SAT", "0 15 19 * * THU");

        scheduler.reschedule();
        verify(firstRun, never()).cancel(false);
        scheduler.reschedule();

        verify(firstRun).cancel(false);
        CronTrigger[] triggers = capturedTriggers(2);
        assertEquals("0 0 22 * * SAT", triggers[0].getExpression());
        assertEquals("0 15 19 * * THU", triggers[1].getExpression());
    }

    @Test
    void viewUsesSendsVerbAndLegacyFallbackForCustomCron() {
        when(settings.getWeeklyReviewEmailCron()).thenReturn("0 0 */6 * * *");
        when(settings.getWeeklyReviewEmailScheduleMode()).thenReturn(CronSchedule.Mode.SIMPLE);

        CronSchedule.View view = scheduler.currentView();

        assertTrue(view.advanced());
        assertTrue(view.summary().startsWith("Sends on a custom schedule"));
        assertEquals(CronSchedule.SimpleSchedule.weekly(LocalTime.of(22, 0), DayOfWeek.SATURDAY), view.simple());
    }

    private CronTrigger[] capturedTriggers(int count) {
        ArgumentCaptor<Trigger> triggers = ArgumentCaptor.forClass(Trigger.class);
        verify(taskScheduler, times(count)).schedule(any(Runnable.class), triggers.capture());
        return triggers.getAllValues().stream().map(CronTrigger.class::cast).toArray(CronTrigger[]::new);
    }

    private static java.time.Instant nextRun(CronTrigger trigger, ZonedDateTime now) {
        return trigger.nextExecution(new SimpleTriggerContext(Clock.fixed(now.toInstant(), NEW_YORK)));
    }
}
