package com.tbdev.teaneckminyanim.enums;

/**
 * Classification for imported calendar entries to distinguish minyan types and other events.
 * Each minyan type (Shacharis, Mincha, Maariv, etc.) has its own enum value for robust pattern matching.
 */
public enum MinyanClassification {
    SHACHARIS("Shacharis"),
    MINCHA("Mincha"),
    MAARIV("Maariv"),
    MINCHA_MAARIV("Mincha/Maariv"),
    SELICHOS("Selichos"),
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

    /**
     * Check if this classification represents a minyan event (any prayer service).
     */
    public boolean isMinyan() {
        return this == SHACHARIS || this == MINCHA || this == MAARIV || 
               this == MINCHA_MAARIV || this == SELICHOS;
    }

    /**
     * Check if this classification represents a non-minyan event.
     */
    public boolean isNonMinyan() {
        return this == NON_MINYAN;
    }

    /**
     * Check if this is a Shacharis minyan.
     */
    public boolean isShacharis() {
        return this == SHACHARIS;
    }

    /**
     * Check if this is a Mincha minyan (not combined Mincha/Maariv).
     */
    public boolean isMincha() {
        return this == MINCHA;
    }

    /**
     * Check if this is a Maariv minyan (not combined Mincha/Maariv).
     */
    public boolean isMaariv() {
        return this == MAARIV;
    }

    /**
     * Check if this is a combined Mincha/Maariv service.
     */
    public boolean isMinchaMariv() {
        return this == MINCHA_MAARIV;
    }

    /**
     * Check if this is a Selichos service.
     */
    public boolean isSelichos() {
        return this == SELICHOS;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
