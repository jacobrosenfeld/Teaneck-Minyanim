package com.tbdev.teaneckminyanim.service.provider;

import com.tbdev.teaneckminyanim.enums.Nusach;
import com.tbdev.teaneckminyanim.front.MinyanEvent;
import com.tbdev.teaneckminyanim.minyan.MinyanType;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.OrganizationCalendarEntry;
import com.tbdev.teaneckminyanim.repo.OrganizationCalendarEntryRepository;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Provider that sources schedule data from imported calendar entries.
 * Used when organization has calendar import enabled (useScrapedCalendar = true).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarImportProvider implements OrgScheduleProvider {

    private final OrganizationCalendarEntryRepository entryRepository;
    private final OrganizationService organizationService;
    private final com.tbdev.teaneckminyanim.service.ApplicationSettingsService settingsService;

    @Override
    public List<MinyanEvent> getEventsForDate(String organizationId, LocalDate date) {
        log.debug("CalendarImportProvider: Getting events for {} on {}", organizationId, date);

        List<MinyanEvent> events = new ArrayList<>();

        // Fetch organization details
        Optional<Organization> orgOpt = organizationService.findById(organizationId);
        if (orgOpt.isEmpty()) {
            log.warn("Organization not found: {}", organizationId);
            return events;
        }
        Organization org = orgOpt.get();

        // Fetch calendar entries for the date
        List<OrganizationCalendarEntry> entries = 
                entryRepository.findByOrganizationIdAndDateAndEnabledTrue(organizationId, date);

        log.debug("Found {} enabled calendar entries for {} on {}", entries.size(), org.getName(), date);

        // Convert calendar entries to MinyanEvent objects
        for (OrganizationCalendarEntry entry : entries) {
            try {
                MinyanEvent event = convertToMinyanEvent(entry, org);
                if (event != null) {
                    events.add(event);
                }
            } catch (Exception e) {
                log.warn("Failed to convert calendar entry to MinyanEvent: {}", entry.getTitle(), e);
            }
        }

        return events;
    }

    @Override
    public boolean canHandle(String organizationId) {
        Optional<Organization> orgOpt = organizationService.findById(organizationId);
        if (orgOpt.isEmpty()) {
            return false;
        }
        
        Organization org = orgOpt.get();
        
        // Can handle if:
        // 1. Calendar URL is configured
        // 2. useScrapedCalendar is enabled
        boolean canHandle = org.getCalendar() != null 
                && !org.getCalendar().trim().isEmpty()
                && Boolean.TRUE.equals(org.getUseScrapedCalendar());
        
        log.debug("CalendarImportProvider.canHandle({}) = {}", organizationId, canHandle);
        return canHandle;
    }

    @Override
    public int getPriority() {
        return 100; // Higher priority than rule-based (10)
    }

    @Override
    public String getProviderName() {
        return "CalendarImportProvider";
    }

    /**
     * Convert OrganizationCalendarEntry to MinyanEvent.
     * Attempts to infer MinyanType from the title/type field and classification.
     */
    private MinyanEvent convertToMinyanEvent(OrganizationCalendarEntry entry, Organization org) {
        // Determine MinyanType from entry title/type/classification
        MinyanType minyanType = inferMinyanType(entry);

        // Get ZoneId from settings
        ZoneId zoneId = settingsService.getZoneId();

        // Convert LocalDateTime to Date
        Date startTime = null;
        if (entry.getStartDatetime() != null) {
            startTime = Date.from(entry.getStartDatetime().atZone(zoneId).toInstant());
        } else if (entry.getStartTime() != null && entry.getDate() != null) {
            startTime = Date.from(
                    entry.getDate().atTime(entry.getStartTime()).atZone(zoneId).toInstant());
        }

        if (startTime == null) {
            log.warn("No valid start time for calendar entry: {}", entry.getTitle());
            return null;
        }

        // Create MinyanEvent
        // Use entry ID as parentMinyanId (prefixed to distinguish from regular minyanim)
        String parentMinyanId = "calendar-" + entry.getId();
        
        // Derive nusach from entry title/notes; default to organization nusach if no hints found
        Nusach organizationNusach = org.getNusach() != null ? org.getNusach() : Nusach.UNSPECIFIED;
        Nusach entryNusach = inferNusach(entry, organizationNusach);

        // Use notes field directly (contains Shkiya for Mincha/Maariv and extracted qualifiers like "Teen")
        // Do NOT append description automatically - that's handled by classifier
        String notes = entry.getNotes() != null ? entry.getNotes() : "";

        return new MinyanEvent(
                parentMinyanId,
                minyanType,
                org.getName(),
                organizationNusach,
                org.getId(),
                entry.getLocation() != null ? entry.getLocation() : "",
                startTime,
                entryNusach,
                notes,
                org.getOrgColor() != null ? org.getOrgColor() : "#000000",
                "" // WhatsApp link not available from calendar imports
        );
    }

    /**
     * Infer nusach from the entry content. If no explicit clue is found, fall back to the provided default.
     * Sephardic/Sephardi => Edot Hamizrach; Sefard/Nusach Sefard/NS => Sefard; Ashkenaz stays Ashkenaz.
     */
    private Nusach inferNusach(OrganizationCalendarEntry entry, Nusach defaultNusach) {
        String title = entry.getTitle() != null ? entry.getTitle() : "";
        String notes = entry.getNotes() != null ? entry.getNotes() : "";
        String combined = (title + " " + notes).toLowerCase();

        if (combined.matches(".*\\b(sephardic|sephardi)\\b.*")) {
            return Nusach.EDOT_HAMIZRACH;
        }

        if (combined.matches(".*\\bsefard\\b.*") || combined.matches(".*\\bnusach\\s+sefard\\b.*") || combined.matches(".*\\bNS\\b.*")) {
            return Nusach.SEFARD;
        }

        if (combined.matches(".*\\bashkenaz\\b.*")) {
            return Nusach.ASHKENAZ;
        }

        return defaultNusach;
    }

    /**
     * Infer MinyanType from entry, checking classification first, then title/type.
     * Looks for keywords like "Shacharis", "Mincha", "Maariv", etc.
     */
    private MinyanType inferMinyanType(OrganizationCalendarEntry entry) {
        // Check classification first - most reliable
        // Since classification is now MinyanType, we can use it directly
        if (entry.getClassification() != null && entry.getClassification().isMinyan()) {
            return entry.getClassification();
        }
        
        // Infer from title and type if classification is NON_MINYAN or OTHER or null
        String combined = (entry.getTitle() + " " + (entry.getType() != null ? entry.getType() : "")).toLowerCase();

        // Check for combined Mincha/Maariv first (most specific)
        if (combined.contains("mincha") && (combined.contains("maariv") || combined.contains("ma'ariv") || combined.contains("arvit"))) {
            return MinyanType.MINCHA_MAARIV;
        } else if (combined.contains("shacharis") || combined.contains("shacharit") || combined.contains("morning")) {
            return MinyanType.SHACHARIS;
        } else if (combined.contains("mincha") || combined.contains("minchah") || combined.contains("afternoon")) {
            return MinyanType.MINCHA;
        } else if (combined.contains("maariv") || combined.contains("ma'ariv") || combined.contains("arvit") || combined.contains("evening")) {
            return MinyanType.MAARIV;
        } else if (combined.contains("selichos") || combined.contains("selichot")) {
            return MinyanType.SELICHOS;
        } else if (combined.contains("megila") || combined.contains("megillah")) {
            return MinyanType.MEGILA_READING;
        }

        // Default to SHACHARIS if unable to determine
        log.debug("Unable to infer MinyanType from '{}', defaulting to SHACHARIS", combined);
        return MinyanType.SHACHARIS;
    }
}
