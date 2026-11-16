package com.tbdev.teaneckminyanim.service.calendar;

import com.tbdev.teaneckminyanim.enums.SettingKey;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.CalendarMaterializationService;
import com.tbdev.teaneckminyanim.service.schedule.CronSchedule;
import com.tbdev.teaneckminyanim.service.schedule.ScheduledCronJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

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
public class CalendarImportScheduler {

    /** Used when the stored cron is missing, invalid or too frequent. */
    public static final String DEFAULT_CRON = SettingKey.CALENDAR_IMPORT_CRON.getDefaultValue();

    /** Summary verb and the picker's fallback (weekly on Sunday at 2:00 AM, matching {@link #DEFAULT_CRON}). */
    public static final CronSchedule.Profile SCHEDULE_PROFILE = new CronSchedule.Profile(
            "Runs", CronSchedule.SimpleSchedule.weekly(LocalTime.of(2, 0), DayOfWeek.SUNDAY));

    private static final String JOB_NAME = "Calendar import";

    private final CalendarImportService importService;
    private final CalendarMaterializationService materializationService;
    private final ApplicationSettingsService settingsService;
    private final ScheduledCronJob job;

    public CalendarImportScheduler(
            CalendarImportService importService,
            CalendarMaterializationService materializationService,
            ApplicationSettingsService settingsService,
            TaskScheduler taskScheduler) {
        this.importService = importService;
        this.materializationService = materializationService;
        this.settingsService = settingsService;
        this.job = new ScheduledCronJob(JOB_NAME, taskScheduler, this::runScheduledImport);
    }

    /**
     * (Re)schedules the import from the current cron and timezone settings.
     * The previous schedule is cancelled first; a run already in progress is allowed to finish.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void reschedule() {
        job.schedule(effectiveCron(), effectiveZone());
    }

    /**
     * Stored cron, or the default {@code 0 0 2 * * SUN} when the stored value is missing,
     * invalid or fires more often than {@link CronSchedule#MIN_INTERVAL}.
     */
    public String effectiveCron() {
        return ScheduledCronJob.usableCronOr(JOB_NAME, settingsService::getCalendarImportCron, () -> DEFAULT_CRON);
    }

    /** Site timezone, or the JVM default (America/New_York, set at startup) when the setting is invalid. */
    public ZoneId effectiveZone() {
        return ScheduledCronJob.zoneOrDefault(JOB_NAME, settingsService::getZoneId);
    }

    /**
     * Current schedule for the settings page: the stored cron (even if invalid, so the admin
     * can see and fix it), the preferred mode and the next run in the site timezone.
     */
    public CronSchedule.View currentView() {
        ZoneId zone = effectiveZone();
        return CronSchedule.view(
                settingsService.getCalendarImportCron(),
                settingsService.getCalendarImportScheduleMode(),
                zone,
                ZonedDateTime.now(zone),
                SCHEDULE_PROFILE);
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
