package com.tbdev.teaneckminyanim.global;

public enum Zman {
    ALOT_HASHACHAR("ALOT_HASHACHAR"),
    ETT("ETT"),
    NETZ("NETZ"),
    SZKS("SZKS"),
    SZT("SZT"),
    CHATZOS("CHATZOS"),
    MINCHA_GEDOLA("MINCHA_GEDOLA"),
    MINCHA_KETANA("MINCHA_KETANA"),
    PLAG_HAMINCHA("PLAG_HAMINCHA"),
    SHEKIYA("SHEKIYA"),
    EARLIEST_SHEMA("EARLIEST_SHEMA"),
    TZES("TZES"),
    CHATZOS_LAILA("CHATZOS_LAILA");

    private String text;

    Zman(String s) {
        this.text = s;
    }

    public String getText() {
        return this.text;
    }

    public static Zman fromString(String text) {
        if (text != null) {
        for (Zman b : Zman.values()) {
            if (b.text.equalsIgnoreCase(text)) {
                return b;
            }
        }
    }
        throw new IllegalArgumentException("No constant with text " + text + " found");

    }

    public String displayName() {
        switch (this) {
            case ALOT_HASHACHAR:
                return "Alos HaShachar";
            case ETT:
                return "ETT";
            case NETZ:
                return "Netz";
            case SZKS:
                return "Sof Zman Krias Shma";
            case SZT:
                return "Sof Zman Tefilla";   
            case CHATZOS:
                return "Chatzos";
            case MINCHA_GEDOLA:
                return "Mincha Gedola";
            case MINCHA_KETANA:
                return "Mincha Ketana";
            case PLAG_HAMINCHA:
                return "Plag HaMincha";
            case SHEKIYA:
                return "Shekiya";
            case EARLIEST_SHEMA:
                return "Earliest Shema";
            case TZES:
                return "Tzes";
            case CHATZOS_LAILA:
                return "Chatzos Laila";
            default:
                return null;
        }
    }
}
