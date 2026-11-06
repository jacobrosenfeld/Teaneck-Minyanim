package com.tbdev.teaneckminyanim.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TNMUserEmailValidationTest {
    @Test
    void acceptsNormalizedAccountAddressesWithinLengthLimit() {
        assertTrue(TNMUser.isValidAccountEmail(TNMUser.normalizeEmail(" Admin+tag@Sub.Example.com ")));
        assertTrue(TNMUser.isValidAccountEmail("a".repeat(250) + "@b.c"));
    }

    @Test
    void rejectsMalformedAndOversizedAddresses() {
        assertFalse(TNMUser.isValidAccountEmail(null));
        for (String email : List.of("", "@example.com", "a@example", "a@.com",
                "a@example.", "a@@example.com", "a b@example.com", "a@exam\tple.com",
                "a@example.com\n", "a".repeat(251) + "@b.c", "!@!." + "!.".repeat(10_000) + "@")) {
            assertFalse(TNMUser.isValidAccountEmail(email));
        }
    }
}
