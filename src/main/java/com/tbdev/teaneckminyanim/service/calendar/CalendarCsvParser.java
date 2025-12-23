package com.tbdev.teaneckminyanim.service.calendar;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for parsing calendar CSV exports into structured data.
 * Handles multiple datetime formats and missing/reordered columns.
 */
@Slf4j
@Service
public class CalendarCsvParser {

    // Common datetime formats found in calendar exports
    private static final DateTimeFormatter[] DATETIME_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("M/d/yyyy h:mm a"),
            DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ISO_DATE_TIME
    };

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    };

    /**
     * Parsed calendar entry from CSV
     */
    @Data
    @Builder
    public static class ParsedEntry {
        private LocalDate date;
        private LocalTime startTime;
        private LocalDateTime startDatetime;
        private LocalTime endTime;
        private LocalDateTime endDatetime;
        private String title;
        private String type;
        private String name;
        private String location;
        private String description;
        private String hebrewDate;
        private String rawText;
    }

    /**
     * Parse CSV content into a list of calendar entries.
     * Tolerant to missing columns, reordered columns, and various datetime formats.
     *
     * @param csvContent Raw CSV content as string
     * @return List of parsed entries
     * @throws IOException if CSV parsing fails
     */
    public List<ParsedEntry> parseCsv(String csvContent) throws IOException {
        if (csvContent == null || csvContent.trim().isEmpty()) {
            log.warn("Empty CSV content provided");
            return new ArrayList<>();
        }

        List<ParsedEntry> entries = new ArrayList<>();

        try (StringReader reader = new StringReader(csvContent);
             CSVParser csvParser = CSVParser.parse(reader, 
                     CSVFormat.DEFAULT.builder()
                             .setHeader()
                             .setSkipHeaderRecord(true)
                             .setIgnoreHeaderCase(true)
                             .setTrim(true)
                             .setIgnoreEmptyLines(true)
                             .build())) {

            for (CSVRecord record : csvParser) {
                try {
                    ParsedEntry entry = parseRecord(record);
                    if (entry != null && entry.getDate() != null) {
                        entries.add(entry);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse CSV record at line {}: {}", 
                            record.getRecordNumber(), e.getMessage());
                    // Continue processing other records
                }
            }

            log.info("Successfully parsed {} entries from CSV", entries.size());
            return entries;

        } catch (IOException e) {
            log.error("Failed to parse CSV content", e);
            throw e;
        }
    }

    /**
     * Parse a single CSV record into a ParsedEntry.
     */
    private ParsedEntry parseRecord(CSVRecord record) {
        ParsedEntry.ParsedEntryBuilder builder = ParsedEntry.builder();

        // Extract Start datetime
        String startStr = getColumnValue(record, "Start", "start", "Start Date", "start_date");
        if (startStr != null && !startStr.isEmpty()) {
            LocalDateTime startDt = parseDateTime(startStr);
            if (startDt != null) {
                builder.startDatetime(startDt);
                builder.date(startDt.toLocalDate());
                builder.startTime(startDt.toLocalTime());
            } else {
                // Try parsing as date only
                LocalDate date = parseDate(startStr);
                if (date != null) {
                    builder.date(date);
                }
            }
        }

        // Extract End datetime
        String endStr = getColumnValue(record, "End", "end", "End Date", "end_date");
        if (endStr != null && !endStr.isEmpty()) {
            LocalDateTime endDt = parseDateTime(endStr);
            if (endDt != null) {
                builder.endDatetime(endDt);
                builder.endTime(endDt.toLocalTime());
            }
        }

        // Extract Name/Title
        String name = getColumnValue(record, "Name", "name", "Title", "title", "Event", "event");
        builder.name(name);

        // Extract Type
        String type = getColumnValue(record, "Type", "type", "Category", "category");
        builder.type(type);

        // Prefer Name for title, fallback to Type
        String title = (name != null && !name.isEmpty()) ? name : type;
        builder.title(title != null ? title : "Untitled Event");

        // Extract Location
        String location = getColumnValue(record, "Location", "location", "Place", "place");
        builder.location(location);

        // Extract Description
        String description = getColumnValue(record, "Description", "description", "Details", "details", "Notes", "notes");
        builder.description(description);

        // Extract Hebrew Date
        String hebrewDate = getColumnValue(record, "Hebrew Date", "hebrew_date", "HebrewDate", "Jewish Date");
        builder.hebrewDate(hebrewDate);

        // Build raw text from all available fields
        StringBuilder rawText = new StringBuilder();
        if (type != null && !type.isEmpty()) rawText.append("Type: ").append(type).append(" | ");
        if (name != null && !name.isEmpty()) rawText.append("Name: ").append(name).append(" | ");
        if (location != null && !location.isEmpty()) rawText.append("Location: ").append(location).append(" | ");
        if (description != null && !description.isEmpty()) rawText.append("Description: ").append(description);
        builder.rawText(rawText.toString());

        return builder.build();
    }

    /**
     * Get column value by trying multiple possible column names.
     * Case-insensitive matching with trimming.
     */
    private String getColumnValue(CSVRecord record, String... possibleNames) {
        for (String name : possibleNames) {
            try {
                if (record.isMapped(name)) {
                    String value = record.get(name);
                    if (value != null && !value.trim().isEmpty()) {
                        return value.trim();
                    }
                }
            } catch (IllegalArgumentException e) {
                // Column not found, try next name
            }
        }
        return null;
    }

    /**
     * Parse datetime string trying multiple formats.
     */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return null;
        }

        String cleaned = dateTimeStr.trim();

        for (DateTimeFormatter formatter : DATETIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(cleaned, formatter);
            } catch (DateTimeParseException e) {
                // Try next format
            }
        }

        log.debug("Could not parse datetime: {}", dateTimeStr);
        return null;
    }

    /**
     * Parse date string trying multiple formats.
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        String cleaned = dateStr.trim();

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(cleaned, formatter);
            } catch (DateTimeParseException e) {
                // Try next format
            }
        }

        log.debug("Could not parse date: {}", dateStr);
        return null;
    }

    /**
     * Normalize a title for comparison and deduplication.
     * Converts to lowercase, removes extra whitespace and punctuation.
     */
    public String normalizeTitle(String title) {
        if (title == null) return "";
        return title.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Normalize time for comparison (round to nearest minute).
     */
    public LocalTime normalizeTime(LocalTime time) {
        if (time == null) return null;
        return time.withSecond(0).withNano(0);
    }
}
