package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.enums.SettingKey;
import com.tbdev.teaneckminyanim.model.ApplicationSettings;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.repo.ApplicationSettingsRepository;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.VersionService;
import com.tbdev.teaneckminyanim.service.email.WeeklyMinyanReviewEmailScheduler;
import com.tbdev.teaneckminyanim.service.email.WeeklyMinyanReviewEmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.mockito.ArgumentCaptor;

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

/**
 * Save/preview endpoints for the weekly review email schedule, wired to a real settings service
 * and a real {@link WeeklyMinyanReviewEmailScheduler} (with a mock TaskScheduler).
 */
class WeeklyReviewScheduleEndpointTest {

    private final Map<SettingKey, ApplicationSettings> rows = new EnumMap<>(SettingKey.class);
    private ApplicationSettingsRepository repository;
    private TaskScheduler taskScheduler;
    private TNMUserService userService;
    private EmailSettingsController controller;

    @BeforeEach
    void setUp() {
        repository = mock(ApplicationSettingsRepository.class);
        for (SettingKey key : SettingKey.values()) {
            rows.put(key, new ApplicationSettings(key.getKey(), key.getDefaultValue(), key.getType().name()));
        }
        rows.get(SettingKey.TIMEZONE).setSettingValue("America/New_York");
        when(repository.findBySettingKey(anyString()))
                .thenAnswer(invocation -> Optional.of(rows.get(SettingKey.fromKey(invocation.getArgument(0)))));
        when(repository.save(any(ApplicationSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ApplicationSettingsService settingsService = new ApplicationSettingsService(repository);

        taskScheduler = mock(TaskScheduler.class);
        WeeklyMinyanReviewEmailScheduler scheduler = new WeeklyMinyanReviewEmailScheduler(
                mock(WeeklyMinyanReviewEmailService.class), settingsService, taskScheduler);
        userService = mock(TNMUserService.class);
        controller = new EmailSettingsController(settingsService, userService, mock(OrganizationService.class),
                mock(VersionService.class), scheduler);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void savingSimpleScheduleStoresCronAndReschedulesTheEmail() {
        signInAs(null);

        ResponseEntity<CronScheduleResponse> response =
                controller.saveWeeklyReviewSchedule("SIMPLE", "WEEKLY", List.of("SUN", "THU"), "08:30", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().success());
        assertEquals("0 30 8 * * SUN,THU", response.getBody().cron());
        assertEquals("Sends every Sunday and Thursday at 8:30 AM (America/New_York)", response.getBody().summary());
        assertEquals("0 30 8 * * SUN,THU", rows.get(SettingKey.EMAIL_WEEKLY_REVIEW_CRON).getSettingValue());
        assertEquals("SIMPLE", rows.get(SettingKey.EMAIL_WEEKLY_REVIEW_SCHEDULE_MODE).getSettingValue());

        ArgumentCaptor<Trigger> trigger = ArgumentCaptor.forClass(Trigger.class);
        verify(taskScheduler).schedule(any(Runnable.class), trigger.capture());
        assertEquals("0 30 8 * * SUN,THU", ((CronTrigger) trigger.getValue()).getExpression());
    }

    @Test
    void invalidAndTooFrequentCronsReturn400AndSaveNothing() {
        signInAs(null);

        for (String cron : List.of("garbage", "0 0 2 * *", "", "* * * * * *", "0 */5 * * * *")) {
            ResponseEntity<CronScheduleResponse> response =
                    controller.saveWeeklyReviewSchedule("ADVANCED", null, null, null, cron);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), cron);
            assertFalse(response.getBody().success(), cron);
            assertFalse(response.getBody().valid(), cron);
            assertTrue(response.getBody().message() != null && !response.getBody().message().isBlank(), cron);
        }
        ResponseEntity<CronScheduleResponse> noDays =
                controller.saveWeeklyReviewSchedule("SIMPLE", "WEEKLY", List.of(), "08:30", null);
        assertEquals(HttpStatus.BAD_REQUEST, noDays.getStatusCode());

        verify(repository, never()).save(any(ApplicationSettings.class));
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Trigger.class));
        assertEquals("", rows.get(SettingKey.EMAIL_WEEKLY_REVIEW_CRON).getSettingValue());
    }

    @Test
    void orgManagerCannotSaveOrPreview() {
        signInAs("org-a");

        assertThrows(AccessDeniedException.class,
                () -> controller.saveWeeklyReviewSchedule("SIMPLE", "DAILY", null, "08:30", null));
        assertThrows(AccessDeniedException.class,
                () -> controller.previewWeeklyReviewSchedule("SIMPLE", "DAILY", null, "08:30", null));
        verify(repository, never()).save(any(ApplicationSettings.class));
    }

    @Test
    void previewUsesSendsVerbAndFlagsInvalidCrons() {
        signInAs(null);

        var daily = controller.previewWeeklyReviewSchedule("SIMPLE", "DAILY", null, "06:45", null).getBody();
        assertTrue(daily.valid());
        assertEquals("Sends every day at 6:45 AM (America/New_York)", daily.summary());

        var tooOften = controller.previewWeeklyReviewSchedule("ADVANCED", null, null, null, "0 0,30 * * * *").getBody();
        assertFalse(tooOften.valid());
        assertTrue(tooOften.message().contains("60 minutes"));
        assertNull(tooOften.nextRun());
    }

    @Test
    void pageViewDerivesScheduleFromLegacyDayAndTimeWhenCronIsBlank() {
        signInAs(null);
        rows.get(SettingKey.EMAIL_WEEKLY_REVIEW_DAY).setSettingValue("SUNDAY");
        rows.get(SettingKey.EMAIL_WEEKLY_REVIEW_TIME).setSettingValue("08:30");

        var view = (com.tbdev.teaneckminyanim.service.schedule.CronSchedule.View)
                controller.emailSettings().getModel().get("weeklyReviewSchedule");

        assertEquals("0 30 8 * * SUN", view.cron());
        assertFalse(view.advanced());
        assertEquals("Sends every Sunday at 8:30 AM (America/New_York)", view.summary());
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
