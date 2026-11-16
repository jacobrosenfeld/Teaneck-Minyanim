package com.tbdev.teaneckminyanim.service.email;

import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.schedule.CronSchedule;
import com.tbdev.teaneckminyanim.service.schedule.ScheduledCronJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Sends the weekly minyan review digest on the schedule saved in
 * {@code email.weekly-review.cron}, in the site timezone. Uses the same cron model and
 * picker as the calendar import ({@link CronSchedule}).
 *
 * When that cron is blank (installs that predate it) the schedule is derived from the legacy
 * {@code email.weekly-review.day} / {@code .time} settings, so the send time doesn't change on
 * upgrade. An invalid or too-frequent cron also falls back to the day/time-derived cron.
 * The digest content doesn't depend on the send day: it always covers the upcoming Sunday-Saturday.
 */
@Slf4j
@Service
public class WeeklyMinyanReviewEmailScheduler {

    private static final String JOB_NAME = "Weekly minyan review email";

    private final WeeklyMinyanReviewEmailService weeklyMinyanReviewEmailService;
    private final ApplicationSettingsService settingsService;
    private final ScheduledCronJob job;

    public WeeklyMinyanReviewEmailScheduler(
            WeeklyMinyanReviewEmailService weeklyMinyanReviewEmailService,
            ApplicationSettingsService settingsService,
            TaskScheduler taskScheduler) {
        this.weeklyMinyanReviewEmailService = weeklyMinyanReviewEmailService;
        this.settingsService = settingsService;
        this.job = new ScheduledCronJob(JOB_NAME, taskScheduler, this::sendWeeklyReviewEmails);
    }

    /**
     * (Re)schedules the weekly send from the current cron (or legacy day/time) and timezone settings.
     * Call after any of those settings change; the previous schedule is cancelled.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void reschedule() {
        job.schedule(effectiveCron(), effectiveZone());
    }

    /** Stored cron, or the cron derived from the legacy day/time settings when it can't be used. */
    public String effectiveCron() {
        return ScheduledCronJob.usableCronOr(JOB_NAME,
                settingsService::getWeeklyReviewEmailCron,
                settingsService::getWeeklyReviewEmailLegacyCron);
    }

    public ZoneId effectiveZone() {
        return ScheduledCronJob.zoneOrDefault(JOB_NAME, settingsService::getZoneId);
    }

    /** Summary verb and the picker's fallback (the legacy day/time schedule). */
    public CronSchedule.Profile scheduleProfile() {
        return new CronSchedule.Profile("Sends", CronSchedule.SimpleSchedule.weekly(
                settingsService.getWeeklyReviewEmailTime(), settingsService.getWeeklyReviewEmailDay()));
    }

    /** Current schedule for the Email Settings page. */
    public CronSchedule.View currentView() {
        ZoneId zone = effectiveZone();
        return CronSchedule.view(
                settingsService.getWeeklyReviewEmailCron(),
                settingsService.getWeeklyReviewEmailScheduleMode(),
                zone,
                ZonedDateTime.now(zone),
                scheduleProfile());
    }

    public void sendWeeklyReviewEmails() {
        WeeklyMinyanReviewEmailService.WeeklyMinyanReviewSendResult result =
                weeklyMinyanReviewEmailService.sendScheduledWeeklyReviewEmails();

        if (result.disabledBySetting()) {
            log.info("Weekly minyan review email job skipped for {} to {}: disabled by the email.weekly-review.enabled setting",
                    result.range().startDate(),
                    result.range().endDate());
            return;
        }

        log.info("Weekly minyan review email job completed for {} to {}: {} sent, {} failed, {} skipped",
                result.range().startDate(),
                result.range().endDate(),
                result.sentCount(),
                result.failedCount(),
                result.skippedCount());
    }
}
