package com.tbdev.teaneckminyanim.service.calendar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CalendarCsvParser
 */
class CalendarCsvParserTest {

    private CalendarCsvParser parser;

    @BeforeEach
    void setUp() {
        parser = new CalendarCsvParser();
    }

    @Test
    void testParseCsv_ValidCsv() throws IOException {
        String csv = """
                Type,Start,End,Name,Location
                Event,2025-01-15 06:30:00,2025-01-15 07:30:00,Shacharis,Main Sanctuary
                Event,2025-01-15 13:45:00,2025-01-15 14:15:00,Mincha,Chapel
                """;

        List<CalendarCsvParser.ParsedEntry> entries = parser.parseCsv(csv);

        assertNotNull(entries);
        assertEquals(2, entries.size());

        CalendarCsvParser.ParsedEntry first = entries.getFirst();
        assertEquals("Shacharis", first.getTitle());
        assertEquals("Main Sanctuary", first.getLocation());
        assertNotNull(first.getDate());
        assertNotNull(first.getStartTime());
    }

    @Test
    void testParseCsv_EmptyContent() throws IOException {
        String csv = "";
        List<CalendarCsvParser.ParsedEntry> entries = parser.parseCsv(csv);
        assertNotNull(entries);
        assertTrue(entries.isEmpty());
    }

    @Test
    void testParseCsv_NullContent() throws IOException {
        List<CalendarCsvParser.ParsedEntry> entries = parser.parseCsv(null);
        assertNotNull(entries);
        assertTrue(entries.isEmpty());
    }

    @Test
    void testParseCsv_MissingColumns() throws IOException {
        String csv = """
                Type,Start
                Event,2025-01-15 06:30:00
                """;

        List<CalendarCsvParser.ParsedEntry> entries = parser.parseCsv(csv);

        assertNotNull(entries);
        assertEquals(1, entries.size());
        // Should handle missing columns gracefully
    }

    @Test
    void testParseCsv_ReorderedColumns() throws IOException {
        String csv = """
                Location,Name,Start,Type
                Main Sanctuary,Shacharis,2025-01-15 06:30:00,Event
                """;

        List<CalendarCsvParser.ParsedEntry> entries = parser.parseCsv(csv);

        assertNotNull(entries);
        assertEquals(1, entries.size());
        assertEquals("Shacharis", entries.getFirst().getTitle());
        assertEquals("Main Sanctuary", entries.getFirst().getLocation());
    }

    @Test
    void testParseCsv_QuotedFields() throws IOException {
        String csv = """
                Type,Start,Name,Description
                Event,2025-01-15 06:30:00,"Morning Service","Daily service, all welcome"
                """;

        List<CalendarCsvParser.ParsedEntry> entries = parser.parseCsv(csv);

        assertNotNull(entries);
        assertEquals(1, entries.size());
        assertEquals("Morning Service", entries.getFirst().getTitle());
        assertEquals("Daily service, all welcome", entries.getFirst().getDescription());
    }

    @Test
    void testNormalizeTitle() {
        assertEquals("shacharis morning service", 
                parser.normalizeTitle("Shacharis Morning Service"));
        assertEquals("mincha afternoon", 
                parser.normalizeTitle("Mincha, Afternoon!"));
        assertEquals("", 
                parser.normalizeTitle(null));
        assertEquals("test 123", 
                parser.normalizeTitle("Test 123"));
    }

    @Test
    void testNormalizeTime() {
        LocalTime time = LocalTime.of(6, 30, 45, 123456789);
        LocalTime normalized = parser.normalizeTime(time);

        assertNotNull(normalized);
        assertEquals(6, normalized.getHour());
        assertEquals(30, normalized.getMinute());
        assertEquals(0, normalized.getSecond());
        assertEquals(0, normalized.getNano());
    }

    @Test
    void testNormalizeTime_Null() {
        assertNull(parser.normalizeTime(null));
    }

    @Test
    void testParseCsv_VariousDateFormats() throws IOException {
        String csv = """
                Type,Start
                Event,2025-01-15 06:30:00
                Event,1/15/2025 6:30 AM
                Event,01/15/2025 06:30:00
                """;

        List<CalendarCsvParser.ParsedEntry> entries = parser.parseCsv(csv);

        assertNotNull(entries);
        // Should parse at least some of these formats
        assertTrue(entries.size() >= 1);
    }

    @Test
    void testParseCsv_FallbackTitle() throws IOException {
        String csv = """
                Type,Start
                Prayer,2025-01-15 06:30:00
                """;

        List<CalendarCsvParser.ParsedEntry> entries = parser.parseCsv(csv);

        assertNotNull(entries);
        assertEquals(1, entries.size());
        // Should use Type as title when Name is missing
        assertEquals("Prayer", entries.getFirst().getTitle());
    }
}
