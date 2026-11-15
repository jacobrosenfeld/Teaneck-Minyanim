package com.tbdev.teaneckminyanim.service.calendar;

import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.CalendarMaterializationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CalendarImportSchedulerTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final ZoneId JERUSALEM = ZoneId.of("Asia/Jerusalem");

    private CalendarImportService importService;
    private CalendarMaterializationService materializationService;
    private ApplicationSettingsService settings;
    private TaskScheduler taskScheduler;
    private CalendarImportScheduler scheduler;

    @BeforeEach
    void setUp() {
        importService = mock(CalendarImportService.class);
        materializationService = mock(CalendarMaterializationService.class);
        settings = mock(ApplicationSettingsService.class);
        taskScheduler = mock(TaskScheduler.class);
        when(settings.getZoneId()).thenReturn(NEW_YORK);
        scheduler = new CalendarImportScheduler(importService, materializationService, settings, taskScheduler);
    }

    @Test
    void schedulesStoredCronInSiteTimezone() {
        when(settings.getCalendarImportCron()).thenReturn("0 30 3 * * MON,THU");

        scheduler.reschedule();

        CronTrigger trigger = capturedTriggers(1)[0];
        assertEquals("0 30 3 * * MON,THU", trigger.getExpression());
        ZonedDateTime wednesdayNoon = ZonedDateTime.of(2026, 11, 18, 12, 0, 0, 0, NEW_YORK);
        assertEquals(ZonedDateTime.of(2026, 11, 19, 3, 30, 0, 0, NEW_YORK).toInstant(),
                trigger.nextExecution(new SimpleTriggerContext(Clock.fixed(wednesdayNoon.toInstant(), NEW_YORK))));
    }

    @Test
    void invalidStoredCronFallsBackToDefault() {
        when(settings.getCalendarImportCron()).thenReturn("every sunday please");

        scheduler.reschedule();

        assertEquals("0 0 2 * * SUN", capturedTriggers(1)[0].getExpression());
        assertEquals("0 0 2 * * SUN", scheduler.effectiveCron());
    }

    @Test
    void missingStoredCronFallsBackToDefault() {
        when(settings.getCalendarImportCron()).thenReturn(null);
        assertEquals("0 0 2 * * SUN", scheduler.effectiveCron());

        when(settings.getCalendarImportCron()).thenReturn("   ");
        assertEquals("0 0 2 * * SUN", scheduler.effectiveCron());
    }

    @Test
    void invalidTimezoneFallsBackToSystemDefault() {
        when(settings.getZoneId()).thenThrow(new java.time.DateTimeException("bad zone"));

        assertEquals(ZoneId.systemDefault(), scheduler.effectiveZone());
    }

    @Test
    void rescheduleCancelsPreviousRunAndPicksUpNewSettings() {
        ScheduledFuture<?> firstRun = mock(ScheduledFuture.class);
        ScheduledFuture<?> secondRun = mock(ScheduledFuture.class);
        doReturn(firstRun, secondRun).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        when(settings.getCalendarImportCron()).thenReturn("0 0 2 * * SUN", "0 15 4 * * *");
        when(settings.getZoneId()).thenReturn(NEW_YORK, JERUSALEM);

        scheduler.reschedule();
        verify(firstRun, never()).cancel(false);
        scheduler.reschedule();

        verify(firstRun).cancel(false);
        verify(secondRun, never()).cancel(false);
        CronTrigger[] triggers = capturedTriggers(2);
        assertEquals("0 0 2 * * SUN", triggers[0].getExpression());
        assertEquals("0 15 4 * * *", triggers[1].getExpression());
        ZonedDateTime noonInJerusalem = ZonedDateTime.of(2026, 11, 18, 12, 0, 0, 0, JERUSALEM);
        assertEquals(ZonedDateTime.of(2026, 11, 19, 4, 15, 0, 0, JERUSALEM).toInstant(),
                triggers[1].nextExecution(new SimpleTriggerContext(Clock.fixed(noonInJerusalem.toInstant(), JERUSALEM))));
    }

    @Test
    void scheduledRunImportsThenMaterializes() {
        when(importService.importAllEnabledOrganizations()).thenReturn(Map.of());

        scheduler.runScheduledImport();

        InOrder order = inOrder(importService, materializationService);
        order.verify(importService).importAllEnabledOrganizations();
        order.verify(materializationService).materializeAll();
    }

    @Test
    void scheduledRunStillMaterializesWhenImportFails() {
        when(importService.importAllEnabledOrganizations()).thenThrow(new RuntimeException("db down"));

        scheduler.runScheduledImport();

        verify(materializationService).materializeAll();
    }

    @Test
    void scheduledJobRunsTheImport() {
        when(settings.getCalendarImportCron()).thenReturn("0 0 2 * * SUN");
        when(importService.importAllEnabledOrganizations()).thenReturn(Map.of());
        scheduler.reschedule();

        ArgumentCaptor<Runnable> job = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(job.capture(), any(Trigger.class));
        job.getValue().run();

        verify(importService).importAllEnabledOrganizations();
        verify(materializationService).materializeAll();
    }

    private CronTrigger[] capturedTriggers(int count) {
        ArgumentCaptor<Trigger> triggers = ArgumentCaptor.forClass(Trigger.class);
        verify(taskScheduler, times(count)).schedule(any(Runnable.class), triggers.capture());
        return triggers.getAllValues().stream().map(CronTrigger.class::cast).toArray(CronTrigger[]::new);
    }
}
