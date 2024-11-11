package com.tbdev.teaneckminyanim.structure.global;

public enum Nusach {
    EDOT_HAMIZRACH("EDOT_HAMIZRACH"),
    SEFARD("SEFARD"),
    ASHKENAZ("ASHKENAZ"),
    ARIZAL("ARIZAL"),
    UNSPECIFIED("UNSPECIFIED");

    private String text;

    Nusach(String s) {
        this.text = s;
    }

    public String getText() {
        return this.text;
    }

//    from string
    public static Nusach fromString(String text) {
        if (text != null) {
            for (Nusach b : Nusach.values()) {
                if (b.text.equalsIgnoreCase(text)) {
                    return b;
                }
            }
        }
//        throw new IllegalArgumentException("No constant with text " + text + " found");
        System.out.println("No nusach constant with text " + text + " found");
        return null;
    }

    public String displayName() {
        switch (this) {
            case EDOT_HAMIZRACH:
                return "Edot Hamizrach";
            case SEFARD:
                return "Sefard";
            case ASHKENAZ:
                return "Ashkenaz";
            case ARIZAL:
                return "Arizal";
            case UNSPECIFIED:
                return "Unspecified";
            default:
                return null;
        }
    }

    public boolean isUnspecified() {
        return this == UNSPECIFIED;
    }

    public boolean isEdotHamizrach() {
        return this == EDOT_HAMIZRACH;
    }

    public boolean isSefard() {
        return this == SEFARD;
    }

    public boolean isAshkenaz() {
        return this == ASHKENAZ;
    }
    
    public boolean isArizal() {
        return this == ARIZAL;
    }
}
