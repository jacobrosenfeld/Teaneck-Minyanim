package com.tbdev.teaneckminyanim.calendar;

import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Debug service to inspect what's actually on a calendar page using headless Chrome
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarDebugger {

    /**
     * Download and analyze a calendar page to help debug scraping issues
     */
    public String debugCalendarPage(String url) {
        StringBuilder report = new StringBuilder();
        report.append("=== Calendar Debug Report (Headless Chrome) ===\n");
        report.append("URL: ").append(url).append("\n\n");

        WebDriver driver = null;
        try {
            // Setup ChromeDriver automatically
            WebDriverManager.chromedriver().setup();
            
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless=new");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--user-agent=Teaneck-Minyanim/1.2 (Calendar Sync Bot)");
            options.addArguments("--ignore-certificate-errors");
            options.setAcceptInsecureCerts(true);
            
            driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(30, java.util.concurrent.TimeUnit.SECONDS);
            driver.manage().timeouts().implicitlyWait(2, java.util.concurrent.TimeUnit.SECONDS);
            
            report.append("✓ Chrome driver initialized\n");
            
            // Navigate to the page
            driver.get(url);
            report.append("✓ Successfully loaded page\n");
            report.append("Final URL: ").append(driver.getCurrentUrl()).append("\n\n");
            
            // Wait for JavaScript
            Thread.sleep(5000);
            report.append("✓ Waited for JavaScript rendering (5 seconds)\n\n");
            
            // Get page source after JavaScript execution
            String pageSource = driver.getPageSource();
            report.append("HTML size after JavaScript: ").append(pageSource.length()).append(" bytes\n\n");

            // Check for JavaScript calendar indicators
            report.append("--- JavaScript Calendar Detection ---\n");
            if (pageSource.toLowerCase().contains("fullcalendar")) {
                report.append("✓ Found FullCalendar.js references\n");
            }
            if (pageSource.toLowerCase().contains("daypilot")) {
                report.append("✓ Found DayPilot references\n");
            }
            if (pageSource.toLowerCase().contains("react")) {
                report.append("✓ Found React references\n");
            }
            report.append("\n");

            // Sample text content
            report.append("--- Content Sample Analysis ---\n");
            String bodyText = driver.findElement(org.openqa.selenium.By.tagName("body")).getText();
            
            // Look for date patterns
            java.util.regex.Pattern datePattern = java.util.regex.Pattern.compile("\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4}");
            java.util.regex.Matcher dateMatcher = datePattern.matcher(bodyText);
            int dateCount = 0;
            report.append("Date patterns (MM/DD/YYYY) in rendered text:\n");
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
            java.util.regex.Pattern timePattern = java.util.regex.Pattern.compile("\\d{1,2}:\\d{2}\\s*[AaPp][Mm]?");
            java.util.regex.Matcher timeMatcher = timePattern.matcher(bodyText);
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
            report.append("\n✓ Using headless Chrome allows scraping JavaScript-rendered calendars!\n");

        } catch (Exception e) {
            report.append("\n✗ ERROR: ").append(e.getMessage()).append("\n");
            report.append("\nStack trace:\n");
            for (StackTraceElement element : e.getStackTrace()) {
                report.append("  ").append(element.toString()).append("\n");
            }
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    report.append("\n⚠ Warning: Error closing driver: ").append(e.getMessage()).append("\n");
                }
            }
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

