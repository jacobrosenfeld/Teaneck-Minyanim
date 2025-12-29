package com.tbdev.teaneckminyanim.enums;

import com.tbdev.teaneckminyanim.minyan.MinyanType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MinyanClassification conversion methods
 */
class MinyanClassificationConversionTest {

    @Test
    void testToMinyanType_Shacharis() {
        assertEquals(MinyanType.SHACHARIS, MinyanClassification.SHACHARIS.toMinyanType());
    }

    @Test
    void testToMinyanType_Mincha() {
        assertEquals(MinyanType.MINCHA, MinyanClassification.MINCHA.toMinyanType());
    }

    @Test
    void testToMinyanType_Maariv() {
        assertEquals(MinyanType.MAARIV, MinyanClassification.MAARIV.toMinyanType());
    }

    @Test
    void testToMinyanType_MinchaMariv() {
        assertEquals(MinyanType.MINCHA_MAARIV, MinyanClassification.MINCHA_MAARIV.toMinyanType());
    }

    @Test
    void testToMinyanType_Selichos() {
        assertEquals(MinyanType.SELICHOS, MinyanClassification.SELICHOS.toMinyanType());
    }

    @Test
    void testToMinyanType_NonMinyan() {
        assertNull(MinyanClassification.NON_MINYAN.toMinyanType(),
            "NON_MINYAN should convert to null");
    }

    @Test
    void testToMinyanType_Other() {
        assertNull(MinyanClassification.OTHER.toMinyanType(),
            "OTHER should convert to null");
    }

    @Test
    void testFromMinyanType_Shacharis() {
        assertEquals(MinyanClassification.SHACHARIS, 
            MinyanClassification.fromMinyanType(MinyanType.SHACHARIS));
    }

    @Test
    void testFromMinyanType_Mincha() {
        assertEquals(MinyanClassification.MINCHA, 
            MinyanClassification.fromMinyanType(MinyanType.MINCHA));
    }

    @Test
    void testFromMinyanType_Maariv() {
        assertEquals(MinyanClassification.MAARIV, 
            MinyanClassification.fromMinyanType(MinyanType.MAARIV));
    }

    @Test
    void testFromMinyanType_MinchaMariv() {
        assertEquals(MinyanClassification.MINCHA_MAARIV, 
            MinyanClassification.fromMinyanType(MinyanType.MINCHA_MAARIV));
    }

    @Test
    void testFromMinyanType_Selichos() {
        assertEquals(MinyanClassification.SELICHOS, 
            MinyanClassification.fromMinyanType(MinyanType.SELICHOS));
    }

    @Test
    void testFromMinyanType_MegilaReading() {
        assertEquals(MinyanClassification.OTHER, 
            MinyanClassification.fromMinyanType(MinyanType.MEGILA_READING),
            "MEGILA_READING should convert to OTHER");
    }

    @Test
    void testFromMinyanType_Null() {
        assertEquals(MinyanClassification.OTHER, 
            MinyanClassification.fromMinyanType(null),
            "Null should convert to OTHER");
    }

    @Test
    void testRoundTripConversion_Shacharis() {
        MinyanClassification original = MinyanClassification.SHACHARIS;
        MinyanType minyanType = original.toMinyanType();
        MinyanClassification result = MinyanClassification.fromMinyanType(minyanType);
        assertEquals(original, result, "Round-trip conversion should preserve value");
    }

    @Test
    void testRoundTripConversion_AllMinyanTypes() {
        MinyanClassification[] classifications = {
            MinyanClassification.SHACHARIS,
            MinyanClassification.MINCHA,
            MinyanClassification.MAARIV,
            MinyanClassification.MINCHA_MAARIV,
            MinyanClassification.SELICHOS
        };

        for (MinyanClassification original : classifications) {
            MinyanType minyanType = original.toMinyanType();
            assertNotNull(minyanType, original + " should convert to a MinyanType");
            
            MinyanClassification result = MinyanClassification.fromMinyanType(minyanType);
            assertEquals(original, result, 
                "Round-trip conversion should preserve " + original);
        }
    }
}
