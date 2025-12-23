package com.tbdev.teaneckminyanim.calendar;

import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.OrganizationCalendarEntry;
import com.tbdev.teaneckminyanim.repo.OrganizationCalendarEntryRepository;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarSyncService {

    private static final int WEEKS_TO_SCRAPE_AHEAD = 8;
    private static final int WEEKS_TO_KEEP_PAST = 2;
    private static final long RATE_LIMIT_MS = 2000; // 2 seconds between requests

    private final OrganizationService organizationService;
    private final OrganizationCalendarEntryRepository calendarEntryRepository;
    private final CsvCalendarScraper csvScraper;  // CSV-based scraper (primary method)
    private final CalendarNormalizer normalizer;

    /**
     * Scheduled job that runs weekly on Sundays at 2 AM
     */
    @Scheduled(cron = "0 0 2 ? * SUN")
    public void scheduledSync() {
        log.info("Starting scheduled calendar sync");
        syncAllOrganizations();
    }

    /**
     * Sync calendars for all organizations with calendar URLs
     */
    public List<CalendarSyncResult> syncAllOrganizations() {
        List<Organization> orgsWithCalendars = organizationService.getAll().stream()
                .filter(org -> org.getCalendar() != null && !org.getCalendar().trim().isEmpty())
                .toList();

        log.info("Found {} organizations with calendar URLs", orgsWithCalendars.size());

        List<CalendarSyncResult> results = new ArrayList<>();

        for (Organization org : orgsWithCalendars) {
            try {
                CalendarSyncResult result = syncOrganization(org.getId());
                results.add(result);

                // Rate limiting - sleep between requests
                if (orgsWithCalendars.indexOf(org) < orgsWithCalendars.size() - 1) {
                    Thread.sleep(RATE_LIMIT_MS);
                }
            } catch (Exception e) {
                log.error("Failed to sync calendar for organization {}: {}", org.getId(), e.getMessage(), e);
                results.add(CalendarSyncResult.builder()
                        .organizationId(org.getId())
                        .organizationName(org.getName())
                        .success(false)
                        .errorMessage(e.getMessage())
                        .syncTime(LocalDateTime.now())
                        .build());
            }
        }

        return results;
    }

    /**
     * Sync calendar for a specific organization
     */
    @Transactional
    public CalendarSyncResult syncOrganization(String organizationId) {
        log.info("Syncing calendar for organization {}", organizationId);

        Optional<Organization> orgOpt = organizationService.findById(organizationId);
        if (orgOpt.isEmpty()) {
            return CalendarSyncResult.builder()
                    .organizationId(organizationId)
                    .success(false)
                    .errorMessage("Organization not found")
                    .syncTime(LocalDateTime.now())
                    .build();
        }

        Organization org = orgOpt.get();
        String calendarUrl = org.getCalendar();

        if (calendarUrl == null || calendarUrl.trim().isEmpty()) {
            return CalendarSyncResult.builder()
                    .organizationId(organizationId)
                    .organizationName(org.getName())
                    .success(false)
                    .errorMessage("No calendar URL configured")
                    .syncTime(LocalDateTime.now())
                    .build();
        }

        try {
            LocalDate startDate = LocalDate.now().minusWeeks(WEEKS_TO_KEEP_PAST);
            LocalDate endDate = LocalDate.now().plusWeeks(WEEKS_TO_SCRAPE_AHEAD);

            log.info("Attempting to scrape {} from {} to {}", calendarUrl, startDate, endDate);
            // Scrape calendar using CSV export
            List<ScrapedCalendarEntry> scrapedEntries = csvScraper.scrapeCalendar(
                    calendarUrl, organizationId, org.getName(), startDate, endDate);
            
            if (scrapedEntries.isEmpty()) {
                log.warn("No entries found for organization {} at URL {}", org.getName(), calendarUrl);
                return CalendarSyncResult.builder()
                        .organizationId(organizationId)
                        .organizationName(org.getName())
                        .success(true)
                        .entriesAdded(0)
                        .entriesUpdated(0)
                        .entriesDisabled(0)
                        .entriesSkipped(0)
                        .errorMessage("No calendar entries found. The calendar format may not be supported or no events exist in the date range.")
                        .syncTime(LocalDateTime.now())
                        .build();
            }

            int added = 0;
            int updated = 0;
            int disabled = 0;
            int skipped = 0;

            for (ScrapedCalendarEntry scraped : scrapedEntries) {
                String fingerprint = normalizer.generateFingerprint(
                        organizationId,
                        scraped.getDate().toString(),
                        scraped.getTitle(),
                        scraped.getTime().toString()
                );

                Optional<OrganizationCalendarEntry> existingOpt = calendarEntryRepository.findByFingerprint(fingerprint);

                if (existingOpt.isPresent()) {
                    // Entry already exists - update timestamp
                    OrganizationCalendarEntry existing = existingOpt.get();
                    existing.setUpdatedAt(LocalDateTime.now());
                    existing.setScrapedAt(LocalDateTime.now());
                    calendarEntryRepository.save(existing);
                    updated++;
                } else {
                    // Check for duplicates with slightly different data
                    List<OrganizationCalendarEntry> potentialDupes = calendarEntryRepository
                            .findByOrganizationIdAndDateAndEnabled(organizationId, scraped.getDate(), true);

                    boolean isDupe = false;
                    for (OrganizationCalendarEntry potentialDupe : potentialDupes) {
                        if (isSimilarEntry(scraped, potentialDupe)) {
                            // This is a duplicate - decide which to keep
                            if (scraped.getRawText() != null && scraped.getRawText().length() > 
                                (potentialDupe.getRawText() != null ? potentialDupe.getRawText().length() : 0)) {
                                // New entry has more detail - disable old one
                                potentialDupe.setEnabled(false);
                                potentialDupe.setDedupeReason("Superseded by more detailed entry");
                                calendarEntryRepository.save(potentialDupe);
                                disabled++;
                            } else {
                                // Keep existing, skip new
                                isDupe = true;
                                skipped++;
                                break;
                            }
                        }
                    }

                    if (!isDupe) {
                        // Create new entry
                        OrganizationCalendarEntry newEntry = OrganizationCalendarEntry.builder()
                                .organizationId(organizationId)
                                .date(scraped.getDate())
                                .title(scraped.getTitle())
                                .type(normalizer.inferMinyanType(scraped.getTitle()))
                                .time(scraped.getTime())
                                .rawText(scraped.getRawText())
                                .sourceUrl(scraped.getSourceUrl())
                                .fingerprint(fingerprint)
                                .enabled(true)
                                .scrapedAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();

                        calendarEntryRepository.save(newEntry);
                        added++;
                    }
                }
            }

            // Clean up old entries
            LocalDate cutoffDate = LocalDate.now().minusWeeks(WEEKS_TO_KEEP_PAST + 1);
            calendarEntryRepository.deleteByOrganizationIdAndDateBefore(organizationId, cutoffDate);

            log.info("Sync complete for {}: {} added, {} updated, {} disabled, {} skipped",
                    org.getName(), added, updated, disabled, skipped);

            return CalendarSyncResult.builder()
                    .organizationId(organizationId)
                    .organizationName(org.getName())
                    .success(true)
                    .entriesAdded(added)
                    .entriesUpdated(updated)
                    .entriesDisabled(disabled)
                    .entriesSkipped(skipped)
                    .syncTime(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to sync calendar for {}: {}", org.getName(), e.getMessage(), e);
            return CalendarSyncResult.builder()
                    .organizationId(organizationId)
                    .organizationName(org.getName())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .syncTime(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Check if two entries are similar enough to be considered duplicates
     */
    private boolean isSimilarEntry(ScrapedCalendarEntry scraped, OrganizationCalendarEntry existing) {
        String scrapedTitle = normalizer.normalizeTitle(scraped.getTitle());
        String existingTitle = normalizer.normalizeTitle(existing.getTitle());

        // Same date, similar title, and similar time (within 5 minutes)
        return scraped.getDate().equals(existing.getDate()) &&
               scrapedTitle.equals(existingTitle) &&
               Math.abs(scraped.getTime().toSecondOfDay() - existing.getTime().toSecondOfDay()) <= 300;
    }
}
