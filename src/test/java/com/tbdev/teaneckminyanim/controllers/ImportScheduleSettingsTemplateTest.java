package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.calendar.CalendarImportSchedule;
import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportScheduleSettingsTemplateTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    @Test
    void simpleCronOpensInSimpleModeWithPickerFilled() {
        String html = renderSettings("0 30 3 * * SUN,WED", CalendarImportSchedule.Mode.SIMPLE);

        assertFalse(openingTag(html, "schedule-simple").contains("hidden"));
        assertTrue(openingTag(html, "schedule-advanced-block").contains("hidden"));
        assertFalse(openingTag(html, "schedule-advanced").contains("checked"));
        assertTrue(html.contains("value=\"03:30\""));
        assertTrue(checkbox(html, "SUN").contains("checked"));
        assertTrue(checkbox(html, "WED").contains("checked"));
        assertFalse(checkbox(html, "MON").contains("checked"));
        assertTrue(html.contains("Runs every Sunday and Wednesday at 3:30 AM (America/New_York)"));
        assertTrue(html.contains("Next run: "));
        // The cron and mode keys are edited by the picker, not as generic setting rows.
        assertFalse(html.contains("data-key=\"calendar.import.cron\""));
    }

    @Test
    void customCronOpensInAdvancedMode() {
        String html = renderSettings("0 0 */6 * * *", CalendarImportSchedule.Mode.SIMPLE);

        assertTrue(openingTag(html, "schedule-simple").contains("hidden"));
        assertFalse(openingTag(html, "schedule-advanced-block").contains("hidden"));
        assertTrue(openingTag(html, "schedule-advanced").contains("checked"));
        assertTrue(html.contains("value=\"0 0 */6 * * *\""));
        assertTrue(html.contains("Runs on a custom schedule: 0 0 */6 * * * (America/New_York)"));
    }

    private static String renderSettings(String cron, CalendarImportSchedule.Mode mode) {
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
        context.setVariable("_csrf", new org.springframework.security.web.csrf.DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "token"));
        context.setVariable("gtmId", "");
        context.setVariable("emailProvider", "");
        context.setVariable("importSchedule",
                CalendarImportSchedule.view(cron, mode, NEW_YORK, ZonedDateTime.now(NEW_YORK)));
        context.setVariable("settingSections", List.of(new AdminController.SettingsPageSection(
                "calendar-import", "Calendar Import", "Tune scheduled imports.", "fa-calendar-days",
                List.of(new AdminController.SettingsPageField(
                        "calendar.cleanup.days", "setting-calendar-cleanup-days", "Cleanup", "Days",
                        "number", "1", "30", "30", false, "30", List.of(), false, false)))));
        return engine.process("admin/settings", context);
    }

    private static String openingTag(String html, String id) {
        Matcher matcher = Pattern.compile("<[^>]*\\bid=\"" + Pattern.quote(id) + "\"[^>]*>").matcher(html);
        assertTrue(matcher.find(), "missing #" + id);
        return matcher.group();
    }

    private static String checkbox(String html, String day) {
        Matcher matcher = Pattern.compile("<input[^>]*name=\"days\"[^>]*value=\"" + day + "\"[^>]*>").matcher(html);
        assertTrue(matcher.find(), "missing day " + day);
        return matcher.group();
    }
}
