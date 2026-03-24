package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.enums.EventSource;
import com.tbdev.teaneckminyanim.minyan.MinyanTime;
import com.tbdev.teaneckminyanim.minyan.MinyanType;
import com.tbdev.teaneckminyanim.model.*;
import com.tbdev.teaneckminyanim.repo.CalendarEventRepository;
import com.tbdev.teaneckminyanim.repo.OrganizationCalendarEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service responsible for materializing calendar events from various sources.
 * 
 * Materialization strategy:
 * - RULES events: Delete + rebuild for rolling window (past 3 weeks, next 8 weeks)
 * - IMPORTED events: Materialize from OrganizationCalendarEntry (preserve existing)
 * - Window: Past 3 weeks to next 8 weeks from today
 * - Cadence: Weekly (triggered by scheduled job or manual admin action)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarMaterializationService {

    private final CalendarEventRepository calendarEventRepository;
    private final OrganizationCalendarEntryRepository importedEntryRepository;
    private final MinyanService minyanService;
    private final OrganizationService organizationService;
    private final LocationService locationService;
    private final ApplicationSettingsService settingsService;
    private final ZmanimHandler zmanimHandler;

    // Rolling window configuration
    private static final int PAST_WEEKS = 3;
    private static final int FUTURE_WEEKS = 8;

    /**
     * Materialize all events for all organizations in the rolling window.
     * This is the main entry point for scheduled materialization.
     */
    @Transactional
    public void materializeAll() {
        log.info("Starting full materialization for all organizations");
        
        List<Organization> allOrgs = organizationService.getAll();
        
        for (Organization org : allOrgs) {
            try {
                materializeOrganization(org.getId());
            } catch (Exception e) {
                log.error("Failed to materialize organization {}: {}", org.getId(), e.getMessage(), e);
            }
        }
        
        // Cleanup old events outside the window
        cleanupOldEvents();
        
        log.info("Completed full materialization for {} organizations", allOrgs.size());
    }

    /**
     * Materialize events for a specific organization.
     * Public method for manual admin triggers.
     */
    @Transactional
    public void materializeOrganization(String organizationId) {
        log.info("Materializing events for organization: {}", organizationId);
        
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusWeeks(PAST_WEEKS);
        LocalDate endDate = today.plusWeeks(FUTURE_WEEKS);
        
        // Step 1: Delete existing RULES events in the window
        log.debug("Deleting RULES events for {} from {} to {}", organizationId, startDate, endDate);
        calendarEventRepository.deleteRulesEventsInRange(organizationId, startDate, endDate);
        
        // Step 2: Generate new RULES events
        List<CalendarEvent> rulesEvents = generateRulesEvents(organizationId, startDate, endDate);
        log.debug("Generated {} RULES events for {}", rulesEvents.size(), organizationId);
        calendarEventRepository.saveAll(rulesEvents);
        
        // Step 3: Materialize IMPORTED events (if any new ones exist)
        List<CalendarEvent> importedEvents = materializeImportedEvents(organizationId, startDate, endDate);
        log.debug("Materialized {} IMPORTED events for {}", importedEvents.size(), organizationId);
        calendarEventRepository.saveAll(importedEvents);
        
        log.info("Completed materialization for organization {}: {} rules, {} imported", 
                organizationId, rulesEvents.size(), importedEvents.size());
    }

    /**
     * Generate calendar events from rule-based minyanim.
     */
    private List<CalendarEvent> generateRulesEvents(String organizationId, LocalDate startDate, LocalDate endDate) {
        List<CalendarEvent> events = new ArrayList<>();
        
        // Get all enabled minyanim for this organization
        List<Minyan> minyanim = minyanService.findEnabledMatching(organizationId);
        
        Optional<Organization> orgOpt = organizationService.findById(organizationId);
        if (orgOpt.isEmpty()) {
            log.warn("Organization not found: {}", organizationId);
            return events;
        }
        Organization org = orgOpt.get();
        
        // Get timezone from settings
        ZoneId zoneId = settingsService.getZoneId();
        
        // Iterate through each day in the window
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            final LocalDate date = currentDate;
            
            // For each minyan, check if it has a service on this date
            for (Minyan minyan : minyanim) {
                try {
                    MinyanTime minyanTime = minyan.getMinyanTime(date);

                    if (minyanTime != null) {
                        // Use resolveLocalTime with the properly-configured zmanimHandler.
                        // This avoids the bug in TimeRule.getTime() which creates its own
                        // ZmanimHandler() that falls back to Jerusalem coordinates.
                        LocalTime startTime = minyanTime.resolveLocalTime(
                                zmanimHandler::getZmanim, date, zoneId);

                        if (startTime != null) {
                            
                            // Get location details
                            String locationId = minyan.getLocationId();
                            String locationName = null;
                            Location location = locationService.findById(locationId);
                            if (location != null) {
                                locationName = location.getName();
                            }
                            
                            // Build dynamic time string if applicable
                            String dynamicTimeString = minyanTime.dynamicDisplayName();
                            String roundedTimeString = minyanTime.roundedDisplayName();
                            String displayString = dynamicTimeString != null ? dynamicTimeString : roundedTimeString;
                            
                            // Create calendar event
                            CalendarEvent event = CalendarEvent.builder()
                                    .organizationId(organizationId)
                                    .date(date)
                                    .minyanType(minyan.getType())
                                    .startTime(startTime)
                                    .notes(minyan.getNotes())
                                    .locationId(locationId)
                                    .locationName(locationName)
                                    .enabled(true)  // Rules-based events are enabled by default
                                    .source(EventSource.RULES)
                                    .sourceRef(minyan.getId())
                                    .nusach(minyan.getNusach())
                                    .whatsapp(minyan.getWhatsapp())
                                    .dynamicTimeString(displayString)
                                    .manuallyEdited(false)
                                    .build();
                            
                            events.add(event);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to generate event from minyan {} for date {}: {}", 
                            minyan.getId(), date, e.getMessage());
                }
            }
            
            currentDate = currentDate.plusDays(1);
        }
        
        return events;
    }

    /**
     * Materialize imported calendar entries into calendar events.
     * Creates missing rows and syncs existing rows (including enabled drift).
     */
    private List<CalendarEvent> materializeImportedEvents(String organizationId, LocalDate startDate, LocalDate endDate) {
        List<OrganizationCalendarEntry> entries =
                importedEntryRepository.findEntriesInRange(organizationId, startDate, endDate);

        Optional<Organization> orgOpt = organizationService.findById(organizationId);
        if (orgOpt.isEmpty()) {
            log.warn("Organization not found: {}", organizationId);
            return List.of();
        }
        Organization org = orgOpt.get();

        List<CalendarEvent> existingImported =
                calendarEventRepository.findByOrganizationIdAndSourceAndDateBetween(
                        organizationId, EventSource.IMPORTED, startDate, endDate);

        Map<String, CalendarEvent> bySourceRef = new HashMap<>();
        for (CalendarEvent existing : existingImported) {
            if (existing.getSourceRef() != null) {
                bySourceRef.put(existing.getSourceRef(), existing);
            }
        }

        List<CalendarEvent> eventsToSave = new ArrayList<>();
        for (OrganizationCalendarEntry entry : entries) {
            upsertImportedEventFromEntry(entry, org, bySourceRef, eventsToSave);
        }

        return eventsToSave;
    }

    /**
     * Live-sync a single imported entry into materialized events (window-only).
     * Used by admin toggle/edit actions so updates are visible immediately.
     */
    @Transactional
    public void syncImportedEntryLive(OrganizationCalendarEntry entry) {
        if (entry == null || entry.getId() == null) {
            return;
        }
        if (!isDateInWindow(entry.getDate())) {
            return;
        }
        syncImportedEntriesInRangeLive(entry.getOrganizationId(), entry.getDate(), entry.getDate());
    }

    /**
     * Live-sync imported entries in a date range into materialized events (window-only).
     * This keeps schedule/API behavior current without requiring rematerialization.
     */
    @Transactional
    public void syncImportedEntriesInRangeLive(String organizationId, LocalDate startDate, LocalDate endDate) {
        if (organizationId == null || startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return;
        }

        WindowBounds bounds = getWindowBounds();
        LocalDate effectiveStart = startDate.isBefore(bounds.getStartDate()) ? bounds.getStartDate() : startDate;
        LocalDate effectiveEnd = endDate.isAfter(bounds.getEndDate()) ? bounds.getEndDate() : endDate;
        if (effectiveEnd.isBefore(effectiveStart)) {
            return;
        }

        Optional<Organization> orgOpt = organizationService.findById(organizationId);
        if (orgOpt.isEmpty()) {
            log.warn("Organization not found while live-syncing imported entries: {}", organizationId);
            return;
        }
        Organization org = orgOpt.get();

        List<OrganizationCalendarEntry> entries =
                importedEntryRepository.findEntriesInRange(organizationId, effectiveStart, effectiveEnd);
        List<CalendarEvent> existingImported =
                calendarEventRepository.findByOrganizationIdAndSourceAndDateBetween(
                        organizationId, EventSource.IMPORTED, effectiveStart, effectiveEnd);

        Map<String, CalendarEvent> bySourceRef = new HashMap<>();
        for (CalendarEvent existing : existingImported) {
            if (existing.getSourceRef() != null) {
                bySourceRef.put(existing.getSourceRef(), existing);
            }
        }

        List<CalendarEvent> eventsToSave = new ArrayList<>();
        for (OrganizationCalendarEntry entry : entries) {
            upsertImportedEventFromEntry(entry, org, bySourceRef, eventsToSave);
        }

        if (!eventsToSave.isEmpty()) {
            calendarEventRepository.saveAll(eventsToSave);
            log.debug("Live-synced {} imported materialized events for {}", eventsToSave.size(), organizationId);
        }
    }

    private void upsertImportedEventFromEntry(
            OrganizationCalendarEntry entry,
            Organization org,
            Map<String, CalendarEvent> existingBySourceRef,
            List<CalendarEvent> eventsToSave) {
        if (entry == null || entry.getId() == null) {
            return;
        }

        String sourceRef = importedSourceRef(entry.getId());
        CalendarEvent existingEvent = existingBySourceRef.get(sourceRef);

        LocalTime resolvedStartTime = resolveStartTime(entry);
        MinyanType minyanType = entry.getClassification() != null
                ? entry.getClassification()
                : MinyanType.OTHER;
        boolean materializable = resolvedStartTime != null && minyanType != MinyanType.NON_MINYAN;

        if (!materializable) {
            if (existingEvent != null && existingEvent.isEnabled()) {
                existingEvent.setEnabled(false);
                eventsToSave.add(existingEvent);
            }
            return;
        }

        CalendarEvent target = existingEvent != null
                ? existingEvent
                : CalendarEvent.builder()
                .organizationId(entry.getOrganizationId())
                .source(EventSource.IMPORTED)
                .sourceRef(sourceRef)
                .whatsapp(null)
                .dynamicTimeString(null)
                .manuallyEdited(false)
                .build();

        target.setDate(entry.getDate());
        target.setMinyanType(minyanType);
        target.setStartTime(resolvedStartTime);
        target.setNotes(entry.getNotes());
        target.setLocationId(null);
        target.setLocationName(entry.getLocation());
        target.setEnabled(entry.isEnabled());
        target.setNusach(org.getNusach());

        eventsToSave.add(target);
    }

    private LocalTime resolveStartTime(OrganizationCalendarEntry entry) {
        if (entry.getStartTime() != null) {
            return entry.getStartTime();
        }
        if (entry.getStartDatetime() != null) {
            return entry.getStartDatetime().toLocalTime();
        }
        return null;
    }

    private String importedSourceRef(Long entryId) {
        return "import-" + entryId;
    }

    /**
     * Clean up events older than the rolling window.
     */
    @Transactional
    public void cleanupOldEvents() {
        LocalDate cutoffDate = LocalDate.now().minusWeeks(PAST_WEEKS);
        log.info("Cleaning up events before {}", cutoffDate);
        
        calendarEventRepository.deleteEventsBeforeDate(cutoffDate);
        
        log.info("Completed cleanup of old events");
    }

    /**
     * Get the current rolling window bounds.
     */
    public WindowBounds getWindowBounds() {
        LocalDate today = LocalDate.now();
        return new WindowBounds(
                today.minusWeeks(PAST_WEEKS),
                today.plusWeeks(FUTURE_WEEKS)
        );
    }

    /**
     * Check if a date is within the materialization window.
     */
    public boolean isDateInWindow(LocalDate date) {
        WindowBounds bounds = getWindowBounds();
        return !date.isBefore(bounds.getStartDate()) && !date.isAfter(bounds.getEndDate());
    }

    /**
     * Helper class for window bounds.
     */
    @lombok.Value
    public static class WindowBounds {
        LocalDate startDate;
        LocalDate endDate;
    }
}
