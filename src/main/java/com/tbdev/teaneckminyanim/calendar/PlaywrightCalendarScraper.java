package com.tbdev.teaneckminyanim.calendar;

import com.microsoft.playwright.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Playwright-based scraper for JavaScript-rendered calendars
 * Playwright has excellent ARM64 support and handles dynamic content reliably
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaywrightCalendarScraper {

    private static final int PAGE_LOAD_TIMEOUT_MS = 30000;
    private static final int JAVASCRIPT_WAIT_MS = 5000;
    
    private final CalendarNormalizer normalizer;

    /**
     * Scrape calendar entries using Playwright headless browser
     * Works on ARM64, x86_64, and all major platforms
     */
    public List<ScrapedCalendarEntry> scrapeCalendar(String url, LocalDate startDate, LocalDate endDate) {
        log.info("Scraping calendar with Playwright from URL: {} (date range: {} to {})", url, startDate, endDate);
        
        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setTimeout(PAGE_LOAD_TIMEOUT_MS);
            
            try (Browser browser = playwright.chromium().launch(launchOptions)) {
                BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                        .setUserAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36"));
                
                Page page = context.newPage();
                page.setDefaultTimeout(PAGE_LOAD_TIMEOUT_MS);
                
                // Navigate to the page
                log.debug("Navigating to URL: {}", url);
                page.navigate(url);
                
                // Wait for JavaScript to render content
                log.debug("Waiting {}ms for JavaScript to render...", JAVASCRIPT_WAIT_MS);
                page.waitForTimeout(JAVASCRIPT_WAIT_MS);
                
                // Get the rendered HTML
                String html = page.content();
                log.debug("Page HTML size: {} characters", html.length());
                
                // Try multiple parsing strategies
                List<ScrapedCalendarEntry> entries = new ArrayList<>();
                
                // Strategy 1: FullCalendar events
                entries.addAll(parseFullCalendarEvents(page, url));
                
                // Strategy 2: HTML tables
                if (entries.isEmpty()) {
                    entries.addAll(parseHtmlTables(page, url));
                }
                
                // Strategy 3: HTML lists
                if (entries.isEmpty()) {
                    entries.addAll(parseHtmlLists(page, url));
                }
                
                // Strategy 4: General text pattern matching
                if (entries.isEmpty()) {
                    entries.addAll(parseGeneralText(html, url));
                }
                
                log.info("Playwright scraper found {} total entries", entries.size());
                
                // Filter by date range
                List<ScrapedCalendarEntry> filtered = entries.stream()
                        .filter(e -> !e.getDate().isBefore(startDate) && !e.getDate().isAfter(endDate))
                        .toList();
                
                log.info("After date filtering: {} entries (range {} to {})", filtered.size(), startDate, endDate);
                return filtered;
                
            } catch (PlaywrightException e) {
                log.error("Playwright browser error: {}", e.getMessage(), e);
                throw new RuntimeException("Playwright scraping failed: " + e.getMessage(), e);
            }
            
        } catch (Exception e) {
            log.error("Error scraping with Playwright: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize Playwright: " + e.getMessage(), e);
        }
    }

    /**
     * Parse FullCalendar.js event elements
     */
    private List<ScrapedCalendarEntry> parseFullCalendarEvents(Page page, String sourceUrl) {
        List<ScrapedCalendarEntry> entries = new ArrayList<>();
        
        try {
            // Look for FullCalendar event elements
            ElementHandle fcEvents = page.querySelector(".fc-event");
            if (fcEvents == null) {
                log.debug("No FullCalendar events found (.fc-event)");
                return entries;
            }
            
            List<ElementHandle> eventElements = page.querySelectorAll(".fc-event");
            log.debug("Found {} FullCalendar events", eventElements.size());
            
            for (ElementHandle event : eventElements) {
                try {
                    String title = event.getAttribute("title");
                    String timeAttr = event.getAttribute("data-time");
                    String dateAttr = event.getAttribute("data-date");
                    
                    if (title != null && timeAttr != null && dateAttr != null) {
                        LocalDate date = LocalDate.parse(dateAttr);
                        LocalTime time = normalizer.normalizeTime(timeAttr);
                        
                        if (time != null) {
                            ScrapedCalendarEntry entry = ScrapedCalendarEntry.builder()
                                    .date(date)
                                    .time(time)
                                    .title(normalizer.normalizeTitle(title))
                                    .rawText(title)
                                    .sourceUrl(sourceUrl)
                                    .build();
                            entries.add(entry);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error parsing FullCalendar event: {}", e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.debug("FullCalendar parsing failed: {}", e.getMessage());
        }
        
        return entries;
    }

    /**
     * Parse HTML tables for date/time patterns
     */
    private List<ScrapedCalendarEntry> parseHtmlTables(Page page, String sourceUrl) {
        List<ScrapedCalendarEntry> entries = new ArrayList<>();
        
        try {
            List<ElementHandle> tables = page.querySelectorAll("table");
            log.debug("Found {} tables", tables.size());
            
            for (ElementHandle table : tables) {
                List<ElementHandle> rows = table.querySelectorAll("tr");
                
                for (ElementHandle row : rows) {
                    String text = row.textContent();
                    if (text == null || text.trim().isEmpty()) continue;
                    
                    // Look for date and time patterns
                    LocalDate date = extractDate(text);
                    LocalTime time = normalizer.normalizeTime(text);
                    
                    if (date != null && time != null) {
                        String title = normalizer.normalizeTitle(text);
                        
                        ScrapedCalendarEntry entry = ScrapedCalendarEntry.builder()
                                .date(date)
                                .time(time)
                                .title(title)
                                .rawText(text.trim())
                                .sourceUrl(sourceUrl)
                                .build();
                        entries.add(entry);
                    }
                }
            }
            
        } catch (Exception e) {
            log.debug("Table parsing failed: {}", e.getMessage());
        }
        
        return entries;
    }

    /**
     * Parse HTML lists (ul/ol) for minyan entries
     */
    private List<ScrapedCalendarEntry> parseHtmlLists(Page page, String sourceUrl) {
        List<ScrapedCalendarEntry> entries = new ArrayList<>();
        
        try {
            List<ElementHandle> items = page.querySelectorAll("ul li, ol li");
            log.debug("Found {} list items", items.size());
            
            for (ElementHandle item : items) {
                String text = item.textContent();
                if (text == null || text.trim().isEmpty()) continue;
                
                // Look for minyan keywords
                if (containsMinyanKeyword(text)) {
                    LocalDate date = extractDate(text);
                    LocalTime time = normalizer.normalizeTime(text);
                    
                    if (date != null && time != null) {
                        String title = normalizer.normalizeTitle(text);
                        
                        ScrapedCalendarEntry entry = ScrapedCalendarEntry.builder()
                                .date(date)
                                .time(time)
                                .title(title)
                                .rawText(text.trim())
                                .sourceUrl(sourceUrl)
                                .build();
                        entries.add(entry);
                    }
                }
            }
            
        } catch (Exception e) {
            log.debug("List parsing failed: {}", e.getMessage());
        }
        
        return entries;
    }

    /**
     * Parse general text for date/time patterns
     * This handles JavaScript-rendered React calendars where content is in the DOM
     */
    private List<ScrapedCalendarEntry> parseGeneralText(String html, String sourceUrl) {
        List<ScrapedCalendarEntry> entries = new ArrayList<>();
        
        try {
            // For React/modern JS calendars, the content is often in nested divs
            // Extract all text content and look for patterns
            String cleanText = html.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ");
            
            log.info("Starting general text parsing. Text length: {} chars", cleanText.length());
            log.debug("Sample of cleaned text (first 1000 chars): {}", 
                     cleanText.length() > 1000 ? cleanText.substring(0, 1000) : cleanText);
            
            // Split into chunks by looking for date patterns
            // Updated pattern to be more flexible and capture more variations
            Pattern dateTimePattern = Pattern.compile(
                "((?:January|February|March|April|May|June|July|August|September|October|November|December)\\s+\\d{1,2})" +
                "[\\s\\S]{0,200}?" +  // Allow up to 200 chars between date and time
                "(\\d{1,2}:\\d{2}\\s*(?:am|pm|AM|PM))" +
                "[\\s\\S]{0,100}?" +  // Allow up to 100 chars between time and keyword
                "(shacharit|shacharis|mincha|ma'?ariv|arvit|plag|minyan)",
                Pattern.CASE_INSENSITIVE
            );
            
            Matcher matcher = dateTimePattern.matcher(cleanText);
            int matchCount = 0;
            
            while (matcher.find()) {
                matchCount++;
                try {
                    String dateStr = matcher.group(1);
                    String timeStr = matcher.group(2);
                    String typeStr = matcher.group(3);
                    
                    log.info("Match #{}: date='{}', time='{}', type='{}'", matchCount, dateStr, timeStr, typeStr);
                    
                    LocalDate date = extractDate(dateStr);
                    LocalTime time = normalizer.normalizeTime(timeStr);
                    
                    if (date != null && time != null) {
                        String title = normalizer.normalizeTitle(typeStr);
                        String rawText = (dateStr + " " + timeStr + " " + typeStr).trim();
                        
                        ScrapedCalendarEntry entry = ScrapedCalendarEntry.builder()
                                .date(date)
                                .time(time)
                                .title(title)
                                .rawText(rawText)
                                .sourceUrl(sourceUrl)
                                .build();
                        entries.add(entry);
                        
                        log.info("✓ Successfully parsed entry: {} at {} on {}", title, time, date);
                    } else {
                        log.warn("✗ Failed to extract date or time from match: dateStr='{}', timeStr='{}' -> date={}, time={}", 
                                dateStr, timeStr, date, time);
                    }
                } catch (Exception e) {
                    log.warn("✗ Failed to parse entry from match: {}", e.getMessage());
                }
            }
            
            log.info("General text parsing completed. Total patterns found: {}, Successfully parsed: {}", 
                    matchCount, entries.size());
            
            if (matchCount == 0) {
                log.warn("No date/time/keyword patterns found in text. Sample text for debugging:");
                log.warn("  First 500 chars: {}", cleanText.length() > 500 ? cleanText.substring(0, 500) : cleanText);
            }
            
        } catch (Exception e) {
            log.debug("General text parsing failed: {}", e.getMessage());
        }
        
        return entries;
    }

    /**
     * Extract date from text
     */
    private LocalDate extractDate(String text) {
        // Try month name format first (e.g., "December 21, 2025" or "December 21")
        Pattern monthNamePattern = Pattern.compile("(January|February|March|April|May|June|July|August|September|October|November|December)\\s+(\\d{1,2}),?\\s*(\\d{4})?", Pattern.CASE_INSENSITIVE);
        Matcher monthNameMatcher = monthNamePattern.matcher(text);
        
        if (monthNameMatcher.find()) {
            try {
                String monthName = monthNameMatcher.group(1);
                int day = Integer.parseInt(monthNameMatcher.group(2));
                String yearStr = monthNameMatcher.group(3);
                int year = yearStr != null ? Integer.parseInt(yearStr) : LocalDate.now().getYear();
                
                int month = switch (monthName.toLowerCase()) {
                    case "january" -> 1;
                    case "february" -> 2;
                    case "march" -> 3;
                    case "april" -> 4;
                    case "may" -> 5;
                    case "june" -> 6;
                    case "july" -> 7;
                    case "august" -> 8;
                    case "september" -> 9;
                    case "october" -> 10;
                    case "november" -> 11;
                    case "december" -> 12;
                    default -> 0;
                };
                
                if (month > 0) {
                    LocalDate date = LocalDate.of(year, month, day);
                    // If the date is more than 6 months in the past, assume it's next year
                    if (date.isBefore(LocalDate.now().minusMonths(6))) {
                        date = date.plusYears(1);
                    }
                    return date;
                }
            } catch (Exception e) {
                log.debug("Failed to parse month name date from: {}", monthNameMatcher.group());
            }
        }
        
        // Try numeric formats: MM/DD/YYYY or DD/MM/YYYY or YYYY-MM-DD
        Pattern datePattern = Pattern.compile("(\\d{1,2})/(\\d{1,2})/(\\d{4})|(\\d{4})-(\\d{2})-(\\d{2})");
        Matcher matcher = datePattern.matcher(text);
        
        if (matcher.find()) {
            try {
                if (matcher.group(3) != null) {
                    // MM/DD/YYYY format
                    int month = Integer.parseInt(matcher.group(1));
                    int day = Integer.parseInt(matcher.group(2));
                    int year = Integer.parseInt(matcher.group(3));
                    return LocalDate.of(year, month, day);
                } else if (matcher.group(6) != null) {
                    // YYYY-MM-DD format
                    int year = Integer.parseInt(matcher.group(4));
                    int month = Integer.parseInt(matcher.group(5));
                    int day = Integer.parseInt(matcher.group(6));
                    return LocalDate.of(year, month, day);
                }
            } catch (Exception e) {
                log.debug("Failed to parse numeric date from: {}", matcher.group());
            }
        }
        
        return null;
    }

    /**
     * Check if text contains minyan-related keywords
     */
    private boolean containsMinyanKeyword(String text) {
        String lower = text.toLowerCase();
        return lower.contains("shacharis") || lower.contains("shacharit") ||
               lower.contains("mincha") || lower.contains("maariv") ||
               lower.contains("ma'ariv") || lower.contains("arvit") ||
               lower.contains("plag") || lower.contains("selichos") ||
               lower.contains("selichot") || lower.contains("megillah") ||
               lower.contains("megila");
    }
}
