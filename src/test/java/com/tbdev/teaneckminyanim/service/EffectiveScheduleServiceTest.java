package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.enums.EventSource;
import com.tbdev.teaneckminyanim.minyan.MinyanType;
import com.tbdev.teaneckminyanim.model.CalendarEvent;
import com.tbdev.teaneckminyanim.repo.CalendarEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EffectiveScheduleServiceTest {

    @Mock
    private CalendarEventRepository calendarEventRepository;

    @Mock
    private CalendarMaterializationService materializationService;

    @InjectMocks
    private EffectiveScheduleService effectiveScheduleService;

    private static final String ORG_ID = "org-1";
    private static final LocalDate DATE = LocalDate.of(2026, 3, 22);

    @BeforeEach
    void setUp() {
        when(materializationService.isDateInWindow(any())).thenReturn(true);
    }

    @Test
    void getEffectiveEventsForDate_fullDayManualOverrideWins() {
        CalendarEvent fullDayManual = event(1L, ORG_ID, DATE, LocalTime.of(7, 0), EventSource.MANUAL,
                EffectiveScheduleService.MANUAL_FULL_DAY_SOURCE_REF_PREFIX + ":abc");
        CalendarEvent imported = event(2L, ORG_ID, DATE, LocalTime.of(7, 30), EventSource.IMPORTED, "imp-1");
        CalendarEvent rules = event(3L, ORG_ID, DATE, LocalTime.of(8, 0), EventSource.RULES, "rule-1");

        when(calendarEventRepository.findByOrganizationIdAndDateAndEnabledTrue(ORG_ID, DATE))
                .thenReturn(List.of(rules, imported, fullDayManual));

        List<CalendarEvent> effective = effectiveScheduleService.getEffectiveEventsForDate(ORG_ID, DATE);

        assertEquals(1, effective.size());
        assertEquals(fullDayManual.getId(), effective.get(0).getId());
    }

    @Test
    void getEffectiveEventsForDate_additiveManualAppendsToImportedDay() {
        CalendarEvent additiveManual = event(1L, ORG_ID, DATE, LocalTime.of(6, 45), EventSource.MANUAL,
                "manual:ADDITIVE:1");
        CalendarEvent imported = event(2L, ORG_ID, DATE, LocalTime.of(7, 0), EventSource.IMPORTED, "imp-1");
        CalendarEvent rules = event(3L, ORG_ID, DATE, LocalTime.of(8, 0), EventSource.RULES, "rule-1");

        when(calendarEventRepository.findByOrganizationIdAndDateAndEnabledTrue(ORG_ID, DATE))
                .thenReturn(List.of(rules, imported, additiveManual));

        List<CalendarEvent> effective = effectiveScheduleService.getEffectiveEventsForDate(ORG_ID, DATE);

        assertIterableEquals(List.of(additiveManual.getId(), imported.getId()),
                effective.stream().map(CalendarEvent::getId).toList());
    }

    @Test
    void getEffectiveEventsInRange_importedBeatsRulesWhenNoManual() {
        LocalDate date2 = DATE.plusDays(1);
        CalendarEvent d1Imported = event(1L, ORG_ID, DATE, LocalTime.of(7, 0), EventSource.IMPORTED, "imp-1");
        CalendarEvent d1Rules = event(2L, ORG_ID, DATE, LocalTime.of(7, 15), EventSource.RULES, "rule-1");
        CalendarEvent d2Rules = event(3L, ORG_ID, date2, LocalTime.of(8, 0), EventSource.RULES, "rule-2");

        when(calendarEventRepository.findEnabledEventsInRange(ORG_ID, DATE, date2))
                .thenReturn(List.of(d1Rules, d1Imported, d2Rules));

        List<CalendarEvent> effective = effectiveScheduleService.getEffectiveEventsInRange(ORG_ID, DATE, date2);

        assertIterableEquals(List.of(d1Imported.getId(), d2Rules.getId()),
                effective.stream().map(CalendarEvent::getId).toList());
    }

    @Test
    void getAllOrgsEffectiveEventsInRange_appliesManualModesPerOrgPerDay() {
        String org2 = "org-2";
        CalendarEvent org1FullDayManual = event(1L, ORG_ID, DATE, LocalTime.of(7, 0), EventSource.MANUAL,
                EffectiveScheduleService.MANUAL_FULL_DAY_SOURCE_REF_PREFIX + ":xyz");
        CalendarEvent org1Imported = event(2L, ORG_ID, DATE, LocalTime.of(7, 30), EventSource.IMPORTED, "imp-1");

        CalendarEvent org2Imported = event(3L, org2, DATE, LocalTime.of(6, 0), EventSource.IMPORTED, "imp-2");
        CalendarEvent org2AdditiveManual = event(4L, org2, DATE, LocalTime.of(6, 15), EventSource.MANUAL,
                "manual:ADDITIVE:2");

        when(calendarEventRepository.findAllEnabledEventsInRange(DATE, DATE))
                .thenReturn(List.of(org1Imported, org1FullDayManual, org2AdditiveManual, org2Imported));

        List<CalendarEvent> effective = effectiveScheduleService.getAllOrgsEffectiveEventsInRange(DATE, DATE);

        assertIterableEquals(List.of(org2Imported.getId(), org2AdditiveManual.getId(), org1FullDayManual.getId()),
                effective.stream().map(CalendarEvent::getId).toList());
    }

    private CalendarEvent event(Long id, String orgId, LocalDate date, LocalTime time, EventSource source, String sourceRef) {
        return CalendarEvent.builder()
                .id(id)
                .organizationId(orgId)
                .date(date)
                .minyanType(MinyanType.SHACHARIS)
                .startTime(time)
                .source(source)
                .sourceRef(sourceRef)
                .enabled(true)
                .build();
    }
}
