package com.tbdev.teaneckminyanim.service.calendar;

import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.OrganizationCalendarEntry;
import com.tbdev.teaneckminyanim.repo.OrganizationCalendarEntryRepository;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for importing calendar entries from CSV exports.
 * Handles HTTP fetching, parsing, deduplication, and persistence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarImportService {

    private final CalendarUrlBuilder urlBuilder;
    private final CalendarCsvParser csvParser;
    private final OrganizationCalendarEntryRepository entryRepository;
    private final OrganizationService organizationService;

    private static final int HTTP_TIMEOUT_SECONDS = 30;
    private static final String USER_AGENT = "TeaneckMinyanim/1.2.1 (Calendar Import Bot)";

    /**
     * Result of an import operation
     */
    public static class ImportResult {
        public boolean success;
        public String organizationId;
        public int totalParsed;
        public int newEntries;
        public int updatedEntries;
        public int duplicatesSkipped;
        public String errorMessage;
        public LocalDateTime importedAt;

        public ImportResult() {
            this.importedAt = LocalDateTime.now();
        }
    }

    /**
     * Import calendar entries for a single organization.
     * Uses REQUIRES_NEW propagation to ensure this runs in its own transaction,
     * preventing transaction rollback issues when exceptions are caught and handled.
     *
     * @param organizationId Organization ID
     * @return Import result with statistics
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportResult importCalendarForOrganization(String organizationId) {
        ImportResult result = new ImportResult();
        result.organizationId = organizationId;

        try {
            // Fetch organization
            Optional<Organization> orgOpt = organizationService.findById(organizationId);
            if (orgOpt.isEmpty()) {
                result.success = false;
                result.errorMessage = "Organization not found: " + organizationId;
                log.error(result.errorMessage);
                return result;
            }
            
            Organization org = orgOpt.get();

            // Check if calendar URL is configured
            if (org.getCalendar() == null || org.getCalendar().trim().isEmpty()) {
                result.success = false;
                result.errorMessage = "No calendar URL configured for organization: " + org.getName();
                log.warn(result.errorMessage);
                return result;
            }

            // Validate calendar URL
            if (!urlBuilder.isValidCalendarUrl(org.getCalendar())) {
                result.success = false;
                result.errorMessage = "Invalid calendar URL format: " + org.getCalendar();
                log.error(result.errorMessage);
                return result;
            }

            log.info("Starting calendar import for organization: {} ({})", org.getName(), organizationId);

            // Build CSV export URL
            String csvUrl = urlBuilder.buildCsvExportUrl(org.getCalendar());
            log.debug("CSV export URL: {}", csvUrl);

            // Fetch CSV content
            String csvContent = fetchCsvContent(csvUrl);
            if (csvContent == null || csvContent.trim().isEmpty()) {
                result.success = false;
                result.errorMessage = "Empty CSV content received from URL";
                log.error(result.errorMessage);
                return result;
            }

            // Parse CSV
            List<CalendarCsvParser.ParsedEntry> parsedEntries = csvParser.parseCsv(csvContent);
            result.totalParsed = parsedEntries.size();
            log.info("Parsed {} entries from CSV", parsedEntries.size());

            // Process and save entries
            processEntries(organizationId, parsedEntries, csvUrl, result);

            result.success = true;
            log.info("Import completed for {}: {} new, {} updated, {} duplicates skipped",
                    org.getName(), result.newEntries, result.updatedEntries, result.duplicatesSkipped);

        } catch (javax.net.ssl.SSLHandshakeException e) {
            result.success = false;
            result.errorMessage = "SSL certificate validation failed. This may be due to expired certificates on the server or missing CA certificates in the Java trust store. Details: " + e.getMessage();
            log.error("SSL certificate error while importing calendar for organization: {}. The server's Java installation may need updated CA certificates. Try: 1) Update Java cacerts, 2) Import the site's certificate, or 3) Use HTTP if available.", organizationId, e);
        } catch (Exception e) {
            result.success = false;
            result.errorMessage = "Import failed: " + e.getMessage();
            log.error("Failed to import calendar for organization: " + organizationId, e);
        }

        return result;
    }

    /**
     * Import calendars for all organizations with calendar URLs and useScrapedCalendar enabled.
     *
     * @return Map of organization ID to import result
     */
    public Map<String, ImportResult> importAllEnabledOrganizations() {
        Map<String, ImportResult> results = new HashMap<>();

        List<Organization> orgs = organizationService.getAll();
        log.info("Checking {} organizations for calendar import", orgs.size());

        for (Organization org : orgs) {
            // Check if calendar import is enabled for this organization
            if (org.getCalendar() != null && !org.getCalendar().trim().isEmpty() 
                    && Boolean.TRUE.equals(org.getUseScrapedCalendar())) {
                
                log.info("Importing calendar for organization: {} ({})", org.getName(), org.getId());
                
                try {
                    ImportResult result = importCalendarForOrganization(org.getId());
                    results.put(org.getId(), result);
                    
                    // Rate limiting: sleep between requests
                    Thread.sleep(2000); // 2 second delay between organizations
                    
                } catch (Exception e) {
                    ImportResult errorResult = new ImportResult();
                    errorResult.success = false;
                    errorResult.organizationId = org.getId();
                    errorResult.errorMessage = "Exception during import: " + e.getMessage();
                    results.put(org.getId(), errorResult);
                    log.error("Failed to import calendar for organization: " + org.getName(), e);
                }
            }
        }

        log.info("Completed import for {} organizations", results.size());
        return results;
    }

    /**
     * Fetch CSV content from URL using Java HttpClient.
     */
    private String fetchCsvContent(String url) throws IOException, InterruptedException {
        log.debug("Fetching CSV from URL: {}", url);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/csv, text/plain, */*")
                .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP request failed with status: " + response.statusCode());
        }

        log.debug("Successfully fetched CSV content ({} bytes)", response.body().length());
        return response.body();
    }

    /**
     * Process parsed entries: deduplicate and save to database.
     */
    private void processEntries(String organizationId, 
                                List<CalendarCsvParser.ParsedEntry> parsedEntries,
                                String sourceUrl,
                                ImportResult result) {
        
        for (CalendarCsvParser.ParsedEntry parsed : parsedEntries) {
            try {
                // Generate fingerprint for deduplication
                String fingerprint = generateFingerprint(organizationId, parsed);

                // Check if entry already exists
                Optional<OrganizationCalendarEntry> existing = 
                        entryRepository.findByFingerprint(fingerprint);

                if (existing.isPresent()) {
                    // Entry exists - update it
                    OrganizationCalendarEntry entry = existing.get();
                    updateEntry(entry, parsed, sourceUrl);
                    entryRepository.save(entry);
                    result.updatedEntries++;
                    log.debug("Updated existing entry: {} on {}", entry.getTitle(), entry.getDate());
                    
                } else {
                    // New entry - check for duplicates and create
                    OrganizationCalendarEntry newEntry = createEntry(
                            organizationId, parsed, fingerprint, sourceUrl);
                    
                    // Check for similar entries (potential duplicates)
                    if (isDuplicate(organizationId, newEntry)) {
                        newEntry.setEnabled(false);
                        newEntry.setDuplicateReason("Auto-disabled: Similar entry exists");
                        result.duplicatesSkipped++;
                        log.debug("Marked as duplicate: {} on {}", newEntry.getTitle(), newEntry.getDate());
                    } else {
                        result.newEntries++;
                    }
                    
                    entryRepository.save(newEntry);
                    log.debug("Created new entry: {} on {}", newEntry.getTitle(), newEntry.getDate());
                }
                
            } catch (Exception e) {
                log.warn("Failed to process entry: {}", parsed.getTitle(), e);
                // Continue processing other entries
            }
        }
    }

    /**
     * Create a new OrganizationCalendarEntry from parsed data.
     */
    private OrganizationCalendarEntry createEntry(String organizationId,
                                                  CalendarCsvParser.ParsedEntry parsed,
                                                  String fingerprint,
                                                  String sourceUrl) {
        return OrganizationCalendarEntry.builder()
                .organizationId(organizationId)
                .date(parsed.getDate())
                .startTime(parsed.getStartTime())
                .startDatetime(parsed.getStartDatetime())
                .endTime(parsed.getEndTime())
                .endDatetime(parsed.getEndDatetime())
                .title(parsed.getTitle())
                .type(parsed.getType())
                .name(parsed.getName())
                .location(parsed.getLocation())
                .description(parsed.getDescription())
                .hebrewDate(parsed.getHebrewDate())
                .rawText(parsed.getRawText())
                .sourceUrl(sourceUrl)
                .fingerprint(fingerprint)
                .enabled(true)
                .importedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Update an existing entry with new data.
     */
    private void updateEntry(OrganizationCalendarEntry entry,
                            CalendarCsvParser.ParsedEntry parsed,
                            String sourceUrl) {
        entry.setStartTime(parsed.getStartTime());
        entry.setStartDatetime(parsed.getStartDatetime());
        entry.setEndTime(parsed.getEndTime());
        entry.setEndDatetime(parsed.getEndDatetime());
        entry.setTitle(parsed.getTitle());
        entry.setType(parsed.getType());
        entry.setName(parsed.getName());
        entry.setLocation(parsed.getLocation());
        entry.setDescription(parsed.getDescription());
        entry.setHebrewDate(parsed.getHebrewDate());
        entry.setRawText(parsed.getRawText());
        entry.setSourceUrl(sourceUrl);
        entry.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * Generate unique fingerprint for deduplication.
     * Based on: org_id + date + normalized_title + normalized_start_time
     */
    private String generateFingerprint(String organizationId, CalendarCsvParser.ParsedEntry parsed) {
        String normalizedTitle = csvParser.normalizeTitle(parsed.getTitle());
        String normalizedTime = parsed.getStartTime() != null 
                ? csvParser.normalizeTime(parsed.getStartTime()).toString() 
                : "";

        String fingerprintInput = String.format("%s|%s|%s|%s",
                organizationId,
                parsed.getDate().toString(),
                normalizedTitle,
                normalizedTime);

        return hashString(fingerprintInput);
    }

    /**
     * Check if a similar entry already exists (potential duplicate).
     */
    private boolean isDuplicate(String organizationId, OrganizationCalendarEntry newEntry) {
        List<OrganizationCalendarEntry> entriesOnDate = 
                entryRepository.findByOrganizationIdAndDate(organizationId, newEntry.getDate());

        String normalizedNewTitle = csvParser.normalizeTitle(newEntry.getTitle());
        String normalizedNewTime = newEntry.getStartTime() != null
                ? csvParser.normalizeTime(newEntry.getStartTime()).toString()
                : "";

        for (OrganizationCalendarEntry existing : entriesOnDate) {
            String normalizedExistingTitle = csvParser.normalizeTitle(existing.getTitle());
            String normalizedExistingTime = existing.getStartTime() != null
                    ? csvParser.normalizeTime(existing.getStartTime()).toString()
                    : "";

            // Check if titles and times match
            if (normalizedNewTitle.equals(normalizedExistingTitle) 
                    && normalizedNewTime.equals(normalizedExistingTime)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Hash a string using SHA-256.
     */
    private String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    /**
     * Clean up old entries (older than specified date).
     */
    @Transactional
    public void cleanupOldEntries(String organizationId, LocalDate beforeDate) {
        log.info("Cleaning up entries for {} before {}", organizationId, beforeDate);
        entryRepository.deleteByOrganizationIdAndDateBefore(organizationId, beforeDate);
    }
}
