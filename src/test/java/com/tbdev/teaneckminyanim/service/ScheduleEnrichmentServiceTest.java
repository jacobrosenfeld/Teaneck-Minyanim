package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.api.dto.ScheduleEventDto;
import com.tbdev.teaneckminyanim.enums.Nusach;
import com.tbdev.teaneckminyanim.enums.Zman;
import com.tbdev.teaneckminyanim.front.MinyanEvent;
import com.tbdev.teaneckminyanim.minyan.MinyanType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleEnrichmentServiceTest {

    private static final ZoneId ZONE_ID = ZoneId.of("America/New_York");
    private static final LocalDate DATE = LocalDate.of(2026, 3, 30);

    @Mock
    private ZmanimHandler zmanimHandler;

    @Mock
    private ApplicationSettingsService settingsService;

    @InjectMocks
    private ScheduleEnrichmentService service;

    @BeforeEach
    void setUp() {
        lenient().when(settingsService.getZoneId()).thenReturn(ZONE_ID);
        when(settingsService.getTimeZone()).thenReturn(TimeZone.getTimeZone(ZONE_ID));
    }

    @Test
    void annotateZmanim_addsShkiyaToMinchaMaarivDtoAtDisplayTime() {
        when(zmanimHandler.getZmanim(DATE))
                .thenReturn(zmanim(DATE, LocalTime.of(19, 10), null));

        ScheduleEventDto dto = dto("manual-1", DATE, "18:30", "MINCHA_MAARIV", "Teen Minyan", null);

        List<ScheduleEventDto> enriched = service.annotateZmanim(List.of(dto));

        assertEquals("Teen Minyan | Shkiya: 7:10 PM", enriched.get(0).notes());
    }

    @Test
    void annotateZmanim_replacesStaleShkiyaWithPlagWhenEventIsNearPlag() {
        when(zmanimHandler.getZmanim(DATE))
                .thenReturn(zmanim(DATE, LocalTime.of(19, 10), LocalTime.of(17, 45)));

        ScheduleEventDto dto = dto(
                "import-1",
                DATE,
                "17:43",
                "MINCHA_MAARIV",
                "Shkiya: 6:55 PM. Teen Minyan",
                null);

        List<ScheduleEventDto> enriched = service.annotateZmanim(List.of(dto));

        assertEquals("Teen Minyan | Plag: 5:45 PM", enriched.get(0).notes());
    }

    @Test
    void annotateZmanim_addsShkiyaToWebMinchaMaarivEvents() {
        Dictionary<Zman, Date> zmanim = zmanim(DATE, LocalTime.of(19, 10), null);
        MinyanEvent event = new MinyanEvent(
                "manual-1",
                MinyanType.MINCHA_MAARIV,
                "Org",
                Nusach.ASHKENAZ,
                "org-1",
                "Main",
                dateAt(DATE, LocalTime.of(18, 30)),
                Nusach.ASHKENAZ,
                null,
                "#000000",
                "");

        service.annotateZmanim(List.of(event), zmanim);

        assertEquals("Shkiya: 7:10 PM", event.getNotes());
    }

    private ScheduleEventDto dto(
            String id,
            LocalDate date,
            String startTime,
            String minyanType,
            String notes,
            String dynamicTimeString) {
        ScheduleEventDto.OrgSummary org = new ScheduleEventDto.OrgSummary(
                "org-1", "Org", "org", "#000000", null);
        return new ScheduleEventDto(
                id,
                date.toString(),
                startTime,
                minyanType,
                "Mincha/Maariv",
                org,
                "Main",
                notes,
                "ASHKENAZ",
                "Ashkenaz",
                dynamicTimeString,
                "MANUAL",
                null);
    }

    private Dictionary<Zman, Date> zmanim(LocalDate date, LocalTime shkiya, LocalTime plag) {
        Hashtable<Zman, Date> zmanim = new Hashtable<>();
        if (shkiya != null) {
            zmanim.put(Zman.SHEKIYA, dateAt(date, shkiya));
        }
        if (plag != null) {
            zmanim.put(Zman.PLAG_HAMINCHA, dateAt(date, plag));
        }
        return zmanim;
    }

    private Date dateAt(LocalDate date, LocalTime time) {
        return Date.from(date.atTime(time).atZone(ZONE_ID).toInstant());
    }
}
