package com.tbdev.teaneckminyanim.calendar;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Debug service to inspect what's actually on a calendar page
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarDebugger {

    private static final int TIMEOUT_MS = 10000;
    private static final String USER_AGENT = "Teaneck-Minyanim/1.2 (Calendar Sync Bot)";

    /**
     * Download and analyze a calendar page to help debug scraping issues
     */
    public String debugCalendarPage(String url) {
        StringBuilder report = new StringBuilder();
        report.append("=== Calendar Debug Report ===\n");
        report.append("URL: ").append(url).append("\n\n");

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();

            report.append("✓ Successfully downloaded HTML\n");
            report.append("HTML size: ").append(doc.html().length()).append(" bytes\n\n");

            // Check for JavaScript calendar indicators
            report.append("--- JavaScript Calendar Detection ---\n");
            Elements scripts = doc.select("script[src]");
            boolean hasFullCalendar = false;
            boolean hasDayPilot = false;
            boolean hasReact = false;
            
            for (Element script : scripts) {
                String src = script.attr("src").toLowerCase();
                if (src.contains("fullcalendar")) {
                    hasFullCalendar = true;
                    report.append("⚠ Found FullCalendar.js: ").append(src).append("\n");
                }
                if (src.contains("daypilot")) {
                    hasDayPilot = true;
                    report.append("⚠ Found DayPilot: ").append(src).append("\n");
                }
                if (src.contains("react")) {
                    hasReact = true;
                    report.append("⚠ Found React: ").append(src).append("\n");
                }
            }
            
            if (hasFullCalendar || hasDayPilot || hasReact) {
                report.append("\n⚠ WARNING: This appears to be a JavaScript-rendered calendar.\n");
                report.append("Static HTML scraping will NOT work. Consider:\n");
                report.append("  - Using the calendar's export/feed URL\n");
                report.append("  - Requesting an API or iCal feed\n");
                report.append("  - Future enhancement: headless browser support\n\n");
            } else {
                report.append("✓ No obvious JavaScript calendar library detected\n\n");
            }

            // Check for common calendar elements
            report.append("--- HTML Structure Analysis ---\n");
            
            Elements tables = doc.select("table");
            report.append("Tables found: ").append(tables.size()).append("\n");
            if (tables.size() > 0) {
                report.append("  Sample table classes: ");
                tables.stream().limit(3).forEach(t -> report.append(t.className()).append(", "));
                report.append("\n");
            }

            Elements lists = doc.select("ul, ol");
            report.append("Lists (ul/ol) found: ").append(lists.size()).append("\n");

            Elements divs = doc.select("div[class*=event], div[class*=calendar]");
            report.append("Event/calendar divs found: ").append(divs.size()).append("\n");

            // Sample text content to look for date/time patterns
            report.append("\n--- Content Sample Analysis ---\n");
            String bodyText = doc.body().text();
            
            // Look for date patterns
            java.util.regex.Pattern datePattern = java.util.regex.Pattern.compile("\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4}");
            java.util.regex.Matcher dateMatcher = datePattern.matcher(bodyText);
            int dateCount = 0;
            report.append("Date patterns (MM/DD/YYYY) in body text:\n");
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
            report.append("\nTime patterns (HH:MM AM/PM) in body text:\n");
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
            report.append("\n--- Sample Visible Text (first 500 chars) ---\n");
            String visibleText = doc.body().text().replaceAll("\\s+", " ").trim();
            report.append(visibleText.substring(0, Math.min(500, visibleText.length())));
            if (visibleText.length() > 500) {
                report.append("...");
            }
            report.append("\n");

            report.append("\n=== End Debug Report ===\n");

        } catch (IOException e) {
            report.append("\n✗ ERROR downloading page: ").append(e.getMessage()).append("\n");
            report.append("Stack trace:\n");
            for (StackTraceElement element : e.getStackTrace()) {
                report.append("  ").append(element.toString()).append("\n");
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
