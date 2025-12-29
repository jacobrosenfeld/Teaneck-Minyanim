package com.tbdev.teaneckminyanim.minyan;

/**
 * Type of minyan (prayer service) or calendar event.
 * Used for both rule-based minyanim and imported calendar entries.
 * 
 * Prayer service types: SHACHARIS, MINCHA, MAARIV, MINCHA_MAARIV, SELICHOS, MEGILA_READING
 * Non-prayer types: NON_MINYAN (learning/social events), OTHER (unclassified events)
 * Legacy: MINYAN (generic prayer service - for backward compatibility only)
 */
public enum MinyanType {
    SHACHARIS("SHACHARIS"),
    MINCHA("MINCHA"),
    MAARIV("MAARIV"),
    MINCHA_MAARIV("MINCHA_MAARIV"),
    SELICHOS("SELICHOS"),
    MEGILA_READING("MEGILAREADING"),
    NON_MINYAN("NON_MINYAN"),
    OTHER("OTHER"),
    @Deprecated
    MINYAN("MINYAN");  // Legacy - kept for database backward compatibility

    private String text;

    MinyanType(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public static MinyanType fromString(String text) {
        if (text != null) {
            for (MinyanType b : MinyanType.values()) {
                if (text.equalsIgnoreCase(b.text)) {
                    return b;
                }
            }
        }
        // Return OTHER instead of throwing exception for unknown values
        return OTHER;
    }


    @Override
    public String toString() {
        switch (this) {
            case SHACHARIS:
                return "SHACHARIS";
            case MINCHA:
                return "MINCHA";
            case MAARIV:
                return "MAARIV";
            case MINCHA_MAARIV:
                return "MINCHA_MAARIV";
            case SELICHOS:
                return "SELICHOS";
            case MEGILA_READING:
                return "MEGILAREADING";
            case NON_MINYAN:
                return "NON_MINYAN";
            case OTHER:
                return "OTHER";
            case MINYAN:
                return "MINYAN";
            default:
                return null;
        }
    }

    public String displayName() {
        switch (this) {
            case SHACHARIS:
                return "Shacharis";
            case MINCHA:
                return "Mincha";
            case MAARIV:
                return "Maariv";
            case MINCHA_MAARIV:
                return "Mincha/Maariv";
            case SELICHOS:
                return "Selichos";
            case MEGILA_READING:
                return "Megila Reading";
            case NON_MINYAN:
                return "Non-Minyan";
            case OTHER:
                return "Other";
            case MINYAN:
                return "Minyan";  // Legacy
            default:
                return null;
        }
    }

    /**
     * Check if this type represents a minyan event (any prayer service).
     * Returns true for prayer services, false for non-prayer events.
     */
    public boolean isMinyan() {
        return this == SHACHARIS || this == MINCHA || this == MAARIV || 
               this == MINCHA_MAARIV || this == SELICHOS || this == MEGILA_READING ||
               this == MINYAN;  // Legacy support
    }

    /**
     * Check if this type represents a non-minyan event (learning/social events).
     */
    public boolean isNonMinyan() {
        return this == NON_MINYAN;
    }

    public boolean isShacharis() {
        return this == SHACHARIS;
    }

    public boolean isMincha() {
        return this == MINCHA;
    }

    public boolean isMaariv() {
        return this == MAARIV;
    }

    public boolean isMinchaMariv() {
        return this == MINCHA_MAARIV;
    }

    public boolean isSelichos() {
        return this == SELICHOS;
    }

    public boolean isMegilaReading() {
        return this == MEGILA_READING;
    }
}
