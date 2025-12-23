package com.tbdev.teaneckminyanim.calendar;

import com.microsoft.playwright.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Debug service to inspect what's actually on a calendar page
 * Uses Playwright for reliable cross-platform support (ARM64, x86_64)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarDebugger {
    
    private final HybridCalendarScraper hybridScraper;

    /**
     * Download and analyze a calendar page to help debug scraping issues
     */
    public String debugCalendarPage(String url) {
        StringBuilder report = new StringBuilder();
        report.append("=== Calendar Debug Report (Playwright) ===\n");
        report.append("URL: ").append(url).append("\n\n");

        // Check if Playwright is available
        if (!hybridScraper.isPlaywrightAvailable()) {
            report.append("⚠ Playwright scraper is disabled: ").append(hybridScraper.getPlaywrightFailureReason()).append("\n");
            report.append("Using Jsoup scraper instead\n\n");
            return debugWithJsoup(url, report);
        }

        try (Playwright playwright = Playwright.create()) {
            report.append("✓ Playwright initialized\n");
            
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setTimeout(30000);
            
            try (Browser browser = playwright.chromium().launch(launchOptions)) {
                report.append("✓ Chromium browser launched\n");
                
                BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                        .setUserAgent("Teaneck-Minyanim/1.2 (Calendar Sync Bot)")
                        .setIgnoreHTTPSErrors(true));
                
                Page page = context.newPage();
                page.setDefaultTimeout(30000);
                
                // Navigate to the page
                report.append("✓ Navigating to URL...\n");
                page.navigate(url);
                report.append("✓ Successfully loaded page\n");
                report.append("Final URL: ").append(page.url()).append("\n\n");
                
                // Wait for JavaScript
                page.waitForTimeout(5000);
                report.append("✓ Waited for JavaScript rendering (5 seconds)\n\n");
                
                // Get page source after JavaScript execution
                String pageSource = page.content();
                report.append("HTML size after JavaScript: ").append(pageSource.length()).append(" bytes\n\n");

                // Check for JavaScript calendar indicators
                report.append("--- JavaScript Calendar Detection ---\n");
                if (pageSource.toLowerCase().contains("fullcalendar")) {
                    report.append("✓ Found FullCalendar.js references\n");
            }
            if (pageSource.toLowerCase().contains("daypilot")) {
                report.append("✓ Found DayPilot references\n");
            } else {
                report.append("⚠ WARNING: This appears to be a JavaScript-rendered calendar\n");
            }
            if (pageSource.toLowerCase().contains("react")) {
                report.append("✓ Found React references\n");
            }
            report.append("\n");

            // Sample text content
            report.append("--- Content Sample Analysis ---\n");
            String bodyText = page.textContent("body");
            
            // Look for date patterns
            report.append("\n--- Date Pattern Detection ---\n");
            Pattern datePattern = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{2,4})\\b|" +
                    "\\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]* \\d{1,2}\\b");
            Matcher dateMatcher = datePattern.matcher(bodyText);
            int dateCount = 0;
            report.append("Date patterns in rendered text:\n");
            while (dateMatcher.find() && dateCount < 5) {
                report.append("  - ").append(dateMatcher.group()).append("\n");
                dateCount++;
            }
            if (dateCount == 0) {
                report.append("  ✗ No date patterns found!\n");
                } else {
                    report.append("  Total date patterns found: ").append(dateCount).append("+\n");
                }

                // Look for time patterns
                Pattern timePattern = Pattern.compile("\\d{1,2}:\\d{2}\\s*[AaPp][Mm]?");
                Matcher timeMatcher = timePattern.matcher(bodyText);
                int timeCount = 0;
                report.append("\nTime patterns (HH:MM AM/PM) in rendered text:\n");
                while (timeMatcher.find() && timeCount < 5) {
                    report.append("  - ").append(timeMatcher.group()).append("\n");
                    timeCount++;
                }
                if (timeCount == 0) {
                    report.append("  ✗ No time patterns found!\n");
                } else {
                    report.append("  Total time patterns found: ").append(timeCount).append("+\n");
                }

                // Look for minyan-related keywords
                report.append("\n--- Minyan Keywords Detection ---\n");
                String lowerBody = bodyText.toLowerCase();
                String[] keywords = {"shacharis", "shacharit", "mincha", "maariv", "arvit", "minyan"};
                for (String keyword : keywords) {
                    int count = countOccurrences(lowerBody, keyword);
                    if (count > 0) {
                        report.append("  '").append(keyword).append("': ").append(count).append(" occurrences\n");
                    }
                }

                // Sample of visible text
                report.append("\n--- Sample Rendered Text (first 500 chars) ---\n");
                String visibleText = bodyText.replaceAll("\\s+", " ").trim();
                report.append(visibleText.substring(0, Math.min(500, visibleText.length())));
                if (visibleText.length() > 500) {
                    report.append("...");
                }
                report.append("\n");

                report.append("\n=== End Debug Report ===\n");
                report.append("\n✓ Playwright successfully scraped the page (JavaScript-rendered content visible)!\n");
                
            } catch (PlaywrightException e) {
                report.append("\n✗ ERROR with Playwright: ").append(e.getMessage()).append("\n");
                report.append("\nFalling back to Jsoup scraper...\n\n");
                return debugWithJsoup(url, report);
            }

        } catch (Exception e) {
            report.append("\n✗ ERROR initializing Playwright: ").append(e.getMessage()).append("\n");
            
            report.append("\nStack trace:\n");
            for (StackTraceElement element : e.getStackTrace()) {
                report.append("  ").append(element.toString()).append("\n");
            }
            
            report.append("\nFalling back to Jsoup scraper...\n\n");
            return debugWithJsoup(url, report);
        }

        return report.toString();
    }
    
    /**
     * Fallback debug using Jsoup (when Playwright fails)
     */
    private String debugWithJsoup(String url, StringBuilder existingReport) {
        StringBuilder report = existingReport != null ? existingReport : new StringBuilder();
        
        try {
            report.append("=== Jsoup Debug (Static HTML Only) ===\n");
            
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.connect(url)
                    .userAgent("Teaneck-Minyanim/1.2 (Calendar Sync Bot)")
                    .timeout(10000)
                    .ignoreHttpErrors(true)
                    .get();
            
            report.append("✓ Page downloaded successfully (static HTML)\n");
            report.append("⚠ Note: JavaScript content will NOT be visible to Jsoup\n\n");
            
            String bodyText = doc.body().text();
            report.append("HTML body size: ").append(bodyText.length()).append(" characters\n\n");
            
            // Check for date patterns
            report.append("--- Date Pattern Detection ---\n");
            Pattern datePattern = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{2,4})\\b|" +
                    "\\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]* \\d{1,2}\\b");
            Matcher dateMatcher = datePattern.matcher(bodyText);
            int dateCount = 0;
            while (dateMatcher.find() && dateCount < 5) {
                report.append("  - ").append(dateMatcher.group()).append("\n");
                dateCount++;
            }
            if (dateCount == 0) {
                report.append("  ✗ No date patterns found in static HTML!\n");
                report.append("  This likely means the calendar is JavaScript-rendered.\n");
            }
            
            // Check for time patterns
            report.append("\n--- Time Pattern Detection ---\n");
            Pattern timePattern = Pattern.compile("\\b\\d{1,2}:\\d{2}\\s*(?:AM|PM|am|pm)?\\b");
            java.util.regex.Matcher timeMatcher = timePattern.matcher(bodyText);
            int timeCount = 0;
            while (timeMatcher.find() && timeCount < 5) {
                report.append("  - ").append(timeMatcher.group()).append("\n");
                timeCount++;
            }
            if (timeCount == 0) {
                report.append("  ✗ No time patterns found in static HTML!\n");
            }
            
            // Sample text
            report.append("\n--- Sample Static HTML Text (first 500 chars) ---\n");
            String sampleText = bodyText.replaceAll("\\s+", " ").trim();
            report.append(sampleText.substring(0, Math.min(500, sampleText.length())));
            if (sampleText.length() > 500) {
                report.append("...");
            }
            report.append("\n");
            
            report.append("\n=== Recommendation ===\n");
            if (dateCount == 0 && timeCount == 0) {
                report.append("❌ This calendar appears to be JavaScript-rendered.\n");
                report.append("Jsoup cannot scrape JavaScript content.\n");
                report.append("\nOptions:\n");
                report.append("1. Install Chrome/Chromium for x86_64 architecture\n");
                report.append("2. Use Docker with x86_64 emulation\n");
                report.append("3. Request an export/feed URL from the shul\n");
            } else {
                report.append("✓ Some date/time data found - scraping may work!\n");
            }
            
        } catch (Exception e) {
            report.append("\n✗ Jsoup also failed: ").append(e.getMessage()).append("\n");
        }
        
        return report.toString();
    }

    private int countOccurrences(String text, String word) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(word, index)) != -1) {
            count++;
            index += word.length();
        }
        return count;
    }
}

