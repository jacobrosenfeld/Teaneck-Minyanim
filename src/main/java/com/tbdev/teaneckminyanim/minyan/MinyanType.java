package com.tbdev.teaneckminyanim.minyan;

import com.tbdev.teaneckminyanim.enums.MinyanClassification;

/**
 * Type of minyan (prayer service).
 * This enum is synchronized with MinyanClassification for seamless conversion 
 * between rule-based minyanim and calendar imports.
 */
public enum MinyanType {
    SHACHARIS("SHACHARIS"),
    MINCHA("MINCHA"),
    MAARIV("MAARIV"),
    MINCHA_MAARIV("MINCHA_MAARIV"),
    SELICHOS("SELICHOS"),
    MEGILA_READING("MEGILAREADING");

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
            throw new IllegalArgumentException("No constant with text " + text + " found");
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
            default:
                return null;
        }
    }

    /**
     * Convert this MinyanType to the corresponding MinyanClassification.
     * 
     * @return Corresponding MinyanClassification, or OTHER for MEGILA_READING
     */
    public MinyanClassification toMinyanClassification() {
        switch (this) {
            case SHACHARIS:
                return MinyanClassification.SHACHARIS;
            case MINCHA:
                return MinyanClassification.MINCHA;
            case MAARIV:
                return MinyanClassification.MAARIV;
            case MINCHA_MAARIV:
                return MinyanClassification.MINCHA_MAARIV;
            case SELICHOS:
                return MinyanClassification.SELICHOS;
            case MEGILA_READING:
                // MEGILA_READING doesn't have a direct classification equivalent
                return MinyanClassification.OTHER;
            default:
                return MinyanClassification.OTHER;
        }
    }

    /**
     * Create a MinyanType from a MinyanClassification.
     * Returns null for NON_MINYAN and OTHER classifications.
     * 
     * @param classification The MinyanClassification to convert
     * @return Corresponding MinyanType, or null if not applicable
     */
    public static MinyanType fromMinyanClassification(MinyanClassification classification) {
        if (classification == null) {
            return null;
        }
        
        switch (classification) {
            case SHACHARIS:
                return SHACHARIS;
            case MINCHA:
                return MINCHA;
            case MAARIV:
                return MAARIV;
            case MINCHA_MAARIV:
                return MINCHA_MAARIV;
            case SELICHOS:
                return SELICHOS;
            case NON_MINYAN:
            case OTHER:
                return null;
            default:
                return null;
        }
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
