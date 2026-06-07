package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.enums.EventSource;
import com.tbdev.teaneckminyanim.enums.Nusach;
import com.tbdev.teaneckminyanim.minyan.MinyanType;
import com.tbdev.teaneckminyanim.model.CalendarEvent;
import com.tbdev.teaneckminyanim.repo.CalendarEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminOverrideXlsxServiceTest {

    private static final String ORG_ID = "org-1";
    private static final LocalDate DATE = LocalDate.of(2026, 6, 7);
    private static final LocalTime TIME = LocalTime.of(8, 0);

    @Mock
    private CalendarEventRepository calendarEventRepository;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private LocationService locationService;

    @InjectMocks
    private SuperAdminOverrideXlsxService service;

    @Test
    void upsertManualEvent_createsSecondRowWhenSameTimeExistingRowHasDifferentLocation() {
        CalendarEvent existingOldMain = manualEvent(41L, "Old Main", null, Nusach.ASHKENAZ);

        when(calendarEventRepository.findByOrganizationIdAndDateAndMinyanTypeAndStartTimeAndSourceOrderByIdAsc(
                ORG_ID, DATE, MinyanType.SHACHARIS, TIME, EventSource.MANUAL))
                .thenReturn(List.of(existingOldMain));

        boolean updated = service.upsertManualEvent(
                ORG_ID,
                DATE,
                TIME,
                MinyanType.SHACHARIS,
                "FULL_DAY_REPLACE",
                null,
                "Ogden Lower Level",
                "Deliberately Paced Minyan",
                Nusach.ASHKENAZ,
                true,
                "tester");

        ArgumentCaptor<CalendarEvent> captor = ArgumentCaptor.forClass(CalendarEvent.class);
        verify(calendarEventRepository).save(captor.capture());

        CalendarEvent saved = captor.getValue();
        assertFalse(updated);
        assertNull(saved.getId());
        assertEquals("Ogden Lower Level", saved.getLocationName());
        assertEquals("Deliberately Paced Minyan", saved.getNotes());
        assertEquals("Old Main", existingOldMain.getLocationName());
        assertNull(existingOldMain.getNotes());
    }

    @Test
    void upsertManualEvent_updatesMatchingLocationAmongSameTimeDuplicates() {
        CalendarEvent oldMain = manualEvent(41L, "Old Main", null, Nusach.ASHKENAZ);
        CalendarEvent ogden = manualEvent(42L, "Ogden Lower Level", "Old Label", Nusach.ASHKENAZ);

        when(calendarEventRepository.findByOrganizationIdAndDateAndMinyanTypeAndStartTimeAndSourceOrderByIdAsc(
                ORG_ID, DATE, MinyanType.SHACHARIS, TIME, EventSource.MANUAL))
                .thenReturn(List.of(oldMain, ogden));

        boolean updated = service.upsertManualEvent(
                ORG_ID,
                DATE,
                TIME,
                MinyanType.SHACHARIS,
                "FULL_DAY_REPLACE",
                null,
                "Ogden Lower Level",
                "Deliberately Paced Minyan",
                Nusach.ASHKENAZ,
                true,
                "tester");

        ArgumentCaptor<CalendarEvent> captor = ArgumentCaptor.forClass(CalendarEvent.class);
        verify(calendarEventRepository).save(captor.capture());

        CalendarEvent saved = captor.getValue();
        assertTrue(updated);
        assertSame(ogden, saved);
        assertEquals("Deliberately Paced Minyan", ogden.getNotes());
        assertEquals("Old Main", oldMain.getLocationName());
    }

    private CalendarEvent manualEvent(Long id, String locationName, String notes, Nusach nusach) {
        return CalendarEvent.builder()
                .id(id)
                .organizationId(ORG_ID)
                .date(DATE)
                .minyanType(MinyanType.SHACHARIS)
                .startTime(TIME)
                .locationName(locationName)
                .notes(notes)
                .nusach(nusach)
                .source(EventSource.MANUAL)
                .enabled(true)
                .build();
    }
}
