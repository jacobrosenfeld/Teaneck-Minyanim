package com.tbdev.teaneckminyanim.service.email;

import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyMinyanReviewEmailScheduler {

    private final WeeklyMinyanReviewEmailService weeklyMinyanReviewEmailService;
    private final ApplicationSettingsService settingsService;
    private final TaskScheduler taskScheduler;

    private ScheduledFuture<?> scheduledRun;

    /**
     * (Re)schedules the weekly send from the current day, time and timezone settings.
     * Call after any of those settings change; the previous schedule is cancelled.
     */
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void reschedule() {
        if (scheduledRun != null) {
            scheduledRun.cancel(false);
        }
        DayOfWeek day = settingsService.getWeeklyReviewEmailDay();
        LocalTime time = settingsService.getWeeklyReviewEmailTime();
        ZoneId zone = settingsService.getZoneId();
        scheduledRun = taskScheduler.schedule(this::sendWeeklyReviewEmails, cronTrigger(day, time, zone));
        log.info("Weekly minyan review emails scheduled for {} at {} ({})", day, time, zone);
    }

    static CronTrigger cronTrigger(DayOfWeek day, LocalTime time, ZoneId zone) {
        String cron = String.format("0 %d %d * * %s",
                time.getMinute(), time.getHour(), day.name().substring(0, 3));
        return new CronTrigger(cron, zone);
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
