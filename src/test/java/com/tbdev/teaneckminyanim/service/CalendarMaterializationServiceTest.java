package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.enums.EventSource;
import com.tbdev.teaneckminyanim.enums.Nusach;
import com.tbdev.teaneckminyanim.minyan.MinyanType;
import com.tbdev.teaneckminyanim.model.CalendarEvent;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.OrganizationCalendarEntry;
import com.tbdev.teaneckminyanim.repo.CalendarEventRepository;
import com.tbdev.teaneckminyanim.repo.OrganizationCalendarEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarMaterializationServiceTest {

    @Mock
    private CalendarEventRepository calendarEventRepository;

    @Mock
    private OrganizationCalendarEntryRepository importedEntryRepository;

    @Mock
    private MinyanService minyanService;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private LocationService locationService;

    @Mock
    private ApplicationSettingsService settingsService;

    @Mock
    private ZmanimHandler zmanimHandler;

    @InjectMocks
    private CalendarMaterializationService service;

    @Test
    void syncImportedEntriesInRangeLive_updatesExistingAndCreatesMissing() {
        String orgId = "org-1";
        LocalDate date = LocalDate.now();

        Organization org = Organization.builder()
                .id(orgId)
                .name("Org")
                .orgColor("#000000")
                .nusach(Nusach.ASHKENAZ)
                .build();

        OrganizationCalendarEntry existingEntry = OrganizationCalendarEntry.builder()
                .id(101L)
                .organizationId(orgId)
                .date(date)
                .classification(MinyanType.SHACHARIS)
                .startTime(LocalTime.of(7, 0))
                .enabled(false)
                .build();

        OrganizationCalendarEntry newEntry = OrganizationCalendarEntry.builder()
                .id(202L)
                .organizationId(orgId)
                .date(date)
                .classification(MinyanType.MINCHA)
                .startTime(LocalTime.of(13, 30))
                .enabled(true)
                .location("Main")
                .notes("Daily")
                .build();

        CalendarEvent existingEvent = CalendarEvent.builder()
                .id(1L)
                .organizationId(orgId)
                .date(date)
                .minyanType(MinyanType.SHACHARIS)
                .startTime(LocalTime.of(7, 0))
                .enabled(true)
                .source(EventSource.IMPORTED)
                .sourceRef("import-101")
                .build();

        when(organizationService.findById(orgId)).thenReturn(Optional.of(org));
        when(importedEntryRepository.findEntriesInRange(orgId, date, date))
                .thenReturn(List.of(existingEntry, newEntry));
        when(calendarEventRepository.findByOrganizationIdAndSourceAndDateBetween(
                orgId, EventSource.IMPORTED, date, date))
                .thenReturn(List.of(existingEvent));

        service.syncImportedEntriesInRangeLive(orgId, date, date);

        ArgumentCaptor<List<CalendarEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(calendarEventRepository).saveAll(captor.capture());
        List<CalendarEvent> saved = captor.getValue();
        assertEquals(2, saved.size());

        CalendarEvent updated = saved.stream()
                .filter(e -> "import-101".equals(e.getSourceRef()))
                .findFirst()
                .orElseThrow();
        assertFalse(updated.isEnabled(), "Existing imported event must sync enabled=false immediately");

        CalendarEvent created = saved.stream()
                .filter(e -> "import-202".equals(e.getSourceRef()))
                .findFirst()
                .orElseThrow();
        assertEquals(MinyanType.MINCHA, created.getMinyanType());
        assertEquals(LocalTime.of(13, 30), created.getStartTime());
        assertTrue(created.isEnabled());
        assertEquals("Main", created.getLocationName());
        assertEquals("Daily", created.getNotes());
        assertNotNull(created.getNusach());
    }

    @Test
    void syncImportedEntriesInRangeLive_disablesExistingWhenEntryIsNonMinyan() {
        String orgId = "org-2";
        LocalDate date = LocalDate.now();

        Organization org = Organization.builder()
                .id(orgId)
                .name("Org")
                .orgColor("#111111")
                .build();

        OrganizationCalendarEntry nonMinyanEntry = OrganizationCalendarEntry.builder()
                .id(303L)
                .organizationId(orgId)
                .date(date)
                .classification(MinyanType.NON_MINYAN)
                .enabled(true)
                .build();

        CalendarEvent existingEvent = CalendarEvent.builder()
                .id(2L)
                .organizationId(orgId)
                .date(date)
                .minyanType(MinyanType.MAARIV)
                .startTime(LocalTime.of(20, 0))
                .enabled(true)
                .source(EventSource.IMPORTED)
                .sourceRef("import-303")
                .build();

        when(organizationService.findById(orgId)).thenReturn(Optional.of(org));
        when(importedEntryRepository.findEntriesInRange(orgId, date, date))
                .thenReturn(List.of(nonMinyanEntry));
        when(calendarEventRepository.findByOrganizationIdAndSourceAndDateBetween(
                orgId, EventSource.IMPORTED, date, date))
                .thenReturn(List.of(existingEvent));

        service.syncImportedEntriesInRangeLive(orgId, date, date);

        ArgumentCaptor<List<CalendarEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(calendarEventRepository).saveAll(captor.capture());
        List<CalendarEvent> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertFalse(saved.get(0).isEnabled(), "Existing imported event must be disabled for NON_MINYAN entries");
    }

    @Test
    void syncImportedEntriesInRangeLive_outsideWindowDoesNothing() {
        String orgId = "org-3";
        LocalDate farFuture = LocalDate.now().plusYears(2);

        service.syncImportedEntriesInRangeLive(orgId, farFuture, farFuture);

        verify(importedEntryRepository, never()).findEntriesInRange(any(), any(), any());
        verify(calendarEventRepository, never()).saveAll(any());
        verify(organizationService, never()).findById(eq(orgId));
    }
}
