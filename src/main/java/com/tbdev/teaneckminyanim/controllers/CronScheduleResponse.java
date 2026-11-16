package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.service.schedule.CronSchedule;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * JSON returned by the schedule picker's save and preview endpoints (calendar import and
 * weekly review email).
 *
 * @param success  whether the request did what was asked (saved, or produced a usable preview)
 * @param valid    whether the schedule is usable; false means {@code message} says why
 * @param message  error or status text, shown inline next to the picker
 * @param simple   picker values for the cron, or null when it can't be shown in simple mode
 */
public record CronScheduleResponse(
        boolean success,
        boolean valid,
        String message,
        String mode,
        String cron,
        String summary,
        String nextRun,
        String timezone,
        SimpleFields simple) {

    public record SimpleFields(String frequency, List<String> days, String time) {
    }

    /**
     * Shared preview logic: builds the cron from the picker values (SIMPLE) or takes it as
     * entered (ADVANCED), then validates it with {@link CronSchedule#validationError(String)},
     * the same check the save endpoints use.
     */
    public static CronScheduleResponse preview(
            String mode,
            String frequency,
            List<String> days,
            String time,
            String cron,
            ZoneId zone,
            CronSchedule.Profile profile) {
        try {
            CronSchedule.Mode parsedMode = CronSchedule.Mode.fromString(mode);
            String previewCron = parsedMode == CronSchedule.Mode.SIMPLE
                    ? CronSchedule.toCron(CronSchedule.fromForm(frequency, days, time))
                    : (cron == null ? "" : cron.trim());
            CronSchedule.View view = CronSchedule.view(previewCron, parsedMode, zone, ZonedDateTime.now(zone), profile);
            return view.valid() ? fromView(true, view, null) : fromView(false, view, view.error());
        } catch (IllegalArgumentException e) {
            return failure(e.getMessage());
        }
    }

    public static CronScheduleResponse saved(CronSchedule.View view) {
        return fromView(true, view, "Saved");
    }

    public static CronScheduleResponse failure(String message) {
        return new CronScheduleResponse(false, false, message, null, null, null, null, null, null);
    }

    private static CronScheduleResponse fromView(boolean success, CronSchedule.View view, String message) {
        SimpleFields simple = view.simpleCompatible()
                ? new SimpleFields(view.simple().frequency().name(), view.simple().dayCodes(), view.simple().timeValue())
                : null;
        return new CronScheduleResponse(success, view.valid(), message, view.mode().name(), view.cron(),
                view.summary(), view.nextRun(), view.timezone(), simple);
    }
}
