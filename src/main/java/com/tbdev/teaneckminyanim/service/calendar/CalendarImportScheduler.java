package com.tbdev.teaneckminyanim.service.calendar;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

/**
 * Scheduled service for automatic weekly calendar imports.
 * Runs every Sunday at 2 AM to refresh calendar data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarImportScheduler {

    private final CalendarImportService importService;

    /**
     * Scheduled job that runs every Sunday at 2:00 AM.
     * Imports calendar data for all organizations with calendar import enabled.
     */
    @Scheduled(cron = "0 0 2 * * SUN")
    public void scheduledWeeklyImport() {
        log.info("Starting scheduled weekly calendar import");
        
        try {
            Map<String, CalendarImportService.ImportResult> results = 
                    importService.importAllEnabledOrganizations();
            
            // Log summary
            int successCount = 0;
            int failureCount = 0;
            int totalNewEntries = 0;
            int totalUpdatedEntries = 0;
            
            for (CalendarImportService.ImportResult result : results.values()) {
                if (result.success) {
                    successCount++;
                    totalNewEntries += result.newEntries;
                    totalUpdatedEntries += result.updatedEntries;
                } else {
                    failureCount++;
                    log.warn("Failed import for organization {}: {}", 
                            result.organizationId, result.errorMessage);
                }
            }
            
            log.info("Scheduled import completed: {} successful, {} failed, {} new entries, {} updated entries",
                    successCount, failureCount, totalNewEntries, totalUpdatedEntries);
            
            // Cleanup old entries (older than 30 days in the past)
            cleanupOldEntries();
            
        } catch (Exception e) {
            log.error("Scheduled calendar import failed", e);
        }
    }

    /**
     * Clean up old calendar entries to prevent database bloat.
     * Removes entries older than 30 days in the past.
     */
    private void cleanupOldEntries() {
        // This would need to be enhanced to iterate through all organizations
        // For now, it's a placeholder for the cleanup logic
        log.info("Cleanup of old entries would run here");
        // TODO: Implement when we have a way to iterate all orgs with calendar import enabled
    }
}
