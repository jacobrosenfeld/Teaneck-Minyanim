package com.tbdev.teaneckminyanim.admin.structure.minyan;

public enum MinyanType {
    SHACHARIS("SHACHARIS"),
    MINCHA("MINCHA"),
    MAARIV("MAARIV"),
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
            case SELICHOS:
                return "Selichos";
            case MEGILA_READING:
                return "Megila Reading";
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

    public boolean isSelichos() {
        return this == SELICHOS;
    }

    public boolean isMegilaReading() {
        return this == MEGILA_READING;
    }
}
