package com.tbdev.teaneckminyanim.service.calendar;

import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.CalendarMaterializationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

/**
 * Runs the automatic calendar import on the schedule saved in the
 * {@code calendar.import.cron} setting, in the site timezone ({@code timezone} setting).
 *
 * The job is registered with the shared {@link TaskScheduler} instead of a fixed
 * {@code @Scheduled} cron so the schedule can change at runtime: {@link #reschedule()} cancels
 * the pending run and registers a new one. It runs on startup and must be called whenever the
 * cron or timezone setting is saved.
 *
 * After each scheduled import the calendar is re-materialized, so imported entries and the
 * rolling RULES window are refreshed in one pass. This replaces the separate weekly
 * materialization cron that used to fire at the same time as the import.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarImportScheduler {

    private final CalendarImportService importService;
    private final CalendarMaterializationService materializationService;
    private final ApplicationSettingsService settingsService;
    private final TaskScheduler taskScheduler;

    private ScheduledFuture<?> scheduledRun;

    /**
     * (Re)schedules the import from the current cron and timezone settings.
     * The previous schedule is cancelled first; a run already in progress is allowed to finish.
     */
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void reschedule() {
        if (scheduledRun != null) {
            scheduledRun.cancel(false);
        }
        String cron = effectiveCron();
        ZoneId zone = effectiveZone();
        scheduledRun = taskScheduler.schedule(this::runScheduledImport, new CronTrigger(cron, zone));
        log.info("Calendar import scheduled with cron '{}' ({}); next run {}",
                cron, zone, CalendarImportSchedule.nextRun(cron, zone, ZonedDateTime.now(zone)).orElse(null));
    }

    /**
     * Stored cron, or the default {@code 0 0 2 * * SUN} when the stored value is missing or invalid.
     */
    public String effectiveCron() {
        String stored = null;
        try {
            stored = settingsService.getCalendarImportCron();
        } catch (RuntimeException e) {
            log.warn("Could not read the calendar import cron setting: {}", e.getMessage());
        }
        if (CalendarImportSchedule.isValidCron(stored)) {
            return stored.trim();
        }
        log.warn("Calendar import cron '{}' is missing or invalid; falling back to '{}'",
                stored, CalendarImportSchedule.DEFAULT_CRON);
        return CalendarImportSchedule.DEFAULT_CRON;
    }

    /**
     * Site timezone, or the JVM default (America/New_York, set at startup) when the setting is invalid.
     */
    public ZoneId effectiveZone() {
        try {
            return settingsService.getZoneId();
        } catch (RuntimeException e) {
            ZoneId fallback = ZoneId.systemDefault();
            log.warn("Invalid timezone setting for the calendar import schedule; using {}: {}",
                    fallback, e.getMessage());
            return fallback;
        }
    }

    /**
     * Current schedule for the settings page: the stored cron (even if invalid, so the admin
     * can see and fix it), the preferred mode and the next run in the site timezone.
     */
    public CalendarImportSchedule.View currentView() {
        ZoneId zone = effectiveZone();
        String stored = settingsService.getCalendarImportCron();
        return CalendarImportSchedule.view(
                stored,
                settingsService.getCalendarImportScheduleMode(),
                zone,
                ZonedDateTime.now(zone));
    }

    /**
     * Scheduled job body: imports every organization with calendar import enabled, then
     * re-materializes the calendar.
     *
     * Materialization runs even if the import step fails. It also rolls the RULES window forward,
     * and skipping it would let rule-based minyanim run out of future events.
     */
    public void runScheduledImport() {
        log.info("Starting scheduled calendar import");

        try {
            Map<String, CalendarImportService.ImportResult> results =
                    importService.importAllEnabledOrganizations();
            logSummary(results);
            cleanupOldEntries();
        } catch (Exception e) {
            log.error("Scheduled calendar import failed; materializing anyway to keep the rolling window current", e);
        }

        try {
            materializationService.materializeAll();
            log.info("Calendar materialization after scheduled import completed");
        } catch (Exception e) {
            log.error("Calendar materialization after scheduled import failed: {}", e.getMessage(), e);
        }
    }

    private void logSummary(Map<String, CalendarImportService.ImportResult> results) {
        int successCount = 0;
        int failureCount = 0;
        int totalNewEntries = 0;
        int totalUpdatedEntries = 0;

        for (CalendarImportService.ImportResult result : results.values()) {
            if (result.success) {
                successCount++;
                totalNewEntries += result.newEntries;
                totalUpdatedEntries += result.updatedEntries;
            } else {
                failureCount++;
                log.warn("Failed import for organization {}: {}",
                        result.organizationId, result.errorMessage);
            }
        }

        log.info("Scheduled import completed: {} successful, {} failed, {} new entries, {} updated entries",
                successCount, failureCount, totalNewEntries, totalUpdatedEntries);
    }

    /**
     * Clean up old calendar entries to prevent database bloat.
     */
    private void cleanupOldEntries() {
        // Placeholder: CALENDAR_CLEANUP_DAYS is not applied yet. CalendarImportService.cleanupOldEntries
        // exists per organization but is not called from here.
        log.info("Cleanup of old entries would run here");
        // TODO: Implement when we have a way to iterate all orgs with calendar import enabled
    }
}
