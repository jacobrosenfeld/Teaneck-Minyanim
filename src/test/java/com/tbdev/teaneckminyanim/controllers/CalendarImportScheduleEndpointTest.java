package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.enums.SettingKey;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.VersionService;
import com.tbdev.teaneckminyanim.service.calendar.CalendarImportSchedule;
import com.tbdev.teaneckminyanim.service.calendar.CalendarImportScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CalendarImportScheduleEndpointTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private ApplicationSettingsService settingsService;
    private TNMUserService userService;
    private CalendarImportScheduler scheduler;
    private ApplicationSettingsController controller;

    @BeforeEach
    void setUp() {
        settingsService = mock(ApplicationSettingsService.class);
        userService = mock(TNMUserService.class);
        scheduler = mock(CalendarImportScheduler.class);
        when(scheduler.effectiveZone()).thenReturn(NEW_YORK);
        controller = new ApplicationSettingsController(settingsService, userService, mock(VersionService.class), scheduler);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void savingScheduleReschedulesImportAndReturnsPreview() throws Exception {
        signInAs(null);
        when(settingsService.saveCalendarImportSchedule("SIMPLE", "WEEKLY", List.of("SUN", "WED"), "02:00", null))
                .thenReturn("0 0 2 * * SUN,WED");
        when(scheduler.currentView()).thenReturn(CalendarImportSchedule.view(
                "0 0 2 * * SUN,WED", CalendarImportSchedule.Mode.SIMPLE, NEW_YORK, ZonedDateTime.now(NEW_YORK)));

        ResponseEntity<ApplicationSettingsController.ImportScheduleResponse> response =
                controller.saveCalendarImportSchedule("SIMPLE", "WEEKLY", List.of("SUN", "WED"), "02:00", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().success());
        assertEquals("0 0 2 * * SUN,WED", response.getBody().cron());
        assertEquals("Runs every Sunday and Wednesday at 2:00 AM (America/New_York)", response.getBody().summary());
        assertEquals(List.of("SUN", "WED"), response.getBody().simple().days());
        verify(scheduler).reschedule();
    }

    @Test
    void invalidScheduleIsRejectedWithoutRescheduling() throws Exception {
        signInAs(null);
        when(settingsService.saveCalendarImportSchedule("SIMPLE", "WEEKLY", null, "02:00", null))
                .thenThrow(new ApplicationSettingsService.ValidationException("Choose at least one day for a weekly schedule."));

        ResponseEntity<ApplicationSettingsController.ImportScheduleResponse> response =
                controller.saveCalendarImportSchedule("SIMPLE", "WEEKLY", null, "02:00", null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(response.getBody().success());
        assertEquals("Choose at least one day for a weekly schedule.", response.getBody().message());
        verify(scheduler, never()).reschedule();
    }

    @Test
    void orgManagerCannotSaveSchedule() throws Exception {
        signInAs("org-a");

        assertThrows(AccessDeniedException.class,
                () -> controller.saveCalendarImportSchedule("ADVANCED", null, null, null, "0 0 3 * * *"));
        verify(settingsService, never()).saveCalendarImportSchedule(any(), any(), any(), any(), any());
        verify(scheduler, never()).reschedule();
    }

    @Test
    void previewParsesSimpleCronBackIntoPicker() {
        signInAs(null);

        var body = controller.previewCalendarImportSchedule("ADVANCED", null, null, null, "0 30 3 * * MON-FRI").getBody();

        assertTrue(body.success());
        assertEquals("WEEKLY", body.simple().frequency());
        assertEquals(List.of("MON", "TUE", "WED", "THU", "FRI"), body.simple().days());
        assertEquals("03:30", body.simple().time());
        assertTrue(body.nextRun().endsWith("(America/New_York)"));
    }

    @Test
    void previewReportsCustomCronThatCannotBeShownSimply() {
        signInAs(null);

        var body = controller.previewCalendarImportSchedule("ADVANCED", null, null, null, "0 0 */6 * * *").getBody();

        assertTrue(body.success());
        assertNull(body.simple());
        assertEquals("ADVANCED", body.mode());
    }

    @Test
    void previewReportsInvalidInput() {
        signInAs(null);

        assertFalse(controller.previewCalendarImportSchedule("ADVANCED", null, null, null, "nope").getBody().success());
        var noDays = controller.previewCalendarImportSchedule("SIMPLE", "WEEKLY", List.of(), "02:00", null).getBody();
        assertFalse(noDays.success());
        assertTrue(noDays.message().contains("at least one day"));
    }

    @Test
    void savingCronOrTimezoneThroughGenericEndpointReschedules() throws Exception {
        signInAs(null);

        controller.updateSetting(SettingKey.CALENDAR_IMPORT_CRON.getKey(), "0 0 4 * * *", new RedirectAttributesModelMap());
        controller.updateSetting(SettingKey.SITE_NAME.getKey(), "Site", new RedirectAttributesModelMap());

        verify(scheduler).reschedule();
    }

    private void signInAs(String organizationId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", "password", List.of(new SimpleGrantedAuthority(Role.ADMIN.getName()))));
        when(userService.findByName("admin")).thenReturn(TNMUser.builder()
                .id("A0")
                .username("admin")
                .email("admin@example.com")
                .organizationId(organizationId)
                .roleId(Role.ADMIN.getId())
                .enabled(true)
                .build());
    }
}
