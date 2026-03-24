package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.enums.EventSource;
import com.tbdev.teaneckminyanim.model.CalendarEvent;
import com.tbdev.teaneckminyanim.repo.CalendarEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for retrieving effective schedule from materialized calendar events.
 * 
 * Applies precedence rules:
 * - If any MANUAL full-day overrides exist for org+date, only MANUAL events are shown
 * - Otherwise, IMPORTED events override RULES events for that day
 * - MANUAL additive overrides are appended to the winning source for that day
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EffectiveScheduleService {

    public static final String MANUAL_FULL_DAY_SOURCE_REF_PREFIX = "manual:FULL_DAY_REPLACE";

    private final CalendarEventRepository calendarEventRepository;
    private final CalendarMaterializationService materializationService;

    /**
     * Get effective events for an organization on a specific date.
     * Applies MANUAL/IMPORTED/RULES precedence rules.
     * 
     * @param organizationId Organization ID
     * @param date Date to get events for
     * @return List of effective events (enabled only, with precedence applied)
     */
    public List<CalendarEvent> getEffectiveEventsForDate(String organizationId, LocalDate date) {
        // Check if date is within materialization window
        if (!materializationService.isDateInWindow(date)) {
            log.debug("Date {} is outside materialization window for org {}", date, organizationId);
            return List.of();
        }

        List<CalendarEvent> dayEvents = calendarEventRepository
                .findByOrganizationIdAndDateAndEnabledTrue(organizationId, date);
        return applyDayPrecedence(dayEvents);
    }

    /**
     * Get effective events for an organization within a date range.
     * 
     * @param organizationId Organization ID
     * @param startDate Start of range (inclusive)
     * @param endDate End of range (inclusive)
     * @return List of effective events with precedence applied
     */
    public List<CalendarEvent> getEffectiveEventsInRange(String organizationId, LocalDate startDate, LocalDate endDate) {
        // Get all enabled events in range
        List<CalendarEvent> allEvents = calendarEventRepository
                .findEnabledEventsInRange(organizationId, startDate, endDate);

        // Group by date and apply precedence
        return allEvents.stream()
                .collect(Collectors.groupingBy(CalendarEvent::getDate))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .flatMap(entry -> applyDayPrecedence(entry.getValue()).stream())
                .sorted(Comparator.comparing(CalendarEvent::getDate).thenComparing(CalendarEvent::getStartTime))
                .collect(Collectors.toList());
    }

    /**
     * Get effective events across ALL organizations for a date range.
     * Used by the combined /api/v1/schedule endpoint.
     * Applies day-level per-org MANUAL/IMPORTED/RULES precedence.
     *
     * @param startDate Start of range (inclusive)
     * @param endDate   End of range (inclusive)
     * @return Effective events sorted by date then startTime
     */
    public List<CalendarEvent> getAllOrgsEffectiveEventsInRange(LocalDate startDate, LocalDate endDate) {
        List<CalendarEvent> allEvents = calendarEventRepository.findAllEnabledEventsInRange(startDate, endDate);

        // Group by orgId+date key, apply precedence per org per day
        return allEvents.stream()
                .collect(Collectors.groupingBy(e -> e.getOrganizationId() + "|" + e.getDate()))
                .values().stream()
                .flatMap(dayOrgEvents -> applyDayPrecedence(dayOrgEvents).stream())
                .sorted(Comparator.comparing(CalendarEvent::getDate).thenComparing(CalendarEvent::getStartTime))
                .collect(Collectors.toList());
    }

    /**
     * Get all events (including disabled) for admin views.
     * Does NOT apply precedence - shows everything.
     * 
     * @param organizationId Organization ID
     * @param date Date to get events for
     * @return All events for the date
     */
    public List<CalendarEvent> getAllEventsForDate(String organizationId, LocalDate date) {
        return calendarEventRepository.findByOrganizationIdAndDate(organizationId, date);
    }

    /**
     * Get all events (including disabled) within a date range for admin views.
     * Does NOT apply precedence - shows everything.
     * 
     * @param organizationId Organization ID
     * @param startDate Start of range (inclusive)
     * @param endDate End of range (inclusive)
     * @return All events in the range
     */
    public List<CalendarEvent> getAllEventsInRange(String organizationId, LocalDate startDate, LocalDate endDate) {
        return calendarEventRepository.findEventsInRange(organizationId, startDate, endDate);
    }

    /**
     * Check if a date is within the materialization window.
     */
    public boolean isDateInWindow(LocalDate date) {
        return materializationService.isDateInWindow(date);
    }

    /**
     * Get the materialization window bounds.
     */
    public CalendarMaterializationService.WindowBounds getWindowBounds() {
        return materializationService.getWindowBounds();
    }

    private List<CalendarEvent> applyDayPrecedence(List<CalendarEvent> dayEvents) {
        if (dayEvents == null || dayEvents.isEmpty()) {
            return List.of();
        }

        List<CalendarEvent> manualEvents = dayEvents.stream()
                .filter(e -> e.getSource() == EventSource.MANUAL)
                .collect(Collectors.toList());

        boolean hasFullDayManualOverride = manualEvents.stream()
                .anyMatch(this::isFullDayManualOverride);

        if (hasFullDayManualOverride) {
            CalendarEvent sample = dayEvents.get(0);
            log.debug("Using MANUAL full-day override events for org {} on {}", sample.getOrganizationId(), sample.getDate());
            return sortByTime(manualEvents);
        }

        boolean hasImported = dayEvents.stream()
                .anyMatch(e -> e.getSource() == EventSource.IMPORTED);

        Stream<CalendarEvent> baseEvents = hasImported
                ? dayEvents.stream().filter(e -> e.getSource() == EventSource.IMPORTED)
                : dayEvents.stream().filter(e -> e.getSource() == EventSource.RULES);

        Stream<CalendarEvent> effectiveEvents = manualEvents.isEmpty()
                ? baseEvents
                : Stream.concat(baseEvents, manualEvents.stream());

        return effectiveEvents
                .sorted(Comparator.comparing(CalendarEvent::getStartTime))
                .collect(Collectors.toList());
    }

    private boolean isFullDayManualOverride(CalendarEvent event) {
        return event.getSource() == EventSource.MANUAL
                && event.getSourceRef() != null
                && event.getSourceRef().startsWith(MANUAL_FULL_DAY_SOURCE_REF_PREFIX);
    }

    private List<CalendarEvent> sortByTime(List<CalendarEvent> events) {
        return events.stream()
                .sorted(Comparator.comparing(CalendarEvent::getStartTime))
                .collect(Collectors.toList());
    }
}
