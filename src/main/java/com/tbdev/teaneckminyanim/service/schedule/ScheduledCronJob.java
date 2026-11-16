package com.tbdev.teaneckminyanim.service.schedule;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Supplier;

/**
 * One job registered on the shared {@link TaskScheduler} with a cron that can change at
 * runtime. {@link #schedule(String, ZoneId)} cancels the pending run (letting a run already
 * in progress finish) and registers the job again with the new cron and zone.
 */
@Slf4j
public class ScheduledCronJob {

    private final String name;
    private final TaskScheduler taskScheduler;
    private final Runnable job;
    private ScheduledFuture<?> scheduledRun;

    public ScheduledCronJob(String name, TaskScheduler taskScheduler, Runnable job) {
        this.name = name;
        this.taskScheduler = taskScheduler;
        this.job = job;
    }

    public synchronized void schedule(String cron, ZoneId zone) {
        if (scheduledRun != null) {
            scheduledRun.cancel(false);
        }
        scheduledRun = taskScheduler.schedule(job, new CronTrigger(cron, zone));
        log.info("{} scheduled with cron '{}' ({}); next run {}",
                name, cron, zone, CronSchedule.nextRun(cron, zone, ZonedDateTime.now(zone)).orElse(null));
    }

    /**
     * The stored cron when it passes {@link CronSchedule#validationError(String)}, otherwise the
     * fallback, with a warning naming the job.
     */
    public static String usableCronOr(String jobName, Supplier<String> stored, Supplier<String> fallback) {
        String value = null;
        try {
            value = stored.get();
        } catch (RuntimeException e) {
            log.warn("Could not read the {} cron setting: {}", jobName, e.getMessage());
        }
        String error = CronSchedule.validationError(value);
        if (error == null) {
            return value.trim();
        }
        String replacement = fallback.get();
        log.warn("{} cron '{}' can't be used ({}); falling back to '{}'", jobName, value, error, replacement);
        return replacement;
    }

    /** The site timezone, or the JVM default (America/New_York, set at startup) when it is invalid. */
    public static ZoneId zoneOrDefault(String jobName, Supplier<ZoneId> siteZone) {
        try {
            return siteZone.get();
        } catch (RuntimeException e) {
            ZoneId fallback = ZoneId.systemDefault();
            log.warn("Invalid timezone setting for the {} schedule; using {}: {}", jobName, fallback, e.getMessage());
            return fallback;
        }
    }
}
