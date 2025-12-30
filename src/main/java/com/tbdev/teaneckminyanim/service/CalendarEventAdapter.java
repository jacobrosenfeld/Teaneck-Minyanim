package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.enums.Nusach;
import com.tbdev.teaneckminyanim.front.MinyanEvent;
import com.tbdev.teaneckminyanim.model.CalendarEvent;
import com.tbdev.teaneckminyanim.model.Organization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Service to convert CalendarEvent entities to MinyanEvent display objects.
 * This maintains backward compatibility with the frontend while using the new
 * materialized calendar architecture.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarEventAdapter {

    private final OrganizationService organizationService;
    private final ApplicationSettingsService settingsService;

    /**
     * Convert a CalendarEvent to a MinyanEvent for frontend display.
     */
    public MinyanEvent toMinyanEvent(CalendarEvent event) {
        Optional<Organization> orgOpt = organizationService.findById(event.getOrganizationId());
        if (orgOpt.isEmpty()) {
            log.warn("Organization not found for event: {}", event.getOrganizationId());
            return null;
        }
        
        Organization org = orgOpt.get();
        ZoneId zoneId = settingsService.getZoneId();
        
        // Convert LocalDate + LocalTime to Date
        Date startTime = Date.from(
                event.getDate().atTime(event.getStartTime()).atZone(zoneId).toInstant()
        );
        
        // Use organization nusach if event nusach is null
        Nusach nusach = event.getNusach() != null ? event.getNusach() : org.getNusach();
        
        // Determine parent minyan ID based on source
        String parentMinyanId = event.getSourceRef() != null ? event.getSourceRef() : "cal-" + event.getId();
        
        // Get location name (prefer event's location_name over location_id)
        String locationName = event.getLocationName();
        
        // Build notes (may contain Shkiya time for Mincha/Maariv)
        String notes = event.getNotes();
        
        // Create MinyanEvent with dynamic time string if available
        if (event.getDynamicTimeString() != null) {
            return new MinyanEvent(
                    parentMinyanId,
                    event.getMinyanType(),
                    org.getName(),
                    org.getNusach(),
                    event.getOrganizationId(),
                    locationName,
                    startTime,
                    event.getDynamicTimeString(),
                    nusach,
                    notes,
                    org.getOrgColor() != null ? org.getOrgColor() : "#000000",
                    event.getWhatsapp() != null ? event.getWhatsapp() : ""
            );
        } else {
            return new MinyanEvent(
                    parentMinyanId,
                    event.getMinyanType(),
                    org.getName(),
                    org.getNusach(),
                    event.getOrganizationId(),
                    locationName,
                    startTime,
                    nusach,
                    notes,
                    org.getOrgColor() != null ? org.getOrgColor() : "#000000",
                    event.getWhatsapp() != null ? event.getWhatsapp() : ""
            );
        }
    }

    /**
     * Convert a list of CalendarEvents to MinyanEvents.
     */
    public List<MinyanEvent> toMinyanEvents(List<CalendarEvent> events) {
        List<MinyanEvent> minyanEvents = new ArrayList<>();
        
        for (CalendarEvent event : events) {
            try {
                MinyanEvent minyanEvent = toMinyanEvent(event);
                if (minyanEvent != null) {
                    minyanEvents.add(minyanEvent);
                }
            } catch (Exception e) {
                log.warn("Failed to convert CalendarEvent to MinyanEvent: {}", event.getId(), e);
            }
        }
        
        return minyanEvents;
    }
}
