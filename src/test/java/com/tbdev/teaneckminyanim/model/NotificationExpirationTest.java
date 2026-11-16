package com.tbdev.teaneckminyanim.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationExpirationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 21, 12, 0);

    private static Notification notification(boolean enabled, LocalDateTime expirationDate) {
        Notification n = new Notification();
        n.setEnabled(enabled);
        n.setExpirationDate(expirationDate);
        return n;
    }

    @Test
    void nullExpirationNeverExpires() {
        Notification n = notification(true, null);
        assertFalse(n.isExpired(NOW));
        assertFalse(n.isExpired());
        assertTrue(n.isActive());
    }

    @Test
    void pastExpirationIsExpired() {
        assertTrue(notification(true, NOW.minusDays(1)).isExpired(NOW));
        assertTrue(notification(true, NOW.minusNanos(1)).isExpired(NOW));
    }

    @Test
    void futureExpirationIsNotExpired() {
        assertFalse(notification(true, NOW.plusDays(1)).isExpired(NOW));
        assertFalse(notification(true, NOW.plusNanos(1)).isExpired(NOW));
    }

    @Test
    void expirationExactlyNowIsNotYetExpired() {
        // Boundary: a notification is expired only once now is strictly after its expiration date.
        assertFalse(notification(true, NOW).isExpired(NOW));
    }

    @Test
    void noArgIsExpiredUsesTheCurrentClock() {
        assertTrue(notification(true, LocalDateTime.now().minusMinutes(1)).isExpired());
        assertFalse(notification(true, LocalDateTime.now().plusMinutes(5)).isExpired());
    }

    @Test
    void activeRequiresEnabledAndNotExpired() {
        assertTrue(notification(true, LocalDateTime.now().plusDays(1)).isActive());
        assertFalse(notification(true, LocalDateTime.now().minusDays(1)).isActive());
        assertFalse(notification(false, null).isActive());
        assertFalse(notification(false, LocalDateTime.now().minusDays(1)).isActive());
    }
}
