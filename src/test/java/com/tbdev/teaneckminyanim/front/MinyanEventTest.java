package com.tbdev.teaneckminyanim.front;

import com.tbdev.teaneckminyanim.enums.Nusach;
import com.tbdev.teaneckminyanim.minyan.MinyanType;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinyanEventTest {

    @Test
    void hasPlagGuidance_detectsGeneratedPlagNote() {
        MinyanEvent event = eventWithNotes("Plag: 5:45 PM");

        assertTrue(event.hasPlagGuidance());
    }

    @Test
    void hasPlagGuidance_detectsGeneratedPlagNoteAfterOtherNotes() {
        MinyanEvent event = eventWithNotes("Teen Minyan | Plag: 5:45 PM");

        assertTrue(event.hasPlagGuidance());
    }

    @Test
    void hasPlagGuidance_ignoresMissingOrNonGeneratedNotes() {
        assertFalse(eventWithNotes(null).hasPlagGuidance());
        assertFalse(eventWithNotes("Shkiya: 7:10 PM").hasPlagGuidance());
        assertFalse(eventWithNotes("Ask about plag timing").hasPlagGuidance());
    }

    private MinyanEvent eventWithNotes(String notes) {
        return new MinyanEvent(
                "manual-1",
                MinyanType.MINCHA_MAARIV,
                "Org",
                Nusach.ASHKENAZ,
                "org-1",
                "Main",
                new Date(),
                Nusach.ASHKENAZ,
                notes,
                "#000000",
                null);
    }
}
