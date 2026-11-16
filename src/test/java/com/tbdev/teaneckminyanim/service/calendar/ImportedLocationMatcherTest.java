package com.tbdev.teaneckminyanim.service.calendar;

import com.tbdev.teaneckminyanim.model.Location;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportedLocationMatcherTest {

    private static final String ORG = "org-1";
    private static final Location MAIN_SANCTUARY = new Location("L1", "Main Sanctuary", ORG);
    private static final Location BEIS_MEDRASH = new Location("L2", "Beis Medrash", ORG);
    private static final Location SOCIAL_HALL = new Location("L3", "Social Hall", ORG);
    private static final List<Location> LOCATIONS = List.of(MAIN_SANCTUARY, BEIS_MEDRASH, SOCIAL_HALL);

    @Test
    void normalize_foldsCasePunctuationWhitespaceAndSpellingVariants() {
        assertEquals("main sanctuary", ImportedLocationMatcher.normalize("  The MAIN   Sanctuary. "));
        assertEquals("beis medrash", ImportedLocationMatcher.normalize("Bais Midrash"));
        assertEquals("beis medrash", ImportedLocationMatcher.normalize("Beit-Medresh"));
        assertEquals("rabbis study", ImportedLocationMatcher.normalize("Rabbi’s Study"));
        assertEquals("kiddush and social hall", ImportedLocationMatcher.normalize("Kiddush & Social Hall"));
        assertEquals("", ImportedLocationMatcher.normalize(null));
        assertEquals("", ImportedLocationMatcher.normalize(" -- "));
    }

    @Test
    void match_exactAfterNormalization() {
        assertEquals(Optional.of(MAIN_SANCTUARY), ImportedLocationMatcher.match("main sanctuary", LOCATIONS));
        assertEquals(Optional.of(BEIS_MEDRASH), ImportedLocationMatcher.match("Bais Midrash", LOCATIONS));
        assertEquals(Optional.of(SOCIAL_HALL), ImportedLocationMatcher.match("Social-Hall", LOCATIONS));
    }

    @Test
    void match_locationNameContainedInImportedText() {
        assertEquals(Optional.of(MAIN_SANCTUARY),
                ImportedLocationMatcher.match("Main Sanctuary (upstairs)", LOCATIONS));
        assertEquals(Optional.of(BEIS_MEDRASH),
                ImportedLocationMatcher.match("Congregation X - Beis Medrash, 123 Main St", LOCATIONS));
    }

    @Test
    void match_prefersLongestContainedName() {
        Location bm = new Location("A", "Beis Medrash", ORG);
        Location mainBm = new Location("B", "Main Beis Medrash", ORG);
        assertEquals(Optional.of(mainBm),
                ImportedLocationMatcher.match("Main Bais Medrash", List.of(bm, mainBm)));
    }

    @Test
    void match_importedTextContainedInExactlyOneName() {
        assertEquals(Optional.of(MAIN_SANCTUARY), ImportedLocationMatcher.match("Sanctuary", LOCATIONS));
    }

    @Test
    void match_ambiguousOrUnknownReturnsEmpty() {
        Location upstairs = new Location("U", "Upstairs Hall", ORG);
        // "Hall" is contained in two names: ambiguous, no guess.
        assertTrue(ImportedLocationMatcher.match("Hall", List.of(SOCIAL_HALL, upstairs)).isEmpty());
        assertTrue(ImportedLocationMatcher.match("Main Bais Medrash", List.of(MAIN_SANCTUARY, SOCIAL_HALL)).isEmpty());
        assertTrue(ImportedLocationMatcher.match("Gym", LOCATIONS).isEmpty());
        assertTrue(ImportedLocationMatcher.match("", LOCATIONS).isEmpty());
        assertTrue(ImportedLocationMatcher.match(null, LOCATIONS).isEmpty());
        assertTrue(ImportedLocationMatcher.match("Main Sanctuary", List.of()).isEmpty());
        assertTrue(ImportedLocationMatcher.match("Main Sanctuary", null).isEmpty());
    }

    @Test
    void match_doesNotMatchPartialWords() {
        Location hall = new Location("H", "Hall", ORG);
        assertTrue(ImportedLocationMatcher.match("Hallway", List.of(hall)).isEmpty());
    }

    @Test
    void findInText_findsLocationNameInsideTitle() {
        assertEquals(Optional.of(BEIS_MEDRASH),
                ImportedLocationMatcher.findInText("Shacharis - Bais Medrash", LOCATIONS));
        assertTrue(ImportedLocationMatcher.findInText("Shacharis", LOCATIONS).isEmpty());
    }

    @Test
    void phrasePattern_findsRawVariantsInText() {
        Pattern pattern = ImportedLocationMatcher.phrasePattern("Main Beis Midrash");
        assertTrue(pattern.matcher("Maariv - MAIN BAIS MEDRASH").find());
        assertTrue(pattern.matcher("Maariv (Main-Beit Medresh)").find());

        Pattern apostrophe = ImportedLocationMatcher.phrasePattern("Rabbi's Study");
        assertTrue(apostrophe.matcher("Mincha in Rabbis Study").find());
        assertTrue(apostrophe.matcher("Mincha in Rabbi’s Study").find());

        Pattern ampersand = ImportedLocationMatcher.phrasePattern("Kiddush & Social Hall");
        assertTrue(ampersand.matcher("Mincha - Kiddush and Social Hall").find());

        assertNull(ImportedLocationMatcher.phrasePattern(" , "));
    }
}
