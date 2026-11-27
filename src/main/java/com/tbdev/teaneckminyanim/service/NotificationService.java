package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.model.Notification;
import com.tbdev.teaneckminyanim.repo.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    public static final int MAX_TITLE_LENGTH = 255;
    public static final int MAX_MESSAGE_LENGTH = 5000;

    private final NotificationRepository repository;
    private final TNMUserService userService;

    /** Thrown when a notification id does not exist. The message is safe to show. */
    public static class NotificationNotFoundException extends RuntimeException {
        public NotificationNotFoundException() {
            super("Notification not found.");
        }
    }

    /**
     * Input for {@link #create} and {@link #update}. For updates, a null field means "leave unchanged";
     * {@code expirationProvided}/{@code maxDisplaysProvided} say whether those two nullable fields were sent
     * (so an explicit null clears them).
     */
    public record NotificationCommand(String title, String message, String type, Boolean enabled,
                                      LocalDateTime expirationDate, boolean expirationProvided,
                                      Integer maxDisplays, boolean maxDisplaysProvided) {
        /** A full replacement: both nullable fields are applied as given. */
        public static NotificationCommand full(String title, String message, String type, Boolean enabled,
                                               LocalDateTime expirationDate, Integer maxDisplays) {
            return new NotificationCommand(title, message, type, enabled, expirationDate, true, maxDisplays, true);
        }
    }

    /**
     * Get all active banner notifications
     */
    public List<Notification> getActiveBanners() {
        return repository.findActiveByType("BANNER").stream()
                .filter(Notification::isActive)
                .collect(Collectors.toList());
    }

    /**
     * Get all active popup notifications
     */
    public List<Notification> getActivePopups() {
        return repository.findActiveByType("POPUP").stream()
                .filter(Notification::isActive)
                .collect(Collectors.toList());
    }

    /**
     * Get all notifications of a specific type
     */
    public List<Notification> getByType(String type) {
        return repository.findByTypeOrderByCreatedAtDesc(type);
    }

    /**
     * Get all notifications
     */
    public List<Notification> getAll() {
        return repository.findAll();
    }

    /**
     * Find notification by ID
     */
    public Notification findById(String id) {
        return repository.findById(id).orElse(null);
    }

    /**
     * Creates a notification. Super admins only.
     *
     * @throws AccessDeniedException    when the current user is not a super admin
     * @throws IllegalArgumentException for invalid input (message is safe to show)
     */
    @Transactional
    public Notification create(NotificationCommand cmd) {
        requireSuperAdmin();
        if (cmd == null) {
            throw new IllegalArgumentException("Notification details are required.");
        }
        Notification n = new Notification();
        n.setTitle(validTitle(cmd.title(), true));
        n.setMessage(validMessage(cmd.message(), true));
        n.setType(validType(cmd.type(), true));
        n.setEnabled(cmd.enabled() == null || cmd.enabled());
        n.setExpirationDate(cmd.expirationDate());
        n.setMaxDisplays(validMaxDisplays(cmd.maxDisplays()));
        return repository.save(n);
    }

    /**
     * Updates a notification. Super admins only.
     *
     * @throws NotificationNotFoundException when no notification has this id
     */
    @Transactional
    public Notification update(String id, NotificationCommand cmd) {
        requireSuperAdmin();
        Notification n = find(id);
        if (cmd == null) {
            throw new IllegalArgumentException("Notification details are required.");
        }
        String title = validTitle(cmd.title(), false);
        String message = validMessage(cmd.message(), false);
        String type = validType(cmd.type(), false);
        Integer maxDisplays = cmd.maxDisplaysProvided() ? validMaxDisplays(cmd.maxDisplays()) : n.getMaxDisplays();
        if (title != null) n.setTitle(title);
        if (message != null) n.setMessage(message);
        if (type != null) n.setType(type);
        if (cmd.enabled() != null) n.setEnabled(cmd.enabled());
        if (cmd.expirationProvided()) n.setExpirationDate(cmd.expirationDate());
        n.setMaxDisplays(maxDisplays);
        return repository.save(n);
    }

    /** Enables or disables a notification. Super admins only. */
    @Transactional
    public Notification setEnabled(String id, boolean enabled) {
        requireSuperAdmin();
        Notification n = find(id);
        n.setEnabled(enabled);
        return repository.save(n);
    }

    /** Flips the enabled flag and returns the new value. Super admins only. */
    @Transactional
    public boolean toggleEnabled(String id) {
        requireSuperAdmin();
        Notification n = find(id);
        n.setEnabled(!Boolean.TRUE.equals(n.getEnabled()));
        return repository.save(n).getEnabled();
    }

    /** Deletes a notification. Super admins only. */
    @Transactional
    public void delete(String id) {
        requireSuperAdmin();
        repository.delete(find(id));
    }

    /**
     * Parses an ISO-8601 date-time. A date-time without an offset is read in the site time zone; one with an
     * offset is converted to it. Blank means no expiration.
     */
    public static LocalDateTime parseExpiration(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        try {
            return LocalDateTime.parse(v);
        } catch (DateTimeParseException ignored) {
            // fall through to the offset form
        }
        try {
            return OffsetDateTime.parse(v).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "expirationDate must be an ISO-8601 date-time, such as 2026-12-31T23:59:00.");
        }
    }

    private Notification find(String id) {
        return (id == null ? java.util.Optional.<Notification>empty() : repository.findById(id))
                .orElseThrow(NotificationNotFoundException::new);
    }

    private void requireSuperAdmin() {
        if (!userService.isSuperAdmin()) {
            throw new AccessDeniedException("Only super admins can manage notifications.");
        }
    }

    private static String validTitle(String title, boolean required) {
        if (title == null && !required) return null;
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) throw new IllegalArgumentException("Title is required.");
        if (t.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("Title can be at most " + MAX_TITLE_LENGTH + " characters.");
        }
        return t;
    }

    private static String validMessage(String message, boolean required) {
        if (message == null && !required) return null;
        String m = message == null ? "" : message.trim();
        if (m.isEmpty()) throw new IllegalArgumentException("Message is required.");
        if (m.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Message can be at most " + MAX_MESSAGE_LENGTH + " characters.");
        }
        return m;
    }

    private static String validType(String type, boolean required) {
        if (type == null && !required) return null;
        String t = type == null ? "" : type.trim().toUpperCase(java.util.Locale.ROOT);
        if (!t.equals("BANNER") && !t.equals("POPUP")) {
            throw new IllegalArgumentException("Type must be BANNER or POPUP.");
        }
        return t;
    }

    private static Integer validMaxDisplays(Integer maxDisplays) {
        if (maxDisplays != null && maxDisplays < 1) {
            throw new IllegalArgumentException("Max displays must be at least 1.");
        }
        return maxDisplays;
    }
}
