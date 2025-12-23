package com.tbdev.teaneckminyanim.calendar;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Hybrid scraper that tries headless Chrome first, then falls back to Jsoup
 * This handles architecture issues (e.g., ARM64) gracefully
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridCalendarScraper {
    
    private final HeadlessChromeScraper chromeScraper;
    private final CalendarScraper jsoupScraper;
    
    private volatile boolean chromeFailed = false; // Cache failure state
    private volatile String chromeFailureReason = null;
    
    /**
     * Scrape calendar with automatic fallback
     * - Try headless Chrome first (handles JavaScript, SSL)
     * - Fall back to Jsoup if Chrome fails (e.g., ARM64 architecture)
     */
    public List<ScrapedCalendarEntry> scrapeCalendar(String url, LocalDate startDate, LocalDate endDate) {
        // If Chrome previously failed, skip straight to Jsoup
        if (chromeFailed) {
            log.info("Using Jsoup scraper (Chrome previously failed: {})", chromeFailureReason);
            try {
                return jsoupScraper.scrapeCalendar(url, startDate, endDate);
            } catch (Exception e) {
                log.error("Jsoup scraper failed: {}", e.getMessage());
                return new ArrayList<>();
            }
        }
        
        // Try Chrome first
        log.info("Attempting scrape with headless Chrome...");
        try {
            List<ScrapedCalendarEntry> entries = chromeScraper.scrapeCalendar(url, startDate, endDate);
            
            // Success - return results (even if empty)
            if (!entries.isEmpty()) {
                log.info("Chrome scraper succeeded: {} entries", entries.size());
                return entries;
            }
            
            // Chrome worked but found nothing - still try Jsoup as backup
            log.info("Chrome scraper found no entries, trying Jsoup as fallback...");
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
            log.warn("Both Chrome and Jsoup found no entries at {}", url);
            return entries;
            
        } catch (Exception e) {
            // Chrome failed - mark it and fall back to Jsoup permanently
            String errorMsg = e.getMessage();
            log.warn("Headless Chrome failed ({}), falling back to Jsoup scraper", errorMsg);
            
            // Check if this is an architecture issue (ARM64)
            if (errorMsg != null && (errorMsg.contains("cannot execute binary file") 
                    || errorMsg.contains("ChromeDriver") 
                    || errorMsg.contains("WebDriverManager"))) {
                chromeFailed = true;
                chromeFailureReason = "Architecture incompatibility (likely ARM64)";
                log.error("Chrome scraper disabled: {}. All future scrapes will use Jsoup.", chromeFailureReason);
            }
            
            // Try Jsoup
            try {
                List<ScrapedCalendarEntry> entries = jsoupScraper.scrapeCalendar(url, startDate, endDate);
                log.info("Jsoup fallback succeeded: {} entries", entries.size());
                return entries;
            } catch (Exception jsoupError) {
                log.error("Jsoup fallback also failed: {}", jsoupError.getMessage());
                throw new RuntimeException("Both Chrome and Jsoup scrapers failed", jsoupError);
            }
        }
    }
    
    /**
     * Check if Chrome scraper is available
     */
    public boolean isChromeAvailable() {
        return !chromeFailed;
    }
    
    /**
     * Get the reason Chrome failed (if applicable)
     */
    public String getChromeFailureReason() {
        return chromeFailureReason;
    }
    
    /**
     * Reset Chrome failure state (for testing/retry)
     */
    public void resetChromeState() {
        chromeFailed = false;
        chromeFailureReason = null;
        log.info("Chrome scraper state reset - will retry on next scrape");
    }
}
