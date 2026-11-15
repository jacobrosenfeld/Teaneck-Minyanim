package com.tbdev.teaneckminyanim.service.email;

import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeeklyMinyanReviewEmailSchedulerTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    @Test
    void defaultTriggerFiresSaturdayAtTenPmInSiteTimezone() {
        CronTrigger trigger = WeeklyMinyanReviewEmailScheduler.cronTrigger(
                DayOfWeek.SATURDAY, LocalTime.of(22, 0), NEW_YORK);

        ZonedDateTime wednesdayNoon = ZonedDateTime.of(2026, 11, 18, 12, 0, 0, 0, NEW_YORK);
        assertEquals(ZonedDateTime.of(2026, 11, 21, 22, 0, 0, 0, NEW_YORK).toInstant(),
                nextRun(trigger, wednesdayNoon));
    }

    @Test
    void customDayAndTimeAreUsed() {
        CronTrigger trigger = WeeklyMinyanReviewEmailScheduler.cronTrigger(
                DayOfWeek.SUNDAY, LocalTime.of(8, 30), NEW_YORK);

        assertEquals("0 30 8 * * SUN", trigger.getExpression());
        ZonedDateTime wednesdayNoon = ZonedDateTime.of(2026, 11, 18, 12, 0, 0, 0, NEW_YORK);
        assertEquals(ZonedDateTime.of(2026, 11, 22, 8, 30, 0, 0, NEW_YORK).toInstant(),
                nextRun(trigger, wednesdayNoon));
    }

    @Test
    void rescheduleCancelsPreviousRunAndUsesCurrentSettings() {
        WeeklyMinyanReviewEmailService emailService = mock(WeeklyMinyanReviewEmailService.class);
        ApplicationSettingsService settings = mock(ApplicationSettingsService.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> firstRun = mock(ScheduledFuture.class);
        ScheduledFuture<?> secondRun = mock(ScheduledFuture.class);
        doReturn(firstRun, secondRun).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        when(settings.getZoneId()).thenReturn(NEW_YORK);
        when(settings.getWeeklyReviewEmailDay()).thenReturn(DayOfWeek.SATURDAY, DayOfWeek.THURSDAY);
        when(settings.getWeeklyReviewEmailTime()).thenReturn(LocalTime.of(22, 0), LocalTime.of(19, 15));

        WeeklyMinyanReviewEmailScheduler scheduler =
                new WeeklyMinyanReviewEmailScheduler(emailService, settings, taskScheduler);
        scheduler.reschedule();
        verify(firstRun, never()).cancel(false);
        scheduler.reschedule();

        verify(firstRun).cancel(false);
        ArgumentCaptor<Trigger> triggers = ArgumentCaptor.forClass(Trigger.class);
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), triggers.capture());
        assertEquals("0 0 22 * * SAT", ((CronTrigger) triggers.getAllValues().get(0)).getExpression());
        assertEquals("0 15 19 * * THU", ((CronTrigger) triggers.getAllValues().get(1)).getExpression());
    }

    private static java.time.Instant nextRun(CronTrigger trigger, ZonedDateTime now) {
        return trigger.nextExecution(new SimpleTriggerContext(Clock.fixed(now.toInstant(), NEW_YORK)));
    }
}
