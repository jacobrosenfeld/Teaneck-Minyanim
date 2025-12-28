package com.tbdev.teaneckminyanim.enums;

/**
 * Classification for imported calendar entries to distinguish minyan times from other events.
 */
public enum MinyanClassification {
    MINYAN("Minyan"),
    MINCHA_MAARIV("Mincha/Maariv"),
    NON_MINYAN("Non-Minyan"),
    OTHER("Other");

    private final String displayName;

    MinyanClassification(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static MinyanClassification fromString(String text) {
        if (text != null) {
            for (MinyanClassification classification : MinyanClassification.values()) {
                if (text.equalsIgnoreCase(classification.name()) || 
                    text.equalsIgnoreCase(classification.displayName)) {
                    return classification;
                }
            }
        }
        return OTHER;
    }

    public boolean isMinyan() {
        return this == MINYAN || this == MINCHA_MAARIV;
    }

    public boolean isNonMinyan() {
        return this == NON_MINYAN;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
