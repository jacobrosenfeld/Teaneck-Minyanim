package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.api.dto.ScheduleEventDto;
import com.tbdev.teaneckminyanim.enums.Zman;
import com.tbdev.teaneckminyanim.front.MinyanEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Shared enrichment logic applied to schedule events before they are returned
 * to either the web frontend or the public REST API.
 *
 * <p>Both {@link ZmanimService} and the API's {@code ScheduleApiController} must call
 * these methods so the app and website always display identical information.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleEnrichmentService {

    private final ZmanimHandler zmanimHandler;
    private final ApplicationSettingsService settingsService;

    // -----------------------------------------------------------------------
    // API path — operates on ScheduleEventDto (immutable record)
    // -----------------------------------------------------------------------

    /**
     * Annotate MAARIV / MINCHA_MAARIV {@link ScheduleEventDto} objects that have a
     * fixed start time (no {@code dynamicTimeString}) and fall within 30 minutes of
     * Plag HaMincha.  Strips any existing "Shkiya:" note and replaces it with
     * "Plag: HH:MM AM/PM" so the app matches what the website displays.
     *
     * @param dtos sorted list of events for one or more dates
     * @return new list with notes patched where appropriate
     */
    public List<ScheduleEventDto> annotatePlag(List<ScheduleEventDto> dtos) {
        if (dtos.isEmpty()) return dtos;

        // Compute plag once per distinct date
        Map<String, Date> plagByDate = new HashMap<>();
        for (ScheduleEventDto dto : dtos) {
            plagByDate.computeIfAbsent(dto.date(), d -> getPlag(LocalDate.parse(d)));
        }

        SimpleDateFormat fmt = buildFmt();
        ZoneId zoneId = settingsService.getZoneId();
        long thirtyMinutes = 30L * 60 * 1000;

        return dtos.stream().map(dto -> {
            if (dto.dynamicTimeString() != null) return dto;
            String type = dto.minyanType();
            if (!"MAARIV".equals(type) && !"MINCHA_MAARIV".equals(type)) return dto;

            Date plagTime = plagByDate.get(dto.date());
            if (plagTime == null) return dto;

            try {
                Date eventTime = Date.from(
                        LocalDate.parse(dto.date()).atTime(LocalTime.parse(dto.startTime()))
                                .atZone(zoneId).toInstant());
                if (Math.abs(eventTime.getTime() - plagTime.getTime()) > thirtyMinutes) return dto;
                return dto.withNotes(buildPlagNotes(dto.notes(), fmt.format(plagTime)));
            } catch (Exception e) {
                log.warn("Plag annotation failed for event {}: {}", dto.id(), e.getMessage());
                return dto;
            }
        }).collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Web path — operates on MinyanEvent (mutable, already has zmanim in scope)
    // -----------------------------------------------------------------------

    /**
     * Same annotation logic as {@link #annotatePlag(List)}, operating on the
     * mutable {@link MinyanEvent} objects used by the web frontend.
     *
     * @param events list to annotate in-place
     * @param zmanim pre-computed zmanim for the rendered date (saves a lookup)
     */
    public void annotatePlag(List<MinyanEvent> events, Dictionary<Zman, Date> zmanim) {
        Date plagTime = zmanim != null ? zmanim.get(Zman.PLAG_HAMINCHA) : null;
        if (plagTime == null) return;

        SimpleDateFormat fmt = buildFmt();
        long thirtyMinutes = 30L * 60 * 1000;

        for (MinyanEvent event : events) {
            if (event.getStartTime() == null) continue;
            if (!event.getType().isMaariv() && !event.getType().isMinchaMariv()) continue;
            if (event.dynamicTimeString() != null) continue;

            long diff = Math.abs(event.getStartTime().getTime() - plagTime.getTime());
            if (diff > thirtyMinutes) continue;

            event.setNotes(buildPlagNotes(event.getNotes(), fmt.format(plagTime)));
        }
    }

    // -----------------------------------------------------------------------
    // Shared internals
    // -----------------------------------------------------------------------

    private Date getPlag(LocalDate date) {
        try {
            Dictionary<Zman, Date> zmanim = zmanimHandler.getZmanim(date);
            return zmanim != null ? zmanim.get(Zman.PLAG_HAMINCHA) : null;
        } catch (Exception e) {
            log.warn("Could not compute plag for {}: {}", date, e.getMessage());
            return null;
        }
    }

    /** Strip existing Shkiya:/Plag: fragments and append "Plag: <time>". */
    private static String buildPlagNotes(String existing, String formattedPlag) {
        String plagLabel = "Plag: " + formattedPlag;
        String stripped = existing == null ? "" : Arrays.stream(existing.split(" \\| "))
                .map(String::trim)
                .filter(p -> !p.startsWith("Shkiya:") && !p.startsWith("Plag:"))
                .reduce((a, b) -> a + " | " + b)
                .orElse("");
        return stripped.isEmpty() ? plagLabel : stripped + " | " + plagLabel;
    }

    private SimpleDateFormat buildFmt() {
        SimpleDateFormat fmt = new SimpleDateFormat("h:mm aa");
        fmt.setTimeZone(settingsService.getTimeZone());
        return fmt;
    }
}
