package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.enums.EventSource;
import com.tbdev.teaneckminyanim.model.CalendarEvent;
import com.tbdev.teaneckminyanim.repo.CalendarEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for retrieving effective schedule from materialized calendar events.
 * 
 * Applies precedence rules:
 * - Day-level override: If any IMPORTED events exist for org+date, only IMPORTED events are shown
 * - Otherwise, RULES events are shown
 * - MANUAL events (future) will override both
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EffectiveScheduleService {

    private final CalendarEventRepository calendarEventRepository;
    private final CalendarMaterializationService materializationService;

    /**
     * Get effective events for an organization on a specific date.
     * Applies day-level precedence: imported overrides rules.
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

        // Check if any imported events exist for this day
        boolean hasImported = calendarEventRepository.existsByOrganizationIdAndDateAndSourceAndEnabledTrue(
                organizationId, date, EventSource.IMPORTED);

        if (hasImported) {
            // Return only imported events (day-level override)
            log.debug("Using IMPORTED events for org {} on {} (day-level override)", organizationId, date);
            List<CalendarEvent> events = calendarEventRepository
                    .findByOrganizationIdAndDateAndEnabledTrue(organizationId, date);
            return events.stream()
                    .filter(e -> e.getSource() == EventSource.IMPORTED)
                    .collect(Collectors.toList());
        } else {
            // Return rules events
            log.debug("Using RULES events for org {} on {}", organizationId, date);
            List<CalendarEvent> events = calendarEventRepository
                    .findByOrganizationIdAndDateAndEnabledTrue(organizationId, date);
            return events.stream()
                    .filter(e -> e.getSource() == EventSource.RULES)
                    .collect(Collectors.toList());
        }
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
                .flatMap(entry -> {
                    LocalDate date = entry.getKey();
                    List<CalendarEvent> dayEvents = entry.getValue();

                    // Check if any imported events exist for this day
                    boolean hasImported = dayEvents.stream()
                            .anyMatch(e -> e.getSource() == EventSource.IMPORTED);

                    if (hasImported) {
                        // Return only imported events for this day
                        return dayEvents.stream()
                                .filter(e -> e.getSource() == EventSource.IMPORTED);
                    } else {
                        // Return rules events for this day
                        return dayEvents.stream()
                                .filter(e -> e.getSource() == EventSource.RULES);
                    }
                })
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
}
