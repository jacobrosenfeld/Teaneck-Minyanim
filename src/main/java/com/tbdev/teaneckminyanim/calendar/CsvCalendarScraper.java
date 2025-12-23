package com.tbdev.teaneckminyanim.calendar;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * CSV-based calendar scraper - downloads and parses structured CSV exports
 * Much more reliable than web scraping HTML/JavaScript
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CsvCalendarScraper {
    
    private final CalendarNormalizer normalizer;
    
    private static final String CSV_EXPORT_DIR = "calendar-exports";
    private static final int MAX_CSV_FILES_TO_KEEP = 3;
    private static final int CONNECTION_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 30000;
    
    /**
     * Scrape calendar by downloading and parsing CSV export
     */
    public List<ScrapedCalendarEntry> scrapeCalendar(String baseUrl, String organizationId, String organizationName,
                                                      LocalDate startDate, LocalDate endDate) {
        log.info("========================================");
        log.info("CSV Scraper: Starting for org {} ({})", organizationName, organizationId);
        log.info("Base URL: {}", baseUrl);
        log.info("Date range: {} to {}", startDate, endDate);
        
        try {
            // Construct CSV export URL
            String csvUrl = buildCsvExportUrl(baseUrl, startDate, endDate);
            log.info("CSV Export URL: {}", csvUrl);
            
            // Download CSV to file
            Path csvFile = downloadCsvToFile(csvUrl, organizationId, organizationName);
            log.info("CSV downloaded to: {}", csvFile.toAbsolutePath());
            
            // Parse CSV file
            List<ScrapedCalendarEntry> entries = parseCsvFile(csvFile, csvUrl);
            log.info("Parsed {} entries from CSV", entries.size());
            
            // Cleanup old CSV files
            cleanupOldCsvFiles(organizationId);
            
            log.info("CSV Scraper: Completed successfully");
            log.info("========================================");
            
            return entries;
            
        } catch (Exception e) {
            log.error("========================================");
            log.error("CSV Scraper: FAILED for org {}", organizationName);
            log.error("Error: {}", e.getMessage(), e);
            log.error("========================================");
            throw new RuntimeException("Failed to scrape calendar via CSV: " + e.getMessage(), e);
        }
    }
    
    /**
     * Build CSV export URL with date range parameters
     * Format: {base}?advanced=Y&date_start=specific+date&date_start_date={start}&has_second_date=Y&date_end=specific+date&date_end_date={end}&view=other&other_view_type=csv&status[]=confirmed
     */
    private String buildCsvExportUrl(String baseUrl, LocalDate startDate, LocalDate endDate) {
        // Remove any existing query parameters from base URL
        String cleanBaseUrl = baseUrl.split("\\?")[0];
        
        // Format dates as YYYY-MM-DD
        String startDateStr = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String endDateStr = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
        
        // Build URL with all required parameters
        return String.format("%s?advanced=Y&date_start=specific+date&date_start_x=0&date_start_date=%s" +
                           "&has_second_date=Y&date_end=specific+date&date_end_x=0&date_end_date=%s" +
                           "&view=other&other_view_type=csv&status%%5B%%5D=confirmed",
                cleanBaseUrl, startDateStr, endDateStr);
    }
    
    /**
     * Download CSV file from URL and save to disk
     */
    private Path downloadCsvToFile(String csvUrl, String organizationId, String organizationName) throws IOException {
        // Create directory for this organization
        Path orgDir = Paths.get(CSV_EXPORT_DIR, organizationId);
        Files.createDirectories(orgDir);
        
        // Generate filename with timestamp
        String timestamp = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String sanitizedOrgName = organizationName.replaceAll("[^a-zA-Z0-9-]", "_");
        String filename = String.format("%s_%s.csv", sanitizedOrgName, timestamp);
        Path csvFile = orgDir.resolve(filename);
        
        log.info("Downloading CSV from: {}", csvUrl);
        
        // Download CSV
        HttpURLConnection connection = null;
        try {
            URL url = new URL(csvUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Teaneck-Minyanim/1.2)");
            
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("HTTP " + responseCode + " when downloading CSV");
            }
            
            // Read and save CSV
            try (InputStream in = connection.getInputStream();
                 OutputStream out = Files.newOutputStream(csvFile)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytes = 0;
                
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                }
                
                log.info("Downloaded {} bytes to {}", totalBytes, csvFile.getFileName());
            }
            
            return csvFile;
            
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * Parse CSV file and extract calendar entries
     */
    private List<ScrapedCalendarEntry> parseCsvFile(Path csvFile, String sourceUrl) throws IOException {
        List<ScrapedCalendarEntry> entries = new ArrayList<>();
        
        log.info("Parsing CSV file: {}", csvFile.getFileName());
        
        try (BufferedReader reader = Files.newBufferedReader(csvFile)) {
            // Read header line
            String headerLine = reader.readLine();
            if (headerLine == null) {
                log.warn("CSV file is empty");
                return entries;
            }
            
            // Parse header to find column indexes
            String[] headers = parseCsvLine(headerLine);
            int dateCol = findColumnIndex(headers, "Date", "Event Date", "Start Date");
            int timeCol = findColumnIndex(headers, "Time", "Start Time", "Event Time");
            int titleCol = findColumnIndex(headers, "Title", "Event", "Summary", "Event Title");
            int descCol = findColumnIndex(headers, "Description", "Details", "Notes");
            
            log.info("CSV Columns - Date: {}, Time: {}, Title: {}, Description: {}", 
                    dateCol, timeCol, titleCol, descCol);
            
            if (dateCol == -1 || timeCol == -1 || titleCol == -1) {
                log.error("Required columns not found in CSV. Headers: {}", String.join(", ", headers));
                throw new IOException("CSV missing required columns (Date, Time, Title)");
            }
            
            // Parse data rows
            String line;
            int rowNum = 1;
            while ((line = reader.readLine()) != null) {
                rowNum++;
                if (line.trim().isEmpty()) continue;
                
                try {
                    String[] columns = parseCsvLine(line);
                    
                    if (columns.length <= Math.max(dateCol, Math.max(timeCol, titleCol))) {
                        log.debug("Row {}: Not enough columns, skipping", rowNum);
                        continue;
                    }
                    
                    String dateStr = columns[dateCol].trim();
                    String timeStr = columns[timeCol].trim();
                    String title = columns[titleCol].trim();
                    String description = descCol >= 0 && descCol < columns.length ? columns[descCol].trim() : "";
                    
                    // Parse date and time
                    LocalDate date = parseDate(dateStr);
                    LocalTime time = normalizer.normalizeTime(timeStr);
                    
                    if (date != null && time != null && !title.isEmpty()) {
                        // Filter for minyan-related entries
                        if (isMinyanEntry(title, description)) {
                            String normalizedTitle = normalizer.normalizeTitle(title);
                            String rawText = String.format("%s %s %s", dateStr, timeStr, title);
                            
                            ScrapedCalendarEntry entry = ScrapedCalendarEntry.builder()
                                    .date(date)
                                    .time(time)
                                    .title(normalizedTitle)
                                    .rawText(rawText)
                                    .sourceUrl(sourceUrl)
                                    .build();
                            
                            entries.add(entry);
                            log.debug("Row {}: ✓ Parsed - {} at {} on {}", rowNum, normalizedTitle, time, date);
                        } else {
                            log.debug("Row {}: Skipped (not a minyan entry) - {}", rowNum, title);
                        }
                    } else {
                        log.debug("Row {}: Failed to parse date/time - date='{}', time='{}'", 
                                rowNum, dateStr, timeStr);
                    }
                    
                } catch (Exception e) {
                    log.warn("Row {}: Parse error - {}", rowNum, e.getMessage());
                }
            }
            
            log.info("CSV parsing complete. Total rows: {}, Entries extracted: {}", rowNum - 1, entries.size());
        }
        
        return entries;
    }
    
    /**
     * Parse CSV line handling quoted fields
     */
    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        
        return fields.toArray(new String[0]);
    }
    
    /**
     * Find column index by trying multiple possible column names
     */
    private int findColumnIndex(String[] headers, String... possibleNames) {
        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].trim().toLowerCase();
            for (String name : possibleNames) {
                if (header.contains(name.toLowerCase())) {
                    return i;
                }
            }
        }
        return -1;
    }
    
    /**
     * Parse date from various formats
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        
        // Try common date formats
        DateTimeFormatter[] formatters = {
                DateTimeFormatter.ofPattern("M/d/yyyy"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("M/d/yy"),
                DateTimeFormatter.ofPattern("MM/dd/yy"),
                DateTimeFormatter.ofPattern("MMM d, yyyy"),
                DateTimeFormatter.ofPattern("MMMM d, yyyy")
        };
        
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (DateTimeParseException e) {
                // Try next format
            }
        }
        
        log.debug("Failed to parse date: {}", dateStr);
        return null;
    }
    
    /**
     * Check if entry is minyan-related based on title and description
     */
    private boolean isMinyanEntry(String title, String description) {
        String combined = (title + " " + description).toLowerCase();
        return combined.contains("shacharis") || combined.contains("shacharit") ||
               combined.contains("mincha") || combined.contains("maariv") ||
               combined.contains("ma'ariv") || combined.contains("arvit") ||
               combined.contains("plag") || combined.contains("minyan");
    }
    
    /**
     * Cleanup old CSV files, keeping only the most recent MAX_CSV_FILES_TO_KEEP
     */
    private void cleanupOldCsvFiles(String organizationId) {
        try {
            Path orgDir = Paths.get(CSV_EXPORT_DIR, organizationId);
            if (!Files.exists(orgDir)) return;
            
            // Get all CSV files sorted by modification time (newest first)
            try (Stream<Path> files = Files.list(orgDir)) {
                List<Path> csvFiles = files
                        .filter(p -> p.toString().endsWith(".csv"))
                        .sorted(Comparator.comparing((Path p) -> {
                            try {
                                return Files.getLastModifiedTime(p);
                            } catch (IOException e) {
                                return java.nio.file.attribute.FileTime.fromMillis(0);
                            }
                        }).reversed())
                        .toList();
                
                // Delete files beyond the keep limit
                if (csvFiles.size() > MAX_CSV_FILES_TO_KEEP) {
                    for (int i = MAX_CSV_FILES_TO_KEEP; i < csvFiles.size(); i++) {
                        Path fileToDelete = csvFiles.get(i);
                        Files.delete(fileToDelete);
                        log.info("Deleted old CSV file: {}", fileToDelete.getFileName());
                    }
                }
                
                log.info("CSV file cleanup complete. Keeping {} most recent files", 
                        Math.min(csvFiles.size(), MAX_CSV_FILES_TO_KEEP));
            }
            
        } catch (Exception e) {
            log.warn("Failed to cleanup old CSV files: {}", e.getMessage());
        }
    }
}
