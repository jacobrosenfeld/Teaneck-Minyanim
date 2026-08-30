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
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void annotateZmanim_keepsShkiyaWhenMinchaMaarivEventIsNotNearPlag() {
        when(zmanimHandler.getZmanim(DATE))
                .thenReturn(zmanim(DATE, LocalTime.of(19, 10), LocalTime.of(17, 45)));

        ScheduleEventDto dto = dto("manual-1", DATE, "18:30", "MINCHA_MAARIV", "Teen Minyan", null);

        List<ScheduleEventDto> enriched = service.annotateZmanim(List.of(dto));

        assertEquals("Teen Minyan | Shkiya: 7:10 PM", enriched.get(0).notes());
    }

    @Test
    void annotateZmanim_doesNotAddPlagToMinchaEvents() {
        when(zmanimHandler.getZmanim(DATE))
                .thenReturn(zmanim(DATE, LocalTime.of(19, 10), LocalTime.of(17, 45)));

        ScheduleEventDto dto = dto("manual-1", DATE, "17:43", "MINCHA", "Teen Minyan", null);

        List<ScheduleEventDto> enriched = service.annotateZmanim(List.of(dto));

        assertEquals("Teen Minyan", enriched.get(0).notes());
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

    @Test
    void annotateZmanim_marksLateStandaloneSelichosAsNightSelichos() {
        when(zmanimHandler.getZmanim(DATE))
                .thenReturn(zmanim(DATE, null, null, LocalTime.of(5, 20), LocalTime.of(20, 10)));

        ScheduleEventDto dto = dto("sel-1", DATE, "00:45", "SELICHOS", null, null);

        List<ScheduleEventDto> enriched = service.annotateZmanim(List.of(dto));

        assertEquals("NIGHT_SELICHOS", enriched.get(0).displayMinyanType());
        assertEquals("Night Selichos", enriched.get(0).displayMinyanTypeDisplay());
        assertEquals("NIGHT_SELICHOS", enriched.get(0).groupMinyanType());
    }

    @Test
    void annotateZmanim_attachesFollowingShacharisTimeToLinkedSelichos() {
        when(zmanimHandler.getZmanim(DATE))
                .thenReturn(zmanim(DATE, null, null));

        ScheduleEventDto linked = dto("sel-sh-1", DATE, "06:00", "SELICHOS_SHACHARIS", null, null);
        ScheduleEventDto shacharis = dto("sh-1", DATE, "06:45", "SHACHARIS", null, null);

        List<ScheduleEventDto> enriched = service.annotateZmanim(List.of(linked, shacharis));

        assertEquals("06:45", enriched.get(0).linkedStartTime());
        assertEquals("SHACHARIS", enriched.get(0).linkedMinyanType());
        assertEquals("SELICHOS_SHACHARIS", enriched.get(0).groupMinyanType());
        assertEquals("Selichos & Shacharis", enriched.get(0).groupMinyanTypeDisplay());
        assertEquals(true, enriched.get(1).linkedTarget());
    }

    @Test
    void annotateZmanim_marksSameTimeShacharisAsLinkedTargetForMorningSelichos() {
        when(zmanimHandler.getZmanim(DATE))
                .thenReturn(zmanim(DATE, null, null, LocalTime.of(5, 20), LocalTime.of(20, 10)));

        ScheduleEventDto selichos = dto("sel-1", DATE, "07:20", "SELICHOS", null, null);
        ScheduleEventDto shacharis = dto("sh-1", DATE, "07:20", "SHACHARIS", null, null);

        List<ScheduleEventDto> enriched = service.annotateZmanim(List.of(selichos, shacharis));

        assertEquals("SELICHOS_SHACHARIS", enriched.get(0).groupMinyanType());
        assertEquals("Selichos & Shacharis", enriched.get(0).groupMinyanTypeDisplay());
        assertNull(enriched.get(0).linkedStartTime());
        assertEquals(true, enriched.get(1).linkedTarget());
    }

    @Test
    void annotateZmanim_attachesFollowingShacharisTimeToMorningSelichos() {
        when(zmanimHandler.getZmanim(DATE))
                .thenReturn(zmanim(DATE, null, null, LocalTime.of(5, 20), LocalTime.of(20, 10)));

        ScheduleEventDto selichos = dto("sel-1", DATE, "06:00", "SELICHOS", null, null);
        ScheduleEventDto shacharis = dto("sh-1", DATE, "06:45", "SHACHARIS", null, null);

        List<ScheduleEventDto> enriched = service.annotateZmanim(List.of(selichos, shacharis));

        assertEquals("SELICHOS_SHACHARIS", enriched.get(0).groupMinyanType());
        assertEquals("06:45", enriched.get(0).linkedStartTime());
        assertEquals("SHACHARIS", enriched.get(0).linkedMinyanType());
        assertEquals(true, enriched.get(1).linkedTarget());
    }

    @Test
    void annotateZmanim_marksSameTimeWebShacharisAsLinkedTargetForMorningSelichos() {
        Dictionary<Zman, Date> zmanim = zmanim(
                DATE,
                null,
                null,
                LocalTime.of(5, 20),
                LocalTime.of(20, 10));
        MinyanEvent selichos = minyanEvent("sel-1", MinyanType.SELICHOS, LocalTime.of(7, 20));
        MinyanEvent shacharis = minyanEvent("sh-1", MinyanType.SHACHARIS, LocalTime.of(7, 20));

        service.annotateZmanim(List.of(selichos, shacharis), zmanim);

        assertEquals("SELICHOS_SHACHARIS", selichos.getPublicGroup());
        assertNull(selichos.getLinkedStartTime());
        assertEquals(true, shacharis.isLinkedTarget());
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
        String display = switch (minyanType) {
            case "SELICHOS" -> "Selichos";
            case "SELICHOS_SHACHARIS" -> "Selichos/Shacharis";
            case "SHACHARIS" -> "Shacharis";
            case "MINCHA" -> "Mincha";
            case "MAARIV" -> "Maariv";
            default -> "Mincha/Maariv";
        };
        String group = "SELICHOS_SHACHARIS".equals(minyanType) ? "SELICHOS_SHACHARIS" : minyanType;
        String groupDisplay = "SELICHOS_SHACHARIS".equals(minyanType) ? "Selichos & Shacharis" : display;
        return new ScheduleEventDto(
                id,
                date.toString(),
                startTime,
                minyanType,
                display,
                minyanType,
                display,
                group,
                groupDisplay,
                "SELICHOS_SHACHARIS".equals(minyanType) ? "SHACHARIS" : null,
                "SELICHOS_SHACHARIS".equals(minyanType) ? "Shacharis" : null,
                null,
                false,
                org,
                "Main",
                notes,
                "ASHKENAZ",
                "Ashkenaz",
                dynamicTimeString,
                "MANUAL",
                null);
    }

    private MinyanEvent minyanEvent(String id, MinyanType type, LocalTime time) {
        return new MinyanEvent(
                id,
                type,
                "Org",
                Nusach.ASHKENAZ,
                "org-1",
                "Main",
                dateAt(DATE, time),
                Nusach.ASHKENAZ,
                null,
                "#000000",
                "");
    }

    private Dictionary<Zman, Date> zmanim(LocalDate date, LocalTime shkiya, LocalTime plag) {
        return zmanim(date, shkiya, plag, null, null);
    }

    private Dictionary<Zman, Date> zmanim(
            LocalDate date,
            LocalTime shkiya,
            LocalTime plag,
            LocalTime alos,
            LocalTime tzes) {
        Hashtable<Zman, Date> zmanim = new Hashtable<>();
        if (shkiya != null) {
            zmanim.put(Zman.SHEKIYA, dateAt(date, shkiya));
        }
        if (plag != null) {
            zmanim.put(Zman.PLAG_HAMINCHA, dateAt(date, plag));
        }
        if (alos != null) {
            zmanim.put(Zman.ALOS_HASHACHAR, dateAt(date, alos));
        }
        if (tzes != null) {
            zmanim.put(Zman.TZES, dateAt(date, tzes));
        }
        return zmanim;
    }

    private Date dateAt(LocalDate date, LocalTime time) {
        return Date.from(date.atTime(time).atZone(ZONE_ID).toInstant());
    }
}
