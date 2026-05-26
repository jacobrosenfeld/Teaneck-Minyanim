package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.repo.CalendarEventRepository;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.CalendarMaterializationScheduler;
import com.tbdev.teaneckminyanim.service.CalendarMaterializationService;
import com.tbdev.teaneckminyanim.service.EffectiveScheduleService;
import com.tbdev.teaneckminyanim.service.LocationService;
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

    private final CalendarEventsAdminController controller = new CalendarEventsAdminController(
            calendarEventRepository,
            organizationService,
            locationService,
            materializationService,
            materializationScheduler,
            userService,
            settingsService,
            effectiveScheduleService,
            overrideXlsxService);

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
}
