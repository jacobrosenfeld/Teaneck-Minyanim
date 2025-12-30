package com.tbdev.teaneckminyanim.enums;

/**
 * Source of a calendar event in the materialized calendar table.
 * 
 * IMPORTED: Event imported from external calendar (CSV/ICS)
 * RULES: Event generated from rule-based minyan schedule
 * MANUAL: Event manually created/overridden by admin (future feature)
 */
public enum EventSource {
    IMPORTED("IMPORTED"),
    RULES("RULES"),
    MANUAL("MANUAL");  // Future feature - not yet implemented

    private final String text;

    EventSource(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public static EventSource fromString(String text) {
        if (text != null) {
            for (EventSource source : EventSource.values()) {
                if (text.equalsIgnoreCase(source.text)) {
                    return source;
                }
            }
        }
        throw new IllegalArgumentException("Unknown EventSource: " + text);
    }

    @Override
    public String toString() {
        return text;
    }

    public String displayName() {
        switch (this) {
            case IMPORTED:
                return "Imported";
            case RULES:
                return "Rule-Based";
            case MANUAL:
                return "Manual Override";
            default:
                return text;
        }
    }

    public boolean isImported() {
        return this == IMPORTED;
    }

    public boolean isRules() {
        return this == RULES;
    }

    public boolean isManual() {
        return this == MANUAL;
    }
}
