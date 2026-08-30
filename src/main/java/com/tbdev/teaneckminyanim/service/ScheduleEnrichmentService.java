package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.api.dto.ScheduleEventDto;
import com.tbdev.teaneckminyanim.enums.Zman;
import com.tbdev.teaneckminyanim.front.MinyanEvent;
import com.tbdev.teaneckminyanim.minyan.MinyanType;
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
    private static final String SELICHOS_SHACHARIS_DISPLAY = "Selichos & Shacharis";
    private static final long MAX_LINKED_SHACHARIS_GAP_MILLIS = 45L * 60 * 1000;
    private static final int MAX_LINKED_SHACHARIS_GAP_MINUTES = 45;

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
        Map<String, Date> alosByDate = new HashMap<>();
        Map<String, Date> tzesByDate = new HashMap<>();
        for (ScheduleEventDto dto : dtos) {
            shkiyaByDate.computeIfAbsent(dto.date(), d -> getZman(LocalDate.parse(d), Zman.SHEKIYA));
            plagByDate.computeIfAbsent(dto.date(), d -> getZman(LocalDate.parse(d), Zman.PLAG_HAMINCHA));
            alosByDate.computeIfAbsent(dto.date(), d -> getZman(LocalDate.parse(d), Zman.ALOS_HASHACHAR));
            tzesByDate.computeIfAbsent(dto.date(), d -> getZman(LocalDate.parse(d), Zman.TZES));
        }

        SimpleDateFormat fmt = buildFmt();
        ZoneId zoneId = settingsService.getZoneId();
        long thirtyMinutes = 30L * 60 * 1000;

        List<ScheduleEventDto> enriched = dtos.stream().map(dto -> {
            ScheduleEventDto result = dto;
            String type = dto.minyanType();
            result = annotateSelichosDisplay(
                    result,
                    alosByDate.get(result.date()),
                    tzesByDate.get(result.date()),
                    zoneId);

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

        return attachLinkedShacharisTimes(enriched);
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
        Date alosTime = zmanim != null ? zmanim.get(Zman.ALOS_HASHACHAR) : null;
        Date tzesTime = zmanim != null ? zmanim.get(Zman.TZES) : null;
        boolean hasZmanim = shkiyaTime != null || plagTime != null || alosTime != null || tzesTime != null;

        SimpleDateFormat fmt = buildFmt();
        long thirtyMinutes = 30L * 60 * 1000;

        for (MinyanEvent event : events) {
            if (event.getStartTime() == null) continue;
            if (hasZmanim) {
                annotateSelichosDisplay(event, alosTime, tzesTime);
            } else if (event.getType().isSelichosShacharis()) {
                event.setDisplayTypeName(SELICHOS_SHACHARIS_DISPLAY);
                event.setPublicGroup(MinyanType.SELICHOS_SHACHARIS.name());
                event.setPublicGroupDisplayName(SELICHOS_SHACHARIS_DISPLAY);
            }

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

        attachLinkedShacharisTimesForWeb(events);
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

    private ScheduleEventDto annotateSelichosDisplay(
            ScheduleEventDto dto,
            Date alosTime,
            Date tzesTime,
            ZoneId zoneId) {
        if (MinyanType.SELICHOS_SHACHARIS.name().equals(dto.minyanType())) {
            return dto.withDisplayContext(
                    MinyanType.SELICHOS_SHACHARIS.name(),
                    SELICHOS_SHACHARIS_DISPLAY,
                    MinyanType.SELICHOS_SHACHARIS.name(),
                    SELICHOS_SHACHARIS_DISPLAY);
        }
        if (!MinyanType.SELICHOS.name().equals(dto.minyanType())) {
            return dto;
        }
        if (isNightSelichos(dto.date(), dto.startTime(), alosTime, tzesTime, zoneId)) {
            return dto.withDisplayContext(
                    "NIGHT_SELICHOS",
                    "Night Selichos",
                    "NIGHT_SELICHOS",
                    "Night Selichos");
        }
        return dto.withDisplayContext(
                MinyanType.SELICHOS_SHACHARIS.name(),
                SELICHOS_SHACHARIS_DISPLAY,
                MinyanType.SELICHOS_SHACHARIS.name(),
                SELICHOS_SHACHARIS_DISPLAY);
    }

    private void annotateSelichosDisplay(MinyanEvent event, Date alosTime, Date tzesTime) {
        if (event.getType().isSelichosShacharis()) {
            event.setDisplayTypeName(SELICHOS_SHACHARIS_DISPLAY);
            event.setPublicGroup(MinyanType.SELICHOS_SHACHARIS.name());
            event.setPublicGroupDisplayName(SELICHOS_SHACHARIS_DISPLAY);
            return;
        }
        if (!event.getType().isSelichos()) {
            return;
        }
        if (isNightSelichos(event.getStartTime(), alosTime, tzesTime)) {
            event.setDisplayTypeName("Night Selichos");
            event.setPublicGroup("NIGHT_SELICHOS");
            event.setPublicGroupDisplayName("Night Selichos");
        } else {
            event.setDisplayTypeName(SELICHOS_SHACHARIS_DISPLAY);
            event.setPublicGroup(MinyanType.SELICHOS_SHACHARIS.name());
            event.setPublicGroupDisplayName(SELICHOS_SHACHARIS_DISPLAY);
        }
    }

    private boolean isNightSelichos(
            String date,
            String startTime,
            Date alosTime,
            Date tzesTime,
            ZoneId zoneId) {
        try {
            Date eventTime = Date.from(
                    LocalDate.parse(date).atTime(LocalTime.parse(startTime)).atZone(zoneId).toInstant());
            return isNightSelichos(eventTime, alosTime, tzesTime);
        } catch (Exception e) {
            log.warn("Could not classify Selichos display period for {} {}: {}", date, startTime, e.getMessage());
            return false;
        }
    }

    private boolean isNightSelichos(Date eventTime, Date alosTime, Date tzesTime) {
        boolean beforeAlos = alosTime != null && eventTime.before(alosTime);
        boolean afterTzes = tzesTime != null && eventTime.after(tzesTime);
        return beforeAlos || afterTzes;
    }

    private void attachLinkedShacharisTimesForWeb(List<MinyanEvent> events) {
        for (MinyanEvent event : events) {
            if (!isSelichosShacharisPublicEvent(event) || event.getStartTime() == null) {
                continue;
            }
            findLinkedShacharisEvent(event, events).ifPresent(linked -> {
                if (linked.getStartTime().after(event.getStartTime())) {
                    event.setLinkedMinyanType(MinyanType.SHACHARIS);
                    event.setLinkedStartTime(linked.getStartTime());
                }
                linked.setLinkedTarget(true);
            });
        }
    }

    private boolean isSelichosShacharisPublicEvent(MinyanEvent event) {
        return MinyanType.SELICHOS_SHACHARIS.name().equals(event.getPublicGroup())
                && event.getType() != MinyanType.SHACHARIS;
    }

    private Optional<MinyanEvent> findLinkedShacharisEvent(MinyanEvent linked, List<MinyanEvent> events) {
        List<MinyanEvent> candidates = events.stream()
                .filter(candidate -> candidate.getType() == MinyanType.SHACHARIS)
                .filter(candidate -> candidate.getStartTime() != null)
                .filter(candidate -> Objects.equals(candidate.getOrganizationId(), linked.getOrganizationId()))
                .filter(candidate -> !candidate.getStartTime().before(linked.getStartTime()))
                .filter(candidate -> withinLinkedShacharisWindow(linked.getStartTime(), candidate.getStartTime()))
                .sorted(Comparator.comparing(MinyanEvent::getStartTime))
                .collect(Collectors.toList());

        return candidates.stream()
                .filter(candidate -> sameLocationWhenPresent(candidate, linked))
                .findFirst()
                .or(() -> candidates.stream()
                        .findFirst());
    }

    private List<ScheduleEventDto> attachLinkedShacharisTimes(List<ScheduleEventDto> dtos) {
        Map<String, String> linkedStartTimesById = new HashMap<>();
        Set<String> linkedTargetIds = new HashSet<>();
        for (ScheduleEventDto dto : dtos) {
            if (!isSelichosShacharisPublicDto(dto)) {
                continue;
            }
            findLinkedShacharis(dto, dtos).ifPresent(linked -> {
                if (linked.startTime().compareTo(dto.startTime()) > 0) {
                    linkedStartTimesById.put(dto.id(), linked.startTime());
                }
                linkedTargetIds.add(linked.id());
            });
        }

        return dtos.stream()
                .map(dto -> {
                    ScheduleEventDto result = dto;
                    if (linkedStartTimesById.containsKey(result.id())) {
                        result = result.withLinkedMinyan(
                                MinyanType.SHACHARIS.name(),
                                MinyanType.SHACHARIS.displayName(),
                                linkedStartTimesById.get(result.id()));
                    }
                    if (linkedTargetIds.contains(result.id())) {
                        result = result.withLinkedTarget(true);
                    }
                    return result;
                })
                .collect(Collectors.toList());
    }

    private boolean isSelichosShacharisPublicDto(ScheduleEventDto dto) {
        return MinyanType.SELICHOS_SHACHARIS.name().equals(dto.groupMinyanType())
                && !MinyanType.SHACHARIS.name().equals(dto.minyanType());
    }

    private Optional<ScheduleEventDto> findLinkedShacharis(ScheduleEventDto linked, List<ScheduleEventDto> dtos) {
        List<ScheduleEventDto> candidates = dtos.stream()
                .filter(candidate -> MinyanType.SHACHARIS.name().equals(candidate.minyanType()))
                .filter(candidate -> Objects.equals(candidate.date(), linked.date()))
                .filter(candidate -> sameOrganization(candidate, linked))
                .filter(candidate -> candidate.startTime().compareTo(linked.startTime()) >= 0)
                .filter(candidate -> withinLinkedShacharisWindow(linked.startTime(), candidate.startTime()))
                .sorted(Comparator.comparing(ScheduleEventDto::startTime))
                .collect(Collectors.toList());

        return candidates.stream()
                .filter(candidate -> sameLocationWhenPresent(candidate, linked))
                .findFirst()
                .or(() -> candidates.stream()
                        .findFirst());
    }

    private boolean withinLinkedShacharisWindow(Date linkedStart, Date shacharisStart) {
        long diff = shacharisStart.getTime() - linkedStart.getTime();
        return diff >= 0 && diff <= MAX_LINKED_SHACHARIS_GAP_MILLIS;
    }

    private boolean withinLinkedShacharisWindow(String linkedStart, String shacharisStart) {
        try {
            int diff = LocalTime.parse(shacharisStart).toSecondOfDay()
                    - LocalTime.parse(linkedStart).toSecondOfDay();
            return diff >= 0 && diff <= MAX_LINKED_SHACHARIS_GAP_MINUTES * 60;
        } catch (Exception e) {
            log.warn("Could not compare linked Selichos/Shacharis times {} -> {}: {}",
                    linkedStart, shacharisStart, e.getMessage());
            return false;
        }
    }

    private boolean sameOrganization(ScheduleEventDto a, ScheduleEventDto b) {
        return a.organization() != null
                && b.organization() != null
                && Objects.equals(a.organization().id(), b.organization().id());
    }

    private boolean sameLocationWhenPresent(ScheduleEventDto a, ScheduleEventDto b) {
        if (a.locationName() == null || a.locationName().isBlank()
                || b.locationName() == null || b.locationName().isBlank()) {
            return true;
        }
        return a.locationName().trim().equalsIgnoreCase(b.locationName().trim());
    }

    private boolean sameLocationWhenPresent(MinyanEvent a, MinyanEvent b) {
        if (a.getLocationName() == null || a.getLocationName().isBlank()
                || b.getLocationName() == null || b.getLocationName().isBlank()) {
            return true;
        }
        return a.getLocationName().trim().equalsIgnoreCase(b.getLocationName().trim());
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
