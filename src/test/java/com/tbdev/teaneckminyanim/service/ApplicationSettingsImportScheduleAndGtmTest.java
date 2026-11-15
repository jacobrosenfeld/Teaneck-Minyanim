package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.enums.SettingKey;
import com.tbdev.teaneckminyanim.model.ApplicationSettings;
import com.tbdev.teaneckminyanim.repo.ApplicationSettingsRepository;
import com.tbdev.teaneckminyanim.service.calendar.CalendarImportSchedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApplicationSettingsImportScheduleAndGtmTest {

    private final Map<SettingKey, ApplicationSettings> rows = new EnumMap<>(SettingKey.class);
    private ApplicationSettingsService service;

    @BeforeEach
    void setUp() {
        ApplicationSettingsRepository repository = mock(ApplicationSettingsRepository.class);
        for (SettingKey key : SettingKey.values()) {
            rows.put(key, new ApplicationSettings(key.getKey(), key.getDefaultValue(), key.getType().name()));
        }
        when(repository.findBySettingKey(anyString()))
                .thenAnswer(invocation -> Optional.of(rows.get(SettingKey.fromKey(invocation.getArgument(0)))));
        when(repository.save(any(ApplicationSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service = new ApplicationSettingsService(repository);
    }

    @Test
    void scheduleModeAndGtmDefaults() {
        assertEquals("SIMPLE", SettingKey.CALENDAR_IMPORT_SCHEDULE_MODE.getDefaultValue());
        assertEquals(CalendarImportSchedule.Mode.SIMPLE, service.getCalendarImportScheduleMode());
        assertEquals("GTM-T2P4J8V", SettingKey.GTM_CONTAINER_ID.getDefaultValue());
        assertEquals("GTM-T2P4J8V", service.getGtmContainerId());
    }

    @Test
    void simpleScheduleSavesGeneratedCronAndMode() throws Exception {
        String cron = service.saveCalendarImportSchedule("SIMPLE", "WEEKLY", List.of("WED", "SUN"), "02:00", "ignored");

        assertEquals("0 0 2 * * SUN,WED", cron);
        assertEquals("0 0 2 * * SUN,WED", rows.get(SettingKey.CALENDAR_IMPORT_CRON).getSettingValue());
        assertEquals("SIMPLE", rows.get(SettingKey.CALENDAR_IMPORT_SCHEDULE_MODE).getSettingValue());
        assertEquals("0 0 2 * * SUN,WED", service.getCalendarImportCron());
    }

    @Test
    void advancedScheduleSavesCronAsEntered() throws Exception {
        String cron = service.saveCalendarImportSchedule("ADVANCED", null, null, null, "  0 0 */6 * * *  ");

        assertEquals("0 0 */6 * * *", cron);
        assertEquals("0 0 */6 * * *", rows.get(SettingKey.CALENDAR_IMPORT_CRON).getSettingValue());
        assertEquals("ADVANCED", rows.get(SettingKey.CALENDAR_IMPORT_SCHEDULE_MODE).getSettingValue());
        assertEquals(CalendarImportSchedule.Mode.ADVANCED, service.getCalendarImportScheduleMode());
    }

    @Test
    void invalidScheduleInputIsRejectedAndNothingIsSaved() {
        ApplicationSettingsService.ValidationException noDays = assertThrows(
                ApplicationSettingsService.ValidationException.class,
                () -> service.saveCalendarImportSchedule("SIMPLE", "WEEKLY", List.of(), "02:00", null));
        assertTrue(noDays.getMessage().contains("at least one day"));

        assertThrows(ApplicationSettingsService.ValidationException.class,
                () -> service.saveCalendarImportSchedule("SIMPLE", "DAILY", null, "24:30", null));
        assertThrows(ApplicationSettingsService.ValidationException.class,
                () -> service.saveCalendarImportSchedule("ADVANCED", null, null, null, "every sunday"));
        assertThrows(ApplicationSettingsService.ValidationException.class,
                () -> service.saveCalendarImportSchedule("ADVANCED", null, null, null, " "));
        assertThrows(ApplicationSettingsService.ValidationException.class,
                () -> service.saveCalendarImportSchedule("EXPERT", "DAILY", null, "02:00", null));

        assertEquals("0 0 2 * * SUN", rows.get(SettingKey.CALENDAR_IMPORT_CRON).getSettingValue());
        assertEquals("SIMPLE", rows.get(SettingKey.CALENDAR_IMPORT_SCHEDULE_MODE).getSettingValue());
    }

    @Test
    void scheduleModeSettingIsValidated() {
        assertThrows(ApplicationSettingsService.ValidationException.class,
                () -> service.updateSetting(SettingKey.CALENDAR_IMPORT_SCHEDULE_MODE, "SOMETIMES"));
        assertDoesNotThrow(() -> service.updateSetting(SettingKey.CALENDAR_IMPORT_SCHEDULE_MODE, "ADVANCED"));
    }

    @Test
    void gtmContainerIdAcceptsValidIdOrBlank() throws Exception {
        service.updateSetting(SettingKey.GTM_CONTAINER_ID, " GTM-ABC123 ");
        assertEquals("GTM-ABC123", service.getGtmContainerId());

        service.updateSetting(SettingKey.GTM_CONTAINER_ID, "");
        assertEquals("", service.getGtmContainerId());
    }

    @Test
    void gtmContainerIdRejectsInvalidValues() {
        for (String bad : List.of("GTM-", "gtm-abc123", "UA-12345", "GTM-ABC 123", "GTM-ABC');alert(1)//")) {
            assertThrows(ApplicationSettingsService.ValidationException.class,
                    () -> service.updateSetting(SettingKey.GTM_CONTAINER_ID, bad), bad);
        }
    }

    @Test
    void gtmGetterIgnoresInvalidStoredValue() {
        rows.get(SettingKey.GTM_CONTAINER_ID).setSettingValue("<script>alert(1)</script>");
        service.refreshCache();

        assertEquals("", service.getGtmContainerId());
    }
}
