package com.tbdev.teaneckminyanim.minyan;

import com.tbdev.teaneckminyanim.enums.MinyanClassification;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MinyanType conversion methods
 */
class MinyanTypeConversionTest {

    @Test
    void testToMinyanClassification_Shacharis() {
        assertEquals(MinyanClassification.SHACHARIS, 
            MinyanType.SHACHARIS.toMinyanClassification());
    }

    @Test
    void testToMinyanClassification_Mincha() {
        assertEquals(MinyanClassification.MINCHA, 
            MinyanType.MINCHA.toMinyanClassification());
    }

    @Test
    void testToMinyanClassification_Maariv() {
        assertEquals(MinyanClassification.MAARIV, 
            MinyanType.MAARIV.toMinyanClassification());
    }

    @Test
    void testToMinyanClassification_MinchaMariv() {
        assertEquals(MinyanClassification.MINCHA_MAARIV, 
            MinyanType.MINCHA_MAARIV.toMinyanClassification());
    }

    @Test
    void testToMinyanClassification_Selichos() {
        assertEquals(MinyanClassification.SELICHOS, 
            MinyanType.SELICHOS.toMinyanClassification());
    }

    @Test
    void testToMinyanClassification_MegilaReading() {
        assertEquals(MinyanClassification.OTHER, 
            MinyanType.MEGILA_READING.toMinyanClassification(),
            "MEGILA_READING should convert to OTHER");
    }

    @Test
    void testFromMinyanClassification_Shacharis() {
        assertEquals(MinyanType.SHACHARIS, 
            MinyanType.fromMinyanClassification(MinyanClassification.SHACHARIS));
    }

    @Test
    void testFromMinyanClassification_Mincha() {
        assertEquals(MinyanType.MINCHA, 
            MinyanType.fromMinyanClassification(MinyanClassification.MINCHA));
    }

    @Test
    void testFromMinyanClassification_Maariv() {
        assertEquals(MinyanType.MAARIV, 
            MinyanType.fromMinyanClassification(MinyanClassification.MAARIV));
    }

    @Test
    void testFromMinyanClassification_MinchaMariv() {
        assertEquals(MinyanType.MINCHA_MAARIV, 
            MinyanType.fromMinyanClassification(MinyanClassification.MINCHA_MAARIV));
    }

    @Test
    void testFromMinyanClassification_Selichos() {
        assertEquals(MinyanType.SELICHOS, 
            MinyanType.fromMinyanClassification(MinyanClassification.SELICHOS));
    }

    @Test
    void testFromMinyanClassification_NonMinyan() {
        assertNull(MinyanType.fromMinyanClassification(MinyanClassification.NON_MINYAN),
            "NON_MINYAN should convert to null");
    }

    @Test
    void testFromMinyanClassification_Other() {
        assertNull(MinyanType.fromMinyanClassification(MinyanClassification.OTHER),
            "OTHER should convert to null");
    }

    @Test
    void testFromMinyanClassification_Null() {
        assertNull(MinyanType.fromMinyanClassification(null),
            "Null should convert to null");
    }

    @Test
    void testRoundTripConversion_Shacharis() {
        MinyanType original = MinyanType.SHACHARIS;
        MinyanClassification classification = original.toMinyanClassification();
        MinyanType result = MinyanType.fromMinyanClassification(classification);
        assertEquals(original, result, "Round-trip conversion should preserve value");
    }

    @Test
    void testRoundTripConversion_AllTypes() {
        MinyanType[] types = {
            MinyanType.SHACHARIS,
            MinyanType.MINCHA,
            MinyanType.MAARIV,
            MinyanType.MINCHA_MAARIV,
            MinyanType.SELICHOS
        };

        for (MinyanType original : types) {
            MinyanClassification classification = original.toMinyanClassification();
            assertNotNull(classification, original + " should convert to a MinyanClassification");
            
            MinyanType result = MinyanType.fromMinyanClassification(classification);
            assertEquals(original, result, 
                "Round-trip conversion should preserve " + original);
        }
    }

    @Test
    void testMegilaReadingDoesNotRoundTrip() {
        MinyanType original = MinyanType.MEGILA_READING;
        MinyanClassification classification = original.toMinyanClassification();
        assertEquals(MinyanClassification.OTHER, classification);
        
        MinyanType result = MinyanType.fromMinyanClassification(classification);
        assertNull(result, "MEGILA_READING -> OTHER -> null (doesn't round-trip)");
    }
}
