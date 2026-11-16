package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.calendar.CalendarImportScheduler;
import com.tbdev.teaneckminyanim.service.schedule.CronSchedule;
import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Renders the shared schedule-picker fragment on the Settings and Email Settings pages. */
class SchedulePickerTemplateTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final String IMPORT_FORM = "import-schedule-form";
    private static final String EMAIL_FORM = "weekly-review-schedule-form";
    private static final CronSchedule.Profile SENDS = new CronSchedule.Profile(
            "Sends", CronSchedule.SimpleSchedule.weekly(LocalTime.of(22, 0), DayOfWeek.SATURDAY));

    @Test
    void importPickerOpensInSimpleModeWithPickerFilled() {
        String html = renderSettings(importView("0 30 3 * * SUN,WED", CronSchedule.Mode.SIMPLE));

        assertFalse(openingTag(html, IMPORT_FORM + "-simple").contains("hidden"));
        assertTrue(openingTag(html, IMPORT_FORM + "-advanced-block").contains("hidden"));
        assertFalse(openingTag(html, IMPORT_FORM + "-advanced").contains("checked"));
        assertTrue(openingTag(html, IMPORT_FORM + "-time").contains("value=\"03:30\""));
        assertTrue(checkbox(html, IMPORT_FORM, "SUN").contains("checked"));
        assertTrue(checkbox(html, IMPORT_FORM, "WED").contains("checked"));
        assertFalse(checkbox(html, IMPORT_FORM, "MON").contains("checked"));
        assertTrue(html.contains("Runs every Sunday and Wednesday at 3:30 AM (America/New_York)"));
        assertTrue(html.contains("Next run: "));
        String form = openingTag(html, IMPORT_FORM);
        assertTrue(form.contains("data-schedule-picker"));
        assertTrue(form.contains("action=\"/admin/settings/calendar-import-schedule\""));
        assertTrue(form.contains("data-preview-url=\"/admin/settings/calendar-import-schedule/preview\""));
        assertTrue(html.contains("/admin/schedule-picker.js"));
        assertTrue(html.contains("/admin/schedule-picker.css"));
        // The cron and mode keys are edited by the picker, not as generic setting rows.
        assertFalse(html.contains("data-key=\"calendar.import.cron\""));
    }

    @Test
    void importPickerOpensInAdvancedModeForCustomCron() {
        String html = renderSettings(importView("0 0 */6 * * *", CronSchedule.Mode.SIMPLE));

        assertTrue(openingTag(html, IMPORT_FORM + "-simple").contains("hidden"));
        assertFalse(openingTag(html, IMPORT_FORM + "-advanced-block").contains("hidden"));
        assertTrue(openingTag(html, IMPORT_FORM + "-advanced").contains("checked"));
        assertTrue(openingTag(html, IMPORT_FORM + "-cron").contains("value=\"0 0 */6 * * *\""));
        assertTrue(html.contains("Runs on a custom schedule: 0 0 */6 * * * (America/New_York)"));
    }

    @Test
    void cronFieldHasAccessibleErrorWiring() {
        String html = renderSettings(importView("0 0 */6 * * *", CronSchedule.Mode.ADVANCED));

        String cron = openingTag(html, IMPORT_FORM + "-cron");
        assertTrue(cron.contains("aria-describedby=\"" + IMPORT_FORM + "-cron-error " + IMPORT_FORM + "-cron-help\""), cron);
        assertTrue(cron.contains("aria-invalid=\"false\""), cron);
        assertFalse(cron.contains("is-invalid"), cron);
        assertTrue(openingTag(html, IMPORT_FORM + "-cron-error").contains("aria-live=\"polite\""));
        assertTrue(html.contains("id=\"" + IMPORT_FORM + "-cron-help\""));
        assertTrue(html.contains("id=\"" + IMPORT_FORM + "-simple-error\""));
        assertFalse(saveButton(html, IMPORT_FORM).contains("disabled"));
    }

    @Test
    void invalidStoredCronRendersErrorAndDisablesSave() {
        String html = renderSettings(importView("0 */5 * * * *", CronSchedule.Mode.ADVANCED));

        String cron = openingTag(html, IMPORT_FORM + "-cron");
        assertTrue(cron.contains("is-invalid"), cron);
        assertTrue(cron.contains("aria-invalid=\"true\""), cron);
        assertTrue(textOf(html, IMPORT_FORM + "-cron-error").contains("60 minutes"));
        assertTrue(openingTag(html, IMPORT_FORM).contains("data-valid=\"false\""));
        assertTrue(saveButton(html, IMPORT_FORM).contains("disabled"));
    }

    @Test
    void emailPageRendersTheSamePickerInsteadOfDayAndTimeFields() {
        CronSchedule.View view = CronSchedule.view("0 0 22 * * SAT", CronSchedule.Mode.SIMPLE, NEW_YORK,
                ZonedDateTime.now(NEW_YORK), SENDS);
        String html = renderEmailSettings(view);

        String form = openingTag(html, EMAIL_FORM);
        assertTrue(form.contains("data-schedule-picker"));
        assertTrue(form.contains("action=\"/admin/settings/email/weekly-review-schedule\""));
        assertTrue(form.contains("data-preview-url=\"/admin/settings/email/weekly-review-schedule/preview\""));
        assertTrue(checkbox(html, EMAIL_FORM, "SAT").contains("checked"));
        assertTrue(openingTag(html, EMAIL_FORM + "-time").contains("value=\"22:00\""));
        assertTrue(html.contains("Sends every Saturday at 10:00 PM (America/New_York)"));
        assertTrue(html.contains("always covers the upcoming Sunday through Saturday"));
        assertTrue(openingTag(html, EMAIL_FORM + "-cron")
                .contains("aria-describedby=\"" + EMAIL_FORM + "-cron-error " + EMAIL_FORM + "-cron-help\""));
        assertTrue(html.contains("/admin/schedule-picker.js"));
        assertTrue(html.contains("/admin/schedule-picker.css"));
        // The legacy day/time rows are gone; the Enabled toggle stays.
        assertFalse(html.contains("data-key=\"email.weekly-review.day\""));
        assertFalse(html.contains("data-key=\"email.weekly-review.time\""));
        assertTrue(html.contains("data-key=\"email.weekly-review.enabled\""));
    }

    private static CronSchedule.View importView(String cron, CronSchedule.Mode mode) {
        return CronSchedule.view(cron, mode, NEW_YORK, ZonedDateTime.now(NEW_YORK), CalendarImportScheduler.SCHEDULE_PROFILE);
    }

    private static String renderSettings(CronSchedule.View view) {
        return render("admin/settings", Map.of(
                "emailProvider", "",
                "importSchedule", view,
                "settingSections", List.of(new AdminController.SettingsPageSection(
                        "calendar-import", "Calendar Import", "Tune scheduled imports.", "fa-calendar-days",
                        List.of(new AdminController.SettingsPageField(
                                "calendar.cleanup.days", "setting-calendar-cleanup-days", "Cleanup", "Days",
                                "number", "1", "30", "30", false, "30", List.of(), false, false))))));
    }

    private static String renderEmailSettings(CronSchedule.View view) {
        return render("admin/email-settings", Map.of(
                "emailProvider", "SMTP",
                "allOrganizations", List.of(),
                "weeklyReviewSchedule", view,
                "sections", List.of(new EmailSettingsController.EmailSettingSection(
                        "weekly-review", "Weekly Review Emails", "Control when the digest is sent.", "fa-calendar-week",
                        List.of(new EmailSettingsController.EmailSettingField(
                                "email.weekly-review.enabled", "Scheduled Weekly Review Emails", "Whether it sends",
                                "select", "true", "true", false, "Enter value",
                                List.of(new EmailSettingsController.SettingOption("true", "Enabled"),
                                        new EmailSettingsController.SettingOption("false", "Disabled")),
                                true))))));
    }

    private static String render(String template, Map<String, Object> variables) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.addDialect(new LayoutDialect());

        MockServletContext servletContext = new MockServletContext();
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext, "GET", "/admin/settings");
        WebContext context = new WebContext(JakartaServletWebApplication.buildApplication(servletContext)
                .buildExchange(request, new MockHttpServletResponse()));
        context.setVariable("user", TNMUser.builder().id("A0").username("admin").roleId(Role.ADMIN.getId()).build());
        context.setVariable("siteName", "Teaneck Minyanim");
        context.setVariable("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "token"));
        context.setVariable("gtmId", "");
        variables.forEach(context::setVariable);
        return engine.process(template, context);
    }

    private static String openingTag(String html, String id) {
        Matcher matcher = Pattern.compile("<[^>]*\\bid=\"" + Pattern.quote(id) + "\"[^>]*>").matcher(html);
        assertTrue(matcher.find(), "missing #" + id);
        String tag = matcher.group();
        assertFalse(matcher.find(), "duplicate #" + id);
        return tag;
    }

    private static String textOf(String html, String id) {
        Matcher matcher = Pattern.compile("<([a-z]+)[^>]*\\bid=\"" + Pattern.quote(id) + "\"[^>]*>([^<]*)</\\1>").matcher(html);
        assertTrue(matcher.find(), "missing #" + id);
        return matcher.group(2);
    }

    private static String checkbox(String html, String formId, String day) {
        String form = html.substring(html.indexOf("id=\"" + formId + "\""));
        Matcher matcher = Pattern.compile("<input[^>]*name=\"days\"[^>]*value=\"" + day + "\"[^>]*>").matcher(form);
        assertTrue(matcher.find(), "missing day " + day);
        return matcher.group();
    }

    private static String saveButton(String html, String formId) {
        String form = html.substring(html.indexOf("id=\"" + formId + "\""));
        Matcher matcher = Pattern.compile("<button[^>]*data-role=\"save\"[^>]*>").matcher(form);
        assertTrue(matcher.find(), "missing save button");
        assertEquals(-1, matcher.group().indexOf("data-role=\"discard\""));
        return matcher.group();
    }
}
