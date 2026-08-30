package com.tbdev.teaneckminyanim.service.provider;

import com.tbdev.teaneckminyanim.enums.Nusach;
import com.tbdev.teaneckminyanim.front.MinyanEvent;
import com.tbdev.teaneckminyanim.minyan.MinyanType;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.OrganizationCalendarEntry;
import com.tbdev.teaneckminyanim.repo.OrganizationCalendarEntryRepository;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarImportProviderTest {

    @Mock
    private OrganizationCalendarEntryRepository entryRepository;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private ApplicationSettingsService settingsService;

    @Test
    void getEventsForDate_infersSelichosShacharisFallbackVariants() {
        String organizationId = "org-selichos";
        LocalDate date = LocalDate.of(2026, 9, 5);
        CalendarImportProvider provider = new CalendarImportProvider(
                entryRepository,
                organizationService,
                settingsService);
        Organization org = Organization.builder()
                .id(organizationId)
                .name("Test Shul")
                .orgColor("#275ED8")
                .nusach(Nusach.ASHKENAZ)
                .build();
        OrganizationCalendarEntry entry = OrganizationCalendarEntry.builder()
                .id(1L)
                .organizationId(organizationId)
                .date(date)
                .startTime(LocalTime.of(6, 0))
                .title("Slichos & Shachris")
                .type("Event")
                .enabled(true)
                .build();

        when(organizationService.findById(organizationId)).thenReturn(Optional.of(org));
        when(entryRepository.findByOrganizationIdAndDateAndEnabledTrue(organizationId, date))
                .thenReturn(List.of(entry));
        when(settingsService.getZoneId()).thenReturn(ZoneId.of("America/New_York"));

        List<MinyanEvent> events = provider.getEventsForDate(organizationId, date);

        assertEquals(1, events.size());
        assertEquals(MinyanType.SELICHOS_SHACHARIS, events.getFirst().getType());
    }
}
