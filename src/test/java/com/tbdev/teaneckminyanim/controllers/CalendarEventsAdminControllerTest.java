package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.repo.CalendarEventRepository;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.CalendarMaterializationScheduler;
import com.tbdev.teaneckminyanim.service.CalendarMaterializationService;
import com.tbdev.teaneckminyanim.service.EffectiveScheduleService;
import com.tbdev.teaneckminyanim.service.LocationService;
import com.tbdev.teaneckminyanim.service.ManualOverrideService;
import com.tbdev.teaneckminyanim.service.NotManualOverrideException;
import com.tbdev.teaneckminyanim.service.OverrideNotFoundException;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import com.tbdev.teaneckminyanim.service.SuperAdminOverrideXlsxService;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.web.servlet.view.RedirectView;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CalendarEventsAdminControllerTest {

    private final CalendarEventRepository calendarEventRepository = mock(CalendarEventRepository.class);
    private final OrganizationService organizationService = mock(OrganizationService.class);
    private final LocationService locationService = mock(LocationService.class);
    private final CalendarMaterializationService materializationService = mock(CalendarMaterializationService.class);
    private final CalendarMaterializationScheduler materializationScheduler = mock(CalendarMaterializationScheduler.class);
    private final TNMUserService userService = mock(TNMUserService.class);
    private final ApplicationSettingsService settingsService = mock(ApplicationSettingsService.class);
    private final EffectiveScheduleService effectiveScheduleService = mock(EffectiveScheduleService.class);
    private final SuperAdminOverrideXlsxService overrideXlsxService = mock(SuperAdminOverrideXlsxService.class);
    private final ManualOverrideService manualOverrideService = mock(ManualOverrideService.class);

    private final CalendarEventsAdminController controller = new CalendarEventsAdminController(
            calendarEventRepository,
            organizationService,
            locationService,
            materializationService,
            materializationScheduler,
            userService,
            settingsService,
            effectiveScheduleService,
            overrideXlsxService,
            manualOverrideService);

    @Test
    void rematerializeAll_triggersSchedulerAndReturnsToMasterCalendar() {
        when(userService.isSuperAdmin()).thenReturn(true);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        RedirectView redirectView = controller.rematerializeAll(redirectAttributes);

        verify(materializationScheduler).triggerMaterialization();
        assertEquals("/admin/calendar-events/all", redirectView.getUrl());
        assertEquals(
                "Full calendar rematerialization completed successfully.",
                redirectAttributes.getFlashAttributes().get("successMessage"));
    }

    @Test
    void rematerializeAll_requiresSuperAdmin() {
        when(userService.isSuperAdmin()).thenReturn(false);

        assertThrows(
                AccessDeniedException.class,
                () -> controller.rematerializeAll(new RedirectAttributesModelMap()));

        verify(materializationScheduler, never()).triggerMaterialization();
    }

    @Test
    void manualCreate_delegatesToTheServiceAndReportsCounts() {
        when(userService.getCurrentUser()).thenReturn(com.tbdev.teaneckminyanim.model.TNMUser.builder().username("alice").build());
        when(manualOverrideService.create(org.mockito.ArgumentMatchers.eq("ORGA"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("alice")))
                .thenReturn(new ManualOverrideService.CreateResult(java.util.List.of(), 2, 1));
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();

        RedirectView view = controller.createManualOverride("ORGA", null, java.time.LocalDate.of(2026, 12, 1),
                java.time.LocalDate.of(2026, 12, 3), java.time.LocalTime.of(18, 45), "MINCHA", "bogus-mode",
                " n ", null, null, null, flash);

        assertEquals("/admin/ORGA/overrides", view.getUrl());
        assertEquals("Manual overrides saved (additive). Created: 2, Updated: 1.", flash.getFlashAttributes().get("successMessage"));
        org.mockito.ArgumentCaptor<ManualOverrideService.CreateOverrideCommand> command =
                org.mockito.ArgumentCaptor.forClass(ManualOverrideService.CreateOverrideCommand.class);
        verify(manualOverrideService).create(org.mockito.ArgumentMatchers.eq("ORGA"), command.capture(), org.mockito.ArgumentMatchers.eq("alice"));
        assertEquals("ADDITIVE", command.getValue().overrideMode());
        assertEquals(java.time.LocalDate.of(2026, 12, 3), command.getValue().endDate());
        assertEquals(Boolean.TRUE, command.getValue().enabled());
    }

    @Test
    void manualCreate_showsServiceValidationAndAuthorizationMessages() {
        when(manualOverrideService.create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalArgumentException("Invalid minyan type"))
                .thenThrow(new AccessDeniedException("no"));
        RedirectAttributesModelMap first = new RedirectAttributesModelMap();
        RedirectAttributesModelMap second = new RedirectAttributesModelMap();

        controller.createManualOverride("ORGA", null, java.time.LocalDate.now(), null, java.time.LocalTime.NOON,
                "X", "ADDITIVE", null, null, null, null, first);
        controller.createManualOverride("ORGA", null, java.time.LocalDate.now(), null, java.time.LocalTime.NOON,
                "MINCHA", "ADDITIVE", null, null, null, null, second);

        assertEquals("Invalid minyan type", first.getFlashAttributes().get("errorMessage"));
        assertEquals("Not authorized", second.getFlashAttributes().get("errorMessage"));
    }

    @Test
    void overrideToggleAndDelete_mapServiceExceptionsToTheOldMessages() {
        when(manualOverrideService.toggle("ORGA", "5", "system")).thenThrow(new NotManualOverrideException("x"));
        org.mockito.Mockito.doThrow(new OverrideNotFoundException("x")).when(manualOverrideService).delete("ORGA", "6");
        RedirectAttributesModelMap toggled = new RedirectAttributesModelMap();
        RedirectAttributesModelMap deleted = new RedirectAttributesModelMap();

        RedirectView toggleView = controller.toggleOverrideEvent("ORGA", 5L, toggled);
        controller.deleteOverrideEvent("ORGA", 6L, deleted);

        assertEquals("/admin/ORGA/overrides", toggleView.getUrl());
        assertEquals("Only manual overrides can be toggled here.", toggled.getFlashAttributes().get("errorMessage"));
        assertEquals("Event not found", deleted.getFlashAttributes().get("errorMessage"));
    }

    @Test
    void everyWriteHandlerCarriesAnAdminActionTag() {
        for (Class<?> type : java.util.List.of(CalendarEventsAdminController.class, SuperAdminOverridesController.class)) {
            for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
                if (method.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class)) {
                    org.junit.jupiter.api.Assertions.assertTrue(
                            method.isAnnotationPresent(com.tbdev.teaneckminyanim.security.AdminAction.class),
                            type.getSimpleName() + "#" + method.getName() + " needs @AdminAction");
                }
            }
        }
    }
}
