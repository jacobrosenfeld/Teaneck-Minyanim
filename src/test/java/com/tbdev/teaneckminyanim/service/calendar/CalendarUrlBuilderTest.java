package com.tbdev.teaneckminyanim.service.calendar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CalendarUrlBuilder
 */
class CalendarUrlBuilderTest {

    private CalendarUrlBuilder urlBuilder;

    @BeforeEach
    void setUp() {
        urlBuilder = new CalendarUrlBuilder();
    }

    @Test
    void testBuildCsvExportUrl_WithBaseUrl() {
        String baseUrl = "https://example.com/calendar";
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 2, 28);

        String result = urlBuilder.buildCsvExportUrl(baseUrl, start, end);

        assertNotNull(result);
        assertTrue(result.contains("example.com/calendar"));
        assertTrue(result.contains("date_start_date=2025-01-01"));
        assertTrue(result.contains("date_end_date=2025-02-28"));
        assertTrue(result.contains("view=other"));
        assertTrue(result.contains("other_view_type=csv"));
        assertTrue(result.contains("advanced=Y"));
    }

    @Test
    void testBuildCsvExportUrl_WithQueryParams() {
        String baseUrl = "https://example.com/calendar?existing=param";
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 1, 31);

        String result = urlBuilder.buildCsvExportUrl(baseUrl, start, end);

        assertNotNull(result);
        assertTrue(result.contains("existing=param"));
        assertTrue(result.contains("date_start_date=2025-01-01"));
        assertTrue(result.contains("date_end_date=2025-01-31"));
    }

    @Test
    void testBuildCsvExportUrl_NullBaseUrl() {
        assertThrows(IllegalArgumentException.class, () -> {
            urlBuilder.buildCsvExportUrl(null);
        });
    }

    @Test
    void testBuildCsvExportUrl_EmptyBaseUrl() {
        assertThrows(IllegalArgumentException.class, () -> {
            urlBuilder.buildCsvExportUrl("");
        });
    }

    @Test
    void testBuildCsvExportUrl_EndDateBeforeStartDate() {
        String baseUrl = "https://example.com/calendar";
        LocalDate start = LocalDate.of(2025, 2, 1);
        LocalDate end = LocalDate.of(2025, 1, 1);

        assertThrows(IllegalArgumentException.class, () -> {
            urlBuilder.buildCsvExportUrl(baseUrl, start, end);
        });
    }

    @Test
    void testBuildCsvExportUrl_DefaultDateRange() {
        String baseUrl = "https://example.com/calendar";
        String result = urlBuilder.buildCsvExportUrl(baseUrl);

        assertNotNull(result);
        // Should contain date parameters (exact dates will vary based on when test runs)
        assertTrue(result.contains("date_start_date="));
        assertTrue(result.contains("date_end_date="));
    }

    @Test
    void testIsValidCalendarUrl_ValidHttp() {
        assertTrue(urlBuilder.isValidCalendarUrl("http://example.com/calendar"));
    }

    @Test
    void testIsValidCalendarUrl_ValidHttps() {
        assertTrue(urlBuilder.isValidCalendarUrl("https://example.com/calendar"));
    }

    @Test
    void testIsValidCalendarUrl_InvalidProtocol() {
        assertFalse(urlBuilder.isValidCalendarUrl("ftp://example.com/calendar"));
    }

    @Test
    void testIsValidCalendarUrl_Null() {
        assertFalse(urlBuilder.isValidCalendarUrl(null));
    }

    @Test
    void testIsValidCalendarUrl_Empty() {
        assertFalse(urlBuilder.isValidCalendarUrl(""));
    }

    @Test
    void testIsValidCalendarUrl_InvalidFormat() {
        assertFalse(urlBuilder.isValidCalendarUrl("not a url"));
    }
}
