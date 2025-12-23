package com.tbdev.teaneckminyanim.service.calendar;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Service for building calendar CSV export URLs with proper query parameters.
 * Generates deterministic URLs from base calendar URLs with date range parameters.
 */
@Slf4j
@Service
public class CalendarUrlBuilder {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Builds a CSV export URL from a base calendar URL.
     * Adds query parameters for date range and CSV export format.
     * 
     * @param baseUrl Base calendar URL from organization.calendar field
     * @return Complete CSV export URL with all required parameters
     */
    public String buildCsvExportUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Base URL cannot be null or empty");
        }

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(7);   // Today - 7 days
        LocalDate endDate = today.plusDays(56);     // Today + 56 days (8 weeks)

        return buildCsvExportUrl(baseUrl, startDate, endDate);
    }

    /**
     * Builds a CSV export URL with custom date range.
     * 
     * @param baseUrl Base calendar URL
     * @param startDate Start date for the export
     * @param endDate End date for the export
     * @return Complete CSV export URL
     */
    public String buildCsvExportUrl(String baseUrl, LocalDate startDate, LocalDate endDate) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Base URL cannot be null or empty");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start date and end date cannot be null");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }

        String startDateStr = startDate.format(DATE_FORMATTER);
        String endDateStr = endDate.format(DATE_FORMATTER);

        log.debug("Building CSV export URL from base: {} with date range {} to {}", 
                baseUrl, startDateStr, endDateStr);

        try {
            // Parse the base URL and preserve existing parameters
            URI baseUri = URI.create(baseUrl.trim());
            
            // Build the complete URL with calendar export parameters
            UriComponentsBuilder builder = UriComponentsBuilder.fromUri(baseUri);
            
            // Add calendar export parameters if not already present
            if (!baseUrl.contains("advanced=")) {
                builder.queryParam("advanced", "Y");
            }
            if (!baseUrl.contains("date_start=")) {
                builder.queryParam("date_start", "specific date");
            }
            if (!baseUrl.contains("date_start_x=")) {
                builder.queryParam("date_start_x", "0");
            }
            if (!baseUrl.contains("has_second_date=")) {
                builder.queryParam("has_second_date", "Y");
            }
            if (!baseUrl.contains("date_end=")) {
                builder.queryParam("date_end", "specific date");
            }
            if (!baseUrl.contains("date_end_x=")) {
                builder.queryParam("date_end_x", "0");
            }
            if (!baseUrl.contains("view=")) {
                builder.queryParam("view", "other");
            }
            if (!baseUrl.contains("other_view_type=")) {
                builder.queryParam("other_view_type", "csv");
            }

            // Always set or replace date parameters to ensure correct date range
            builder.replaceQueryParam("date_start_date", startDateStr)
                   .replaceQueryParam("date_end_date", endDateStr);

            // Add status filter (confirmed events only) if not present
            if (!baseUrl.contains("status[]=")) {
                builder.queryParam("status[]", "confirmed");
            }

            String finalUrl = builder.build().toUriString();
            log.debug("Built CSV export URL: {}", finalUrl);
            
            return finalUrl;
            
        } catch (IllegalArgumentException e) {
            log.error("Invalid base URL format: {}", baseUrl, e);
            throw new IllegalArgumentException("Invalid base URL format: " + baseUrl, e);
        }
    }

    /**
     * Validates if a URL appears to be a valid calendar base URL.
     * Basic validation - checks for http/https protocol.
     * 
     * @param url URL to validate
     * @return true if URL is valid
     */
    public boolean isValidCalendarUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            return scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid URL format: {}", url, e);
            return false;
        }
    }
}
