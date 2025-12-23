package com.tbdev.teaneckminyanim.calendar;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarScraper {

    private static final int TIMEOUT_MS = 10000;
    private static final String USER_AGENT = "Teaneck-Minyanim/1.2 (Calendar Sync Bot)";

    private final CalendarNormalizer normalizer;

    /**
     * Scrape calendar entries from a URL
     */
    public List<ScrapedCalendarEntry> scrapeCalendar(String url, LocalDate startDate, LocalDate endDate) throws IOException {
        log.info("Scraping calendar from URL: {} (date range: {} to {})", url, startDate, endDate);

        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .ignoreHttpErrors(true)  // Ignore HTTP errors
                .ignoreContentType(true)  // Allow any content type
                .get();

        log.debug("Downloaded HTML, size: {} bytes", doc.html().length());

        List<ScrapedCalendarEntry> entries = new ArrayList<>();

        // Try different common calendar structures
        List<ScrapedCalendarEntry> googleEntries = parseGoogleCalendarEmbeds(doc, url, startDate, endDate);
        log.debug("Found {} Google Calendar entries", googleEntries.size());
        entries.addAll(googleEntries);
        
        List<ScrapedCalendarEntry> tableEntries = parseTableBasedCalendar(doc, url, startDate, endDate);
        log.debug("Found {} table-based entries", tableEntries.size());
        entries.addAll(tableEntries);
        
        List<ScrapedCalendarEntry> listEntries = parseListBasedCalendar(doc, url, startDate, endDate);
        log.debug("Found {} list-based entries", listEntries.size());
        entries.addAll(listEntries);

        log.info("Scraped {} total entries from {}", entries.size(), url);
        return entries;
    }

    /**
     * Parse Google Calendar embeds (many shuls use this)
     */
    private List<ScrapedCalendarEntry> parseGoogleCalendarEmbeds(Document doc, String sourceUrl, LocalDate startDate, LocalDate endDate) {
        List<ScrapedCalendarEntry> entries = new ArrayList<>();

        // Look for calendar event elements
        Elements events = doc.select(".event, .calendar-event, [class*=event], [class*=calendar]");

        for (Element event : events) {
            try {
                String text = event.text();
                String title = extractTitle(event);
                LocalDate date = extractDate(event, text);
                LocalTime time = extractTime(event, text);

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
                log.debug("Failed to parse event element: {}", event.text(), e);
            }
        }

        return entries;
    }

    /**
     * Parse table-based calendars
     */
    private List<ScrapedCalendarEntry> parseTableBasedCalendar(Document doc, String sourceUrl, LocalDate startDate, LocalDate endDate) {
        List<ScrapedCalendarEntry> entries = new ArrayList<>();

        Elements tables = doc.select("table");
        for (Element table : tables) {
            Elements rows = table.select("tr");

            for (Element row : rows) {
                try {
                    Elements cells = row.select("td, th");
                    if (cells.size() < 2) continue;

                    String rowText = row.text();
                    LocalDate date = extractDate(row, rowText);
                    LocalTime time = extractTime(row, rowText);
                    String title = extractTitle(row);

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
                    log.debug("Failed to parse table row: {}", row.text(), e);
                }
            }
        }

        return entries;
    }

    /**
     * Parse list-based calendars
     */
    private List<ScrapedCalendarEntry> parseListBasedCalendar(Document doc, String sourceUrl, LocalDate startDate, LocalDate endDate) {
        List<ScrapedCalendarEntry> entries = new ArrayList<>();

        Elements lists = doc.select("ul, ol, div.list");
        for (Element list : lists) {
            Elements items = list.select("li, div.item, div.entry");

            for (Element item : items) {
                try {
                    String text = item.text();
                    LocalDate date = extractDate(item, text);
                    LocalTime time = extractTime(item, text);
                    String title = extractTitle(item);

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
                    log.debug("Failed to parse list item: {}", item.text(), e);
                }
            }
        }

        return entries;
    }

    /**
     * Extract title from element
     */
    private String extractTitle(Element element) {
        // Try specific selectors first
        Element titleElem = element.selectFirst(".title, .event-title, .summary, h3, h4, strong");
        if (titleElem != null) {
            return titleElem.text().trim();
        }

        // Fall back to element text
        String text = element.text().trim();
        if (text.isEmpty()) {
            return null;
        }

        // Remove date and time patterns to get title
        text = text.replaceAll("\\d{1,2}[/\\-]\\d{1,2}([/\\-]\\d{2,4})?", "")
                   .replaceAll("\\d{1,2}:\\d{2}\\s*[AaPp][Mm]?", "")
                   .trim();

        return text.isEmpty() ? null : text;
    }

    /**
     * Extract date from element
     */
    private LocalDate extractDate(Element element, String text) {
        // Try data attributes
        String dateAttr = element.attr("data-date");
        if (!dateAttr.isEmpty()) {
            try {
                return LocalDate.parse(dateAttr);
            } catch (DateTimeParseException e) {
                // Continue to other methods
            }
        }

        // Try parsing from text
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

    /**
     * Extract time from element
     */
    private LocalTime extractTime(Element element, String text) {
        // Try data attributes
        String timeAttr = element.attr("data-time");
        if (!timeAttr.isEmpty()) {
            LocalTime time = normalizer.normalizeTime(timeAttr);
            if (time != null) {
                return time;
            }
        }

        // Try parsing from text
        Pattern timePattern = Pattern.compile("(\\d{1,2})[:.]?(\\d{2})\\s*([AaPp][Mm]?)");
        Matcher matcher = timePattern.matcher(text);

        if (matcher.find()) {
            return normalizer.normalizeTime(matcher.group());
        }

        return null;
    }
}
