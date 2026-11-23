package com.tbdev.teaneckminyanim.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Permission scopes an API key can carry. Mobile app tokens hold every scope; a key either holds
 * "same as owner" or an explicit subset of these. Whatever the scope, a request can never do more
 * than the key's owner can do at that moment.
 */
public enum ApiScope {
    SCHEDULE_READ("schedule:read", "Read schedules"),
    OVERRIDES_WRITE("overrides:write", "Create, change and delete manual overrides"),
    MINYANIM_WRITE("minyanim:write", "Enable, disable and edit minyanim"),
    NOTIFICATIONS_WRITE("notifications:write", "Create, change and delete announcements"),
    SHUL_WRITE("shul:write", "Edit shul profile details");

    private final String value;
    private final String description;

    ApiScope(String value, String description) {
        this.value = value;
        this.description = description;
    }

    /** The wire value, e.g. {@code overrides:write}. */
    @JsonValue
    public String value() {
        return value;
    }

    /** Short human-readable description for admin pages and docs. */
    public String description() {
        return description;
    }

    /**
     * Parses a wire value ({@code overrides:write}); the enum name ({@code OVERRIDES_WRITE}) is accepted too.
     *
     * @throws IllegalArgumentException for an unknown or blank value
     */
    @JsonCreator
    public static ApiScope fromValue(String value) {
        return find(value).orElseThrow(() -> new IllegalArgumentException("Unknown API scope: " + value));
    }

    /** Same as {@link #fromValue(String)} but empty instead of throwing. */
    public static Optional<ApiScope> find(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        return Arrays.stream(values())
                .filter(scope -> scope.value.equalsIgnoreCase(trimmed)
                        || scope.name().equals(trimmed.toUpperCase(Locale.ROOT)))
                .findFirst();
    }

    @Override
    public String toString() {
        return value;
    }
}
