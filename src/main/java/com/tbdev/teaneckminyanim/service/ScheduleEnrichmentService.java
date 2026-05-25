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
import java.util.regex.Pattern;
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

    private static final Pattern GENERATED_ZMAN_NOTE_PATTERN = Pattern.compile(
            "(?i)(^|\\s*\\|\\s*|\\.\\s*)\\b(?:Shkiya|Plag):\\s*\\d{1,2}:\\d{2}\\s*[AP]M\\b\\.?\\s*");

    // -----------------------------------------------------------------------
    // API path — operates on ScheduleEventDto (immutable record)
    // -----------------------------------------------------------------------

    /**
     * Apply final zman notes to schedule DTOs. MINCHA_MAARIV events receive the
     * day's Shkiya at response time, and MAARIV / MINCHA_MAARIV events near Plag
     * receive Plag instead.
     *
     * @param dtos sorted list of events for one or more dates
     * @return new list with notes patched where appropriate
     */
    public List<ScheduleEventDto> annotateZmanim(List<ScheduleEventDto> dtos) {
        if (dtos.isEmpty()) return dtos;

        // Compute zmanim once per distinct date
        Map<String, Date> shkiyaByDate = new HashMap<>();
        Map<String, Date> plagByDate = new HashMap<>();
        for (ScheduleEventDto dto : dtos) {
            shkiyaByDate.computeIfAbsent(dto.date(), d -> getZman(LocalDate.parse(d), Zman.SHEKIYA));
            plagByDate.computeIfAbsent(dto.date(), d -> getZman(LocalDate.parse(d), Zman.PLAG_HAMINCHA));
        }

        SimpleDateFormat fmt = buildFmt();
        ZoneId zoneId = settingsService.getZoneId();
        long thirtyMinutes = 30L * 60 * 1000;

        return dtos.stream().map(dto -> {
            ScheduleEventDto result = dto;
            String type = dto.minyanType();

            if ("MINCHA_MAARIV".equals(type)) {
                Date shkiyaTime = shkiyaByDate.get(dto.date());
                if (shkiyaTime != null) {
                    result = result.withNotes(buildShkiyaNotes(result.notes(), fmt.format(shkiyaTime)));
                }
            }

            if (result.dynamicTimeString() != null) return result;
            if (!"MAARIV".equals(type) && !"MINCHA_MAARIV".equals(type)) return result;

            Date plagTime = plagByDate.get(result.date());
            if (plagTime == null) return result;

            try {
                Date eventTime = Date.from(
                        LocalDate.parse(result.date()).atTime(LocalTime.parse(result.startTime()))
                                .atZone(zoneId).toInstant());
                if (Math.abs(eventTime.getTime() - plagTime.getTime()) > thirtyMinutes) return result;
                return result.withNotes(buildPlagNotes(result.notes(), fmt.format(plagTime)));
            } catch (Exception e) {
                log.warn("Plag annotation failed for event {}: {}", result.id(), e.getMessage());
                return result;
            }
        }).collect(Collectors.toList());
    }

    /**
     * Backward-compatible entry point for callers/tests still using the old name.
     */
    public List<ScheduleEventDto> annotatePlag(List<ScheduleEventDto> dtos) {
        return annotateZmanim(dtos);
    }

    // -----------------------------------------------------------------------
    // Web path — operates on MinyanEvent (mutable, already has zmanim in scope)
    // -----------------------------------------------------------------------

    /**
     * Same annotation logic as {@link #annotateZmanim(List)}, operating on the
     * mutable {@link MinyanEvent} objects used by the web frontend.
     *
     * @param events list to annotate in-place
     * @param zmanim pre-computed zmanim for the rendered date (saves a lookup)
     */
    public void annotateZmanim(List<MinyanEvent> events, Dictionary<Zman, Date> zmanim) {
        Date shkiyaTime = zmanim != null ? zmanim.get(Zman.SHEKIYA) : null;
        Date plagTime = zmanim != null ? zmanim.get(Zman.PLAG_HAMINCHA) : null;
        if (shkiyaTime == null && plagTime == null) return;

        SimpleDateFormat fmt = buildFmt();
        long thirtyMinutes = 30L * 60 * 1000;

        for (MinyanEvent event : events) {
            if (event.getStartTime() == null) continue;

            if (event.getType().isMinchaMariv() && shkiyaTime != null) {
                event.setNotes(buildShkiyaNotes(event.getNotes(), fmt.format(shkiyaTime)));
            }

            if (plagTime == null) continue;
            if (!event.getType().isMaariv() && !event.getType().isMinchaMariv()) continue;
            if (event.dynamicTimeString() != null) continue;

            long diff = Math.abs(event.getStartTime().getTime() - plagTime.getTime());
            if (diff > thirtyMinutes) continue;

            event.setNotes(buildPlagNotes(event.getNotes(), fmt.format(plagTime)));
        }
    }

    /**
     * Backward-compatible entry point for callers/tests still using the old name.
     */
    public void annotatePlag(List<MinyanEvent> events, Dictionary<Zman, Date> zmanim) {
        annotateZmanim(events, zmanim);
    }

    // -----------------------------------------------------------------------
    // Shared internals
    // -----------------------------------------------------------------------

    private Date getZman(LocalDate date, Zman zman) {
        try {
            Dictionary<Zman, Date> zmanim = zmanimHandler.getZmanim(date);
            return zmanim != null ? zmanim.get(zman) : null;
        } catch (Exception e) {
            log.warn("Could not compute {} for {}: {}", zman, date, e.getMessage());
            return null;
        }
    }

    /** Strip existing Shkiya:/Plag: fragments and append "Shkiya: <time>". */
    private static String buildShkiyaNotes(String existing, String formattedShkiya) {
        return buildGeneratedZmanNotes(existing, "Shkiya: " + formattedShkiya);
    }

    /** Strip existing Shkiya:/Plag: fragments and append "Plag: <time>". */
    private static String buildPlagNotes(String existing, String formattedPlag) {
        return buildGeneratedZmanNotes(existing, "Plag: " + formattedPlag);
    }

    private static String buildGeneratedZmanNotes(String existing, String label) {
        String stripped = existing == null ? "" : GENERATED_ZMAN_NOTE_PATTERN.matcher(existing)
                .replaceAll(" ")
                .replaceAll("\\s+", " ")
                .trim();
        stripped = stripped.isEmpty() ? "" : Arrays.stream(stripped.split("\\s*\\|\\s*"))
                .map(String::trim)
                .filter(p -> !p.isEmpty())
                .reduce((a, b) -> a + " | " + b)
                .orElse("");
        return stripped.isEmpty() ? label : stripped + " | " + label;
    }

    private SimpleDateFormat buildFmt() {
        SimpleDateFormat fmt = new SimpleDateFormat("h:mm aa");
        fmt.setTimeZone(settingsService.getTimeZone());
        return fmt;
    }
}
