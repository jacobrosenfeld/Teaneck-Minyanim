package com.tbdev.teaneckminyanim.service.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicLinkRateLimitServiceTest {

    @Test
    void limitsEachIdentifierToThreeRequests() {
        MagicLinkRateLimitService service = new MagicLinkRateLimitService();

        assertTrue(service.allow("ip-1", "account-1"));
        assertTrue(service.allow("ip-1", "account-1"));
        assertTrue(service.allow("ip-1", "account-1"));
        assertFalse(service.allow("ip-1", "account-1"));
    }

    @Test
    void limitsEachIpAcrossIdentifiersToFiveRequests() {
        MagicLinkRateLimitService service = new MagicLinkRateLimitService();

        for (int request = 0; request < 5; request++) {
            assertTrue(service.allow("ip-1", "account-" + request));
        }
        assertFalse(service.allow("ip-1", "account-6"));
    }
}
