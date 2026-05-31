package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.enums.EventSource;
import com.tbdev.teaneckminyanim.enums.Nusach;
import com.tbdev.teaneckminyanim.minyan.MinyanType;
import com.tbdev.teaneckminyanim.model.CalendarEvent;
import com.tbdev.teaneckminyanim.model.Location;
import com.tbdev.teaneckminyanim.model.Minyan;
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
import java.time.ZoneId;
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
    void clearImportedEventsForOrganization_deletesImportedMaterializedRows() {
        String orgId = "org-import-cleanup";
        when(calendarEventRepository.deleteByOrganizationIdAndSource(orgId, EventSource.IMPORTED))
                .thenReturn(3L);

        long deleted = service.clearImportedEventsForOrganization(orgId);

        assertEquals(3L, deleted);
        verify(calendarEventRepository).deleteByOrganizationIdAndSource(orgId, EventSource.IMPORTED);
    }

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
    void syncImportedEntriesInRangeLive_deletesDuplicateMaterializedRowsForSameImport() {
        String orgId = "org-duplicates";
        LocalDate date = LocalDate.now();

        Organization org = Organization.builder()
                .id(orgId)
                .name("Org")
                .orgColor("#000000")
                .nusach(Nusach.ASHKENAZ)
                .build();

        OrganizationCalendarEntry entry = OrganizationCalendarEntry.builder()
                .id(101L)
                .organizationId(orgId)
                .date(date)
                .classification(MinyanType.SHACHARIS)
                .startTime(LocalTime.of(5, 15))
                .enabled(true)
                .notes("Vasikin Minyan")
                .build();

        CalendarEvent canonical = CalendarEvent.builder()
                .id(1L)
                .organizationId(orgId)
                .date(date)
                .minyanType(MinyanType.SHACHARIS)
                .startTime(LocalTime.of(5, 15))
                .enabled(true)
                .source(EventSource.IMPORTED)
                .sourceRef("import-101")
                .build();

        CalendarEvent duplicate = CalendarEvent.builder()
                .id(2L)
                .organizationId(orgId)
                .date(date)
                .minyanType(MinyanType.SHACHARIS)
                .startTime(LocalTime.of(5, 15))
                .enabled(true)
                .source(EventSource.IMPORTED)
                .sourceRef("import-101")
                .build();

        when(organizationService.findById(orgId)).thenReturn(Optional.of(org));
        when(importedEntryRepository.findEntriesInRange(orgId, date, date))
                .thenReturn(List.of(entry));
        when(calendarEventRepository.findByOrganizationIdAndSourceAndDateBetween(
                orgId, EventSource.IMPORTED, date, date))
                .thenReturn(List.of(duplicate, canonical));

        service.syncImportedEntriesInRangeLive(orgId, date, date);

        verify(calendarEventRepository).deleteAll(List.of(duplicate));

        ArgumentCaptor<List<CalendarEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(calendarEventRepository).saveAll(captor.capture());
        List<CalendarEvent> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertEquals(canonical.getId(), saved.get(0).getId());
        assertEquals("Vasikin Minyan", saved.get(0).getNotes());
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
    void syncImportedEntriesInRangeLive_disablesExistingWhenEntryWasRemovedFromSource() {
        String orgId = "org-source-deleted";
        LocalDate date = LocalDate.now();

        Organization org = Organization.builder()
                .id(orgId)
                .name("Org")
                .orgColor("#111111")
                .build();

        OrganizationCalendarEntry sourceDeletedEntry = OrganizationCalendarEntry.builder()
                .id(404L)
                .organizationId(orgId)
                .date(date)
                .classification(MinyanType.SHACHARIS)
                .startTime(LocalTime.of(7, 15))
                .enabled(true)
                .sourceDeleted(true)
                .build();

        CalendarEvent existingEvent = CalendarEvent.builder()
                .id(4L)
                .organizationId(orgId)
                .date(date)
                .minyanType(MinyanType.SHACHARIS)
                .startTime(LocalTime.of(7, 15))
                .enabled(true)
                .source(EventSource.IMPORTED)
                .sourceRef("import-404")
                .build();

        when(organizationService.findById(orgId)).thenReturn(Optional.of(org));
        when(importedEntryRepository.findEntriesInRange(orgId, date, date))
                .thenReturn(List.of(sourceDeletedEntry));
        when(calendarEventRepository.findByOrganizationIdAndSourceAndDateBetween(
                orgId, EventSource.IMPORTED, date, date))
                .thenReturn(List.of(existingEvent));

        service.syncImportedEntriesInRangeLive(orgId, date, date);

        ArgumentCaptor<List<CalendarEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(calendarEventRepository).saveAll(captor.capture());
        List<CalendarEvent> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertFalse(saved.get(0).isEnabled(), "Source-deleted imported entries must not stay live");
    }

    @Test
    void syncImportedEntriesInRangeLive_enablesSourceDeletedEntryWhenAdminRestoredIt() {
        String orgId = "org-source-restored";
        LocalDate date = LocalDate.now();

        Organization org = Organization.builder()
                .id(orgId)
                .name("Org")
                .orgColor("#111111")
                .nusach(Nusach.ASHKENAZ)
                .build();

        OrganizationCalendarEntry sourceDeletedEntry = OrganizationCalendarEntry.builder()
                .id(405L)
                .organizationId(orgId)
                .date(date)
                .classification(MinyanType.SHACHARIS)
                .startTime(LocalTime.of(7, 15))
                .enabled(true)
                .enabledManuallySet(true)
                .sourceDeleted(true)
                .build();

        CalendarEvent existingEvent = CalendarEvent.builder()
                .id(6L)
                .organizationId(orgId)
                .date(date)
                .minyanType(MinyanType.SHACHARIS)
                .startTime(LocalTime.of(7, 15))
                .enabled(false)
                .source(EventSource.IMPORTED)
                .sourceRef("import-405")
                .build();

        when(organizationService.findById(orgId)).thenReturn(Optional.of(org));
        when(importedEntryRepository.findEntriesInRange(orgId, date, date))
                .thenReturn(List.of(sourceDeletedEntry));
        when(calendarEventRepository.findByOrganizationIdAndSourceAndDateBetween(
                orgId, EventSource.IMPORTED, date, date))
                .thenReturn(List.of(existingEvent));

        service.syncImportedEntriesInRangeLive(orgId, date, date);

        ArgumentCaptor<List<CalendarEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(calendarEventRepository).saveAll(captor.capture());
        List<CalendarEvent> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertEquals(existingEvent.getId(), saved.get(0).getId());
        assertTrue(saved.get(0).isEnabled(), "Admin-restored source-deleted imported entries must stay live");
    }

    @Test
    void syncImportedEntriesInRangeLive_disablesMaterializedRowsWithoutSourceEntry() {
        String orgId = "org-orphan";
        LocalDate date = LocalDate.now();

        Organization org = Organization.builder()
                .id(orgId)
                .name("Org")
                .orgColor("#111111")
                .build();

        CalendarEvent orphanedEvent = CalendarEvent.builder()
                .id(5L)
                .organizationId(orgId)
                .date(date)
                .minyanType(MinyanType.MINCHA)
                .startTime(LocalTime.of(13, 30))
                .enabled(true)
                .source(EventSource.IMPORTED)
                .sourceRef("import-505")
                .build();

        when(organizationService.findById(orgId)).thenReturn(Optional.of(org));
        when(importedEntryRepository.findEntriesInRange(orgId, date, date))
                .thenReturn(List.of());
        when(calendarEventRepository.findByOrganizationIdAndSourceAndDateBetween(
                orgId, EventSource.IMPORTED, date, date))
                .thenReturn(List.of(orphanedEvent));

        service.syncImportedEntriesInRangeLive(orgId, date, date);

        ArgumentCaptor<List<CalendarEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(calendarEventRepository).saveAll(captor.capture());
        List<CalendarEvent> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertEquals(orphanedEvent.getId(), saved.get(0).getId());
        assertFalse(saved.get(0).isEnabled(), "Orphaned imported materialized rows must be disabled");
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

    @Test
    void syncRulesForOrganizationLive_rebuildsRuleMaterializedEventsInWindow() {
        String orgId = "org-rules";
        Organization org = Organization.builder()
                .id(orgId)
                .name("Org")
                .orgColor("#000000")
                .nusach(Nusach.ASHKENAZ)
                .build();
        Location location = new Location("Main", orgId);

        Minyan minyan = new Minyan();
        minyan.setId("rule-1");
        minyan.setOrganizationId(orgId);
        minyan.setLocationId(location.getId());
        minyan.setType(MinyanType.MINCHA_MAARIV);
        minyan.setNusach(Nusach.ASHKENAZ);
        minyan.setNotes("Rule note");
        minyan.setWhatsapp("https://chat.example");
        minyan.setSchedule(fixedSchedule("T18:0:0:0"));

        when(minyanService.findEnabledMatching(orgId)).thenReturn(List.of(minyan));
        when(organizationService.findById(orgId)).thenReturn(Optional.of(org));
        when(settingsService.getZoneId()).thenReturn(ZoneId.of("America/New_York"));
        when(locationService.findById(location.getId())).thenReturn(location);

        service.syncRulesForOrganizationLive(orgId);

        verify(calendarEventRepository).deleteRulesEventsInRange(eq(orgId), any(LocalDate.class), any(LocalDate.class));

        ArgumentCaptor<List<CalendarEvent>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(calendarEventRepository).saveAll(savedCaptor.capture());
        List<CalendarEvent> saved = savedCaptor.getValue();
        assertFalse(saved.isEmpty());
        assertTrue(saved.stream().allMatch(e -> e.getSource() == EventSource.RULES));
        assertTrue(saved.stream().allMatch(e -> "rule-1".equals(e.getSourceRef())));
        assertTrue(saved.stream().allMatch(e -> e.getMinyanType() == MinyanType.MINCHA_MAARIV));
        assertTrue(saved.stream().allMatch(e -> "Main".equals(e.getLocationName())));
    }

    private com.tbdev.teaneckminyanim.minyan.Schedule fixedSchedule(String time) {
        return new com.tbdev.teaneckminyanim.minyan.Schedule(
                time, time, time, time, time, time, time, time, time, time, time);
    }
}
