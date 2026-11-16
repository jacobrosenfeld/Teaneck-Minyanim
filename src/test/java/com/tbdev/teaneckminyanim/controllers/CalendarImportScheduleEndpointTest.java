package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.enums.SettingKey;
import com.tbdev.teaneckminyanim.model.ApplicationSettings;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.repo.ApplicationSettingsRepository;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.VersionService;
import com.tbdev.teaneckminyanim.service.calendar.CalendarImportScheduler;
import com.tbdev.teaneckminyanim.service.schedule.CronSchedule;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CalendarImportScheduleEndpointTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private final Map<SettingKey, ApplicationSettings> rows = new EnumMap<>(SettingKey.class);
    private ApplicationSettingsRepository repository;
    private TNMUserService userService;
    private CalendarImportScheduler scheduler;
    private ApplicationSettingsController controller;

    @BeforeEach
    void setUp() {
        repository = mock(ApplicationSettingsRepository.class);
        for (SettingKey key : SettingKey.values()) {
            rows.put(key, new ApplicationSettings(key.getKey(), key.getDefaultValue(), key.getType().name()));
        }
        when(repository.findBySettingKey(anyString()))
                .thenAnswer(invocation -> Optional.of(rows.get(SettingKey.fromKey(invocation.getArgument(0)))));
        when(repository.save(any(ApplicationSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ApplicationSettingsService settingsService = new ApplicationSettingsService(repository);

        userService = mock(TNMUserService.class);
        scheduler = mock(CalendarImportScheduler.class);
        when(scheduler.effectiveZone()).thenReturn(NEW_YORK);
        when(scheduler.currentView()).thenAnswer(invocation -> CronSchedule.view(
                settingsService.getCalendarImportCron(), settingsService.getCalendarImportScheduleMode(),
                NEW_YORK, ZonedDateTime.now(NEW_YORK), CalendarImportScheduler.SCHEDULE_PROFILE));
        controller = new ApplicationSettingsController(settingsService, userService, mock(VersionService.class), scheduler);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void savingScheduleReschedulesImportAndReturnsPreview() {
        signInAs(null);

        ResponseEntity<CronScheduleResponse> response =
                controller.saveCalendarImportSchedule("SIMPLE", "WEEKLY", List.of("SUN", "WED"), "02:00", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().success());
        assertTrue(response.getBody().valid());
        assertEquals("0 0 2 * * SUN,WED", response.getBody().cron());
        assertEquals("Runs every Sunday and Wednesday at 2:00 AM (America/New_York)", response.getBody().summary());
        assertEquals(List.of("SUN", "WED"), response.getBody().simple().days());
        assertEquals("0 0 2 * * SUN,WED", rows.get(SettingKey.CALENDAR_IMPORT_CRON).getSettingValue());
        verify(scheduler).reschedule();
    }

    @Test
    void invalidSimpleScheduleIsRejectedWithoutSavingOrRescheduling() {
        signInAs(null);

        ResponseEntity<CronScheduleResponse> response =
                controller.saveCalendarImportSchedule("SIMPLE", "WEEKLY", null, "02:00", null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(response.getBody().success());
        assertEquals("Choose at least one day for a weekly schedule.", response.getBody().message());
        assertNothingSaved();
    }

    @Test
    void invalidAndTooFrequentCronsAreRejectedWithoutSaving() {
        signInAs(null);

        for (String cron : List.of("not a cron", "0 0 25 * * *", "", "* * * * * *", "0 */5 * * * *")) {
            ResponseEntity<CronScheduleResponse> response =
                    controller.saveCalendarImportSchedule("ADVANCED", null, null, null, cron);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), cron);
            assertFalse(response.getBody().success(), cron);
            assertFalse(response.getBody().valid(), cron);
            assertEquals(CronSchedule.validationError(cron), response.getBody().message(), cron);
        }
        assertNothingSaved();
    }

    @Test
    void orgManagerCannotSaveOrPreviewSchedule() {
        signInAs("org-a");

        assertThrows(AccessDeniedException.class,
                () -> controller.saveCalendarImportSchedule("ADVANCED", null, null, null, "0 0 3 * * *"));
        assertThrows(AccessDeniedException.class,
                () -> controller.previewCalendarImportSchedule("ADVANCED", null, null, null, "0 0 3 * * *"));
        assertNothingSaved();
    }

    @Test
    void previewParsesSimpleCronBackIntoPicker() {
        signInAs(null);

        var body = controller.previewCalendarImportSchedule("ADVANCED", null, null, null, "0 30 3 * * MON-FRI").getBody();

        assertTrue(body.success());
        assertTrue(body.valid());
        assertEquals("WEEKLY", body.simple().frequency());
        assertEquals(List.of("MON", "TUE", "WED", "THU", "FRI"), body.simple().days());
        assertEquals("03:30", body.simple().time());
        assertTrue(body.nextRun().endsWith("(America/New_York)"));
    }

    @Test
    void previewReportsCustomCronThatCannotBeShownSimply() {
        signInAs(null);

        var body = controller.previewCalendarImportSchedule("ADVANCED", null, null, null, "0 0 */6 * * *").getBody();

        assertTrue(body.valid());
        assertNull(body.simple());
        assertEquals("ADVANCED", body.mode());
    }

    @Test
    void previewFlagsInvalidAndTooFrequentCronsWithReason() {
        signInAs(null);

        var syntax = controller.previewCalendarImportSchedule("ADVANCED", null, null, null, "nope").getBody();
        assertFalse(syntax.valid());
        assertTrue(syntax.message().startsWith("Not a valid cron expression"), syntax.message());
        assertNull(syntax.nextRun());

        var empty = controller.previewCalendarImportSchedule("ADVANCED", null, null, null, "  ").getBody();
        assertFalse(empty.valid());
        assertEquals("Enter a cron expression.", empty.message());

        var tooOften = controller.previewCalendarImportSchedule("ADVANCED", null, null, null, "0 */5 * * * *").getBody();
        assertFalse(tooOften.valid());
        assertTrue(tooOften.message().contains("60 minutes"), tooOften.message());

        var noDays = controller.previewCalendarImportSchedule("SIMPLE", "WEEKLY", List.of(), "02:00", null).getBody();
        assertFalse(noDays.valid());
        assertTrue(noDays.message().contains("at least one day"));
    }

    @Test
    void savingCronOrTimezoneThroughGenericEndpointReschedules() {
        signInAs(null);

        controller.updateSetting(SettingKey.CALENDAR_IMPORT_CRON.getKey(), "0 0 4 * * *", new RedirectAttributesModelMap());
        controller.updateSetting(SettingKey.SITE_NAME.getKey(), "Site", new RedirectAttributesModelMap());

        verify(scheduler).reschedule();
    }

    private void assertNothingSaved() {
        verify(repository, never()).save(any(ApplicationSettings.class));
        verify(scheduler, never()).reschedule();
        assertEquals("0 0 2 * * SUN", rows.get(SettingKey.CALENDAR_IMPORT_CRON).getSettingValue());
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
