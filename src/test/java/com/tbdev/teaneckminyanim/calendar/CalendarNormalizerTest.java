package com.tbdev.teaneckminyanim.calendar;

import com.tbdev.teaneckminyanim.minyan.MinyanType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class CalendarNormalizerTest {

    private CalendarNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new CalendarNormalizer();
    }

    @Test
    void testNormalizeTitle() {
        // Test basic normalization
        assertEquals("shacharis", normalizer.normalizeTitle("Shacharis"));
        assertEquals("shacharis", normalizer.normalizeTitle("  Shacharis  "));
        assertEquals("shacharis", normalizer.normalizeTitle("Shacharis!!!"));
        
        // Test space collapsing
        assertEquals("mincha minyan", normalizer.normalizeTitle("Mincha    Minyan"));
        
        // Test punctuation removal
        assertEquals("shacharis netz", normalizer.normalizeTitle("Shacharis (Netz)"));
    }

    @Test
    void testNormalizeTime() {
        // Test standard formats
        assertEquals(LocalTime.of(7, 30), normalizer.normalizeTime("7:30 AM"));
        assertEquals(LocalTime.of(7, 30), normalizer.normalizeTime("7:30AM"));
        assertEquals(LocalTime.of(7, 30), normalizer.normalizeTime("7:30am"));
        
        // Test PM times
        assertEquals(LocalTime.of(19, 30), normalizer.normalizeTime("7:30 PM"));
        assertEquals(LocalTime.of(19, 30), normalizer.normalizeTime("7:30pm"));
        
        // Test noon and midnight edge cases
        assertEquals(LocalTime.of(12, 0), normalizer.normalizeTime("12:00 PM"));
        assertEquals(LocalTime.of(0, 0), normalizer.normalizeTime("12:00 AM"));
        
        // Test 24-hour format
        assertEquals(LocalTime.of(19, 30), normalizer.normalizeTime("19:30"));
        
        // Test alternative formats
        assertEquals(LocalTime.of(7, 30), normalizer.normalizeTime("7.30 AM"));
    }

    @Test
    void testInferMinyanType() {
        // Test Shacharis variations
        assertEquals(MinyanType.SHACHARIS, normalizer.inferMinyanType("Shacharis"));
        assertEquals(MinyanType.SHACHARIS, normalizer.inferMinyanType("Morning Minyan"));
        assertEquals(MinyanType.SHACHARIS, normalizer.inferMinyanType("Shacharit"));
        
        // Test Mincha
        assertEquals(MinyanType.MINCHA, normalizer.inferMinyanType("Mincha"));
        assertEquals(MinyanType.MINCHA, normalizer.inferMinyanType("Afternoon Service"));
        
        // Test Maariv
        assertEquals(MinyanType.MAARIV, normalizer.inferMinyanType("Maariv"));
        assertEquals(MinyanType.MAARIV, normalizer.inferMinyanType("Evening Minyan"));
        assertEquals(MinyanType.MAARIV, normalizer.inferMinyanType("Arvit"));
        
        // Test Selichos
        assertEquals(MinyanType.SELICHOS, normalizer.inferMinyanType("Selichos"));
        assertEquals(MinyanType.SELICHOS, normalizer.inferMinyanType("Selichot"));
        
        // Test unknown types
        assertNull(normalizer.inferMinyanType("Unknown Service"));
        assertNull(normalizer.inferMinyanType(""));
    }

    @Test
    void testGenerateFingerprint() {
        // Test that same input produces same fingerprint
        String fp1 = normalizer.generateFingerprint("org1", "2024-01-01", "Shacharis", "07:30");
        String fp2 = normalizer.generateFingerprint("org1", "2024-01-01", "Shacharis", "07:30");
        assertEquals(fp1, fp2);
        
        // Test that normalized titles produce same fingerprint
        String fp3 = normalizer.generateFingerprint("org1", "2024-01-01", "SHACHARIS!!!", "07:30");
        assertEquals(fp1, fp3);
        
        // Test that different inputs produce different fingerprints
        String fp4 = normalizer.generateFingerprint("org1", "2024-01-01", "Mincha", "07:30");
        assertNotEquals(fp1, fp4);
        
        // Test that fingerprint is consistently 64 characters (SHA-256 hex)
        assertEquals(64, fp1.length());
    }

    @Test
    void testNormalizeTimeEdgeCases() {
        // Test null/empty
        assertNull(normalizer.normalizeTime(null));
        assertNull(normalizer.normalizeTime(""));
        assertNull(normalizer.normalizeTime("   "));
        
        // Test invalid formats
        assertNull(normalizer.normalizeTime("not a time"));
        assertNull(normalizer.normalizeTime("25:00"));
    }

    @Test
    void testNormalizeTitleEdgeCases() {
        // Test null
        assertEquals("", normalizer.normalizeTitle(null));
        
        // Test empty
        assertEquals("", normalizer.normalizeTitle(""));
        
        // Test whitespace only
        assertEquals("", normalizer.normalizeTitle("   "));
    }
}
