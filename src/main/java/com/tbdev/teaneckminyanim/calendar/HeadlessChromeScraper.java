package com.tbdev.teaneckminyanim.calendar;

import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Headless Chrome scraper for JavaScript-rendered calendars
 * This can handle dynamic content that Jsoup cannot process
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HeadlessChromeScraper {

    private static final int PAGE_LOAD_TIMEOUT_SECONDS = 30;
    private static final int JAVASCRIPT_WAIT_SECONDS = 5;
    
    private final CalendarNormalizer normalizer;

    /**
     * Scrape calendar entries using headless Chrome
     * Falls back to Jsoup if Chrome fails (e.g., ARM64 architecture issues)
     */
    public List<ScrapedCalendarEntry> scrapeCalendar(String url, LocalDate startDate, LocalDate endDate) {
        log.info("Scraping calendar with headless Chrome from URL: {} (date range: {} to {})", url, startDate, endDate);
        
        WebDriver driver = null;
        try {
            driver = createHeadlessDriver();
            
            // Navigate to the page
            driver.get(url);
            log.debug("Page loaded, URL: {}", driver.getCurrentUrl());
            
            // Wait for JavaScript to render
            Thread.sleep(JAVASCRIPT_WAIT_SECONDS * 1000);
            log.debug("JavaScript rendering complete");
            
            // Get the page HTML after JavaScript execution
            String pageSource = driver.getPageSource();
            log.debug("Page source retrieved, size: {} bytes", pageSource.length());
            
            // Try to find calendar events in the rendered page
            List<ScrapedCalendarEntry> entries = new ArrayList<>();
            
            // Strategy 1: Look for common calendar event elements
            entries.addAll(findFullCalendarEvents(driver, url, startDate, endDate));
            
            // Strategy 2: Look for table-based events
            entries.addAll(findTableEvents(driver, url, startDate, endDate));
            
            // Strategy 3: Look for list-based events
            entries.addAll(findListEvents(driver, url, startDate, endDate));
            
            // Strategy 4: Parse the rendered HTML with common patterns
            entries.addAll(parseRenderedHtml(pageSource, url, startDate, endDate));
            
            log.info("Scraped {} entries using headless Chrome", entries.size());
            return entries;
            
        } catch (Exception e) {
            log.error("Error scraping calendar with headless Chrome: {}", e.getMessage(), e);
            return new ArrayList<>();
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    log.warn("Error closing Chrome driver: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Create a headless Chrome WebDriver instance
     * Updated to use chrome-headless-shell and system-installed Chromium
     */
    private WebDriver createHeadlessDriver() {
        try {
            ChromeOptions options = new ChromeOptions();
            
            // Try to use system-installed Chromium first
            // Common paths for Chromium on Linux
            String[] chromiumPaths = {
                "/usr/bin/chromium",           // Debian/Ubuntu via yum
                "/usr/bin/chromium-browser",   // Standard Ubuntu/Debian
                "/usr/bin/google-chrome",      // Google Chrome
                "/usr/bin/chrome-headless-shell" // Chrome headless shell
            };
            
            boolean chromiumFound = false;
            for (String path : chromiumPaths) {
                java.io.File chromiumBinary = new java.io.File(path);
                if (chromiumBinary.exists() && chromiumBinary.canExecute()) {
                    options.setBinary(path);
                    chromiumFound = true;
                    log.info("Using system Chromium at: {}", path);
                    break;
                }
            }
            
            if (!chromiumFound) {
                log.info("System Chromium not found, falling back to WebDriverManager");
                // Let WebDriverManager handle it
                WebDriverManager.chromedriver().setup();
            }
            
            // Headless options
            options.addArguments("--headless=new");  // New headless mode (Chrome 109+)
            options.addArguments("--no-sandbox");  // Required for Docker/containerized environments
            options.addArguments("--disable-dev-shm-usage");  // Overcome limited resource problems
            options.addArguments("--disable-gpu");  // Disable GPU
            options.addArguments("--disable-software-rasterizer");  // Disable software rasterizer
            options.addArguments("--window-size=1920,1080");  // Set viewport size
            options.addArguments("--user-agent=Teaneck-Minyanim/1.2 (Calendar Sync Bot)");
            options.addArguments("--disable-blink-features=AutomationControlled");  // Avoid detection
            options.addArguments("--ignore-certificate-errors");  // Ignore SSL errors
            options.addArguments("--allow-insecure-localhost");  // Allow insecure localhost
            options.setAcceptInsecureCerts(true);  // Accept self-signed certificates
            
            // For ARM64 compatibility
            options.addArguments("--disable-extensions");
            options.addArguments("--disable-setuid-sandbox");
            
            ChromeDriver driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(PAGE_LOAD_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
            driver.manage().timeouts().implicitlyWait(2, java.util.concurrent.TimeUnit.SECONDS);
            
            return driver;
        } catch (Exception e) {
            log.error("Failed to create headless Chrome driver", e);
            throw new RuntimeException("Failed to create headless Chrome driver: " + e.getMessage(), e);
        }
    }

    /**
     * Find events from FullCalendar.js rendered elements
     */
    private List<ScrapedCalendarEntry> findFullCalendarEvents(WebDriver driver, String sourceUrl, 
                                                               LocalDate startDate, LocalDate endDate) {
        List<ScrapedCalendarEntry> entries = new ArrayList<>();
        
        try {
            // FullCalendar typically renders events with class 'fc-event'
            List<WebElement> events = driver.findElements(By.cssSelector(".fc-event, .fc-event-container, [class*='event']"));
            log.debug("Found {} potential FullCalendar events", events.size());
            
            for (WebElement event : events) {
                try {
                    String text = event.getText();
                    if (text == null || text.trim().isEmpty()) continue;
                    
                    String title = extractTitle(event);
                    LocalDate date = extractDateFromElement(event, text);
                    LocalTime time = extractTimeFromElement(event, text);
                    
                    if (date != null && time != null && title != null &&
                        !date.isBefore(startDate) && !date.isAfter(endDate)) {
                        
                        entries.add(ScrapedCalendarEntry.builder()
                                .date(date)
                                .title(title)
                                .time(time)
                                .rawText(text)
                                .sourceUrl(sourceUrl)
                                .build());
                    }
                } catch (Exception e) {
                    log.debug("Failed to parse event element: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.debug("No FullCalendar events found: {}", e.getMessage());
        }
        
        return entries;
    }

    /**
     * Find events from table elements
     */
    private List<ScrapedCalendarEntry> findTableEvents(WebDriver driver, String sourceUrl,
                                                        LocalDate startDate, LocalDate endDate) {
        List<ScrapedCalendarEntry> entries = new ArrayList<>();
        
        try {
            List<WebElement> rows = driver.findElements(By.cssSelector("table tr"));
            log.debug("Found {} table rows", rows.size());
            
            for (WebElement row : rows) {
                try {
                    List<WebElement> cells = row.findElements(By.tagName("td"));
                    if (cells.size() < 2) continue;
                    
                    String rowText = row.getText();
                    LocalDate date = extractDateFromText(rowText);
                    LocalTime time = extractTimeFromText(rowText);
                    String title = extractTitleFromRow(cells);
                    
                    if (date != null && time != null && title != null &&
                        !date.isBefore(startDate) && !date.isAfter(endDate)) {
                        
                        entries.add(ScrapedCalendarEntry.builder()
                                .date(date)
                                .title(title)
                                .time(time)
                                .rawText(rowText)
                                .sourceUrl(sourceUrl)
                                .build());
                    }
                } catch (Exception e) {
                    log.debug("Failed to parse table row: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.debug("No table events found: {}", e.getMessage());
        }
        
        return entries;
    }

    /**
     * Find events from list elements
     */
    private List<ScrapedCalendarEntry> findListEvents(WebDriver driver, String sourceUrl,
                                                       LocalDate startDate, LocalDate endDate) {
        List<ScrapedCalendarEntry> entries = new ArrayList<>();
        
        try {
            List<WebElement> items = driver.findElements(By.cssSelector("ul li, ol li, .list-item, .event-item"));
            log.debug("Found {} list items", items.size());
            
            for (WebElement item : items) {
                try {
                    String text = item.getText();
                    if (text == null || text.trim().isEmpty()) continue;
                    
                    LocalDate date = extractDateFromText(text);
                    LocalTime time = extractTimeFromText(text);
                    String title = extractTitleFromText(text);
                    
                    if (date != null && time != null && title != null &&
                        !date.isBefore(startDate) && !date.isAfter(endDate)) {
                        
                        entries.add(ScrapedCalendarEntry.builder()
                                .date(date)
                                .title(title)
                                .time(time)
                                .rawText(text)
                                .sourceUrl(sourceUrl)
                                .build());
                    }
                } catch (Exception e) {
                    log.debug("Failed to parse list item: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.debug("No list events found: {}", e.getMessage());
        }
        
        return entries;
    }

    /**
     * Parse the rendered HTML for date/time patterns
     */
    private List<ScrapedCalendarEntry> parseRenderedHtml(String html, String sourceUrl,
                                                          LocalDate startDate, LocalDate endDate) {
        List<ScrapedCalendarEntry> entries = new ArrayList<>();
        
        // This is a fallback - look for date/time patterns in the entire HTML
        // Split by common delimiters and look for patterns
        String[] lines = html.split("\n");
        
        for (String line : lines) {
            try {
                LocalDate date = extractDateFromText(line);
                LocalTime time = extractTimeFromText(line);
                
                if (date != null && time != null &&
                    !date.isBefore(startDate) && !date.isAfter(endDate)) {
                    
                    String title = extractTitleFromText(line);
                    if (title != null && !title.isEmpty()) {
                        entries.add(ScrapedCalendarEntry.builder()
                                .date(date)
                                .title(title)
                                .time(time)
                                .rawText(line.trim())
                                .sourceUrl(sourceUrl)
                                .build());
                    }
                }
            } catch (Exception e) {
                // Ignore parsing errors for individual lines
            }
        }
        
        return entries;
    }

    // Helper methods for extraction
    
    private String extractTitle(WebElement element) {
        try {
            // Try to find title in nested elements
            WebElement titleElem = element.findElement(By.cssSelector(".title, .event-title, .summary, h3, h4, strong"));
            if (titleElem != null) {
                return titleElem.getText().trim();
            }
        } catch (Exception e) {
            // Fall back to element text
        }
        
        String text = element.getText().trim();
        return extractTitleFromText(text);
    }

    private String extractTitleFromRow(List<WebElement> cells) {
        // Typically title is in the last cell or a cell with specific class
        for (WebElement cell : cells) {
            String text = cell.getText().trim();
            if (!text.matches("\\d{1,2}[:/]\\d{1,2}.*") && text.length() > 2) {
                return normalizer.normalizeTitle(text);
            }
        }
        return null;
    }

    private String extractTitleFromText(String text) {
        // Remove date and time patterns to get title
        String cleaned = text.replaceAll("\\d{1,2}[/\\-]\\d{1,2}([/\\-]\\d{2,4})?", "")
                             .replaceAll("\\d{1,2}:\\d{2}\\s*[AaPp][Mm]?", "")
                             .trim();
        
        if (cleaned.isEmpty()) return null;
        return normalizer.normalizeTitle(cleaned);
    }

    private LocalDate extractDateFromElement(WebElement element, String text) {
        // Try data attributes first
        try {
            String dateAttr = element.getAttribute("data-date");
            if (dateAttr != null && !dateAttr.isEmpty()) {
                return LocalDate.parse(dateAttr);
            }
        } catch (Exception e) {
            // Continue to text parsing
        }
        
        return extractDateFromText(text);
    }

    private LocalDate extractDateFromText(String text) {
        Pattern datePattern = Pattern.compile("(\\d{1,2})[/\\-](\\d{1,2})[/\\-](\\d{2,4})");
        Matcher matcher = datePattern.matcher(text);
        
        if (matcher.find()) {
            try {
                int month = Integer.parseInt(matcher.group(1));
                int day = Integer.parseInt(matcher.group(2));
                int year = Integer.parseInt(matcher.group(3));
                
                if (year < 100) {
                    year += 2000;
                }
                
                return LocalDate.of(year, month, day);
            } catch (Exception e) {
                log.debug("Failed to parse date from: {}", text);
            }
        }
        
        return null;
    }

    private LocalTime extractTimeFromElement(WebElement element, String text) {
        // Try data attributes first
        try {
            String timeAttr = element.getAttribute("data-time");
            if (timeAttr != null && !timeAttr.isEmpty()) {
                return normalizer.normalizeTime(timeAttr);
            }
        } catch (Exception e) {
            // Continue to text parsing
        }
        
        return extractTimeFromText(text);
    }

    private LocalTime extractTimeFromText(String text) {
        Pattern timePattern = Pattern.compile("(\\d{1,2})[:.]?(\\d{2})\\s*([AaPp][Mm]?)");
        Matcher matcher = timePattern.matcher(text);
        
        if (matcher.find()) {
            return normalizer.normalizeTime(matcher.group());
        }
        
        return null;
    }
}
