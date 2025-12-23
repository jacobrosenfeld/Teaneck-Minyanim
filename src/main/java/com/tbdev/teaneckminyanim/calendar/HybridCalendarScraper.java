package com.tbdev.teaneckminyanim.calendar;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Hybrid scraper that tries Playwright first, then falls back to Jsoup
 * Playwright has excellent ARM64 support and handles JavaScript calendars
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridCalendarScraper {
    
    private final PlaywrightCalendarScraper playwrightScraper;
    private final CalendarScraper jsoupScraper;
    
    private volatile boolean playwrightFailed = false; // Cache failure state
    private volatile String playwrightFailureReason = null;
    
    /**
     * Scrape calendar with automatic fallback
     * - Try Playwright first (handles JavaScript, SSL, works on ARM64)
     * - Fall back to Jsoup if Playwright fails
     */
    public List<ScrapedCalendarEntry> scrapeCalendar(String url, LocalDate startDate, LocalDate endDate) {
        // If Playwright previously failed, skip straight to Jsoup
        if (playwrightFailed) {
            log.info("Using Jsoup scraper (Playwright previously failed: {})", playwrightFailureReason);
            try {
                return jsoupScraper.scrapeCalendar(url, startDate, endDate);
            } catch (Exception e) {
                log.error("Jsoup scraper failed: {}", e.getMessage());
                return new ArrayList<>();
            }
        }
        
        // Try Playwright first
        log.info("Attempting scrape with Playwright...");
        try {
            List<ScrapedCalendarEntry> entries = playwrightScraper.scrapeCalendar(url, startDate, endDate);
            
            // Success - return results (even if empty)
            if (!entries.isEmpty()) {
                log.info("Playwright scraper succeeded: {} entries", entries.size());
                return entries;
            }
            
            // Playwright worked but found nothing - still try Jsoup as backup
            log.info("Playwright scraper found no entries, trying Jsoup as fallback...");
            try {
                List<ScrapedCalendarEntry> jsoupEntries = jsoupScraper.scrapeCalendar(url, startDate, endDate);
                
                if (!jsoupEntries.isEmpty()) {
                    log.info("Jsoup fallback succeeded: {} entries", jsoupEntries.size());
                    return jsoupEntries;
                }
            } catch (Exception jsoupEx) {
                log.warn("Jsoup fallback also failed: {}", jsoupEx.getMessage());
            }
            
            // Both found nothing - return empty list
            log.warn("Both Playwright and Jsoup found no entries at {}", url);
            return entries;
            
        } catch (Exception e) {
            // Playwright failed - mark it and fall back to Jsoup permanently
            String errorMsg = e.getMessage();
            log.warn("Playwright failed ({}), falling back to Jsoup scraper", errorMsg);
            
            log.warn("Playwright scraper disabled due to error. All future scrapes will use Jsoup.");
            
            // Try Jsoup
            try {
                List<ScrapedCalendarEntry> entries = jsoupScraper.scrapeCalendar(url, startDate, endDate);
                log.info("Jsoup fallback succeeded: {} entries", entries.size());
                return entries;
            } catch (Exception jsoupError) {
                log.error("Jsoup fallback also failed: {}", jsoupError.getMessage());
                throw new RuntimeException("Both Playwright and Jsoup scrapers failed", jsoupError);
            }
        }
    }
    
    /**
     * Check if Playwright scraper is available
     */
    public boolean isPlaywrightAvailable() {
        return !playwrightFailed;
    }
    
    /**
     * Get the reason Playwright failed (if applicable)
     */
    public String getPlaywrightFailureReason() {
        return playwrightFailureReason;
    }
    
    /**
     * Reset Playwright failure state (for testing/retry)
     */
    public void resetPlaywrightState() {
        playwrightFailed = false;
        playwrightFailureReason = null;
        log.info("Playwright scraper state reset - will retry on next scrape");
    }
}
