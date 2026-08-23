package com.tbdev.teaneckminyanim.front;

import com.tbdev.teaneckminyanim.minyan.MinyanType;
import com.tbdev.teaneckminyanim.enums.Nusach;

//import jakarta.persistence.Id;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Pattern;

public class MinyanEvent {
    // Timezone is set globally in TeaneckMinyanimApplication from settings
    public static final String PLAG_GUIDANCE_TEXT =
            "Plag Mincha-Maariv is intended exclusively for those under the pressing circumstances of she'at ha-dechak (Sh\"A O\"Ch 233:1).";

    private static final Pattern GENERATED_PLAG_NOTE_PATTERN = Pattern.compile(
            "(?i)(^|\\s*\\|\\s*|\\.\\s*)\\bPlag:\\s*\\d{1,2}:\\d{2}\\s*[AP]M\\b\\.?\\s*");
    
    private String parentMinyanId;

    private MinyanType type;

    private String displayTypeName;

    private String publicGroup;

    private String publicGroupDisplayName;

    private String organizationName;

    private Nusach organizationNusach;

    private String organizationId;

    private String organizationSlug;

    private String locationName;

    final private Date startTime;

    private String dynamicTimeString;

    private Nusach nusach;

    private String notes;

    private String orgColor;

    private String whatsapp;

    public MinyanEvent(String parentMinyanId, MinyanType type, String organizationName, Nusach organizationNusach, String organizationId, String locationName, Date startTime, Nusach nusach, String notes, String orgColor, String whatsapp) {
        this.parentMinyanId = parentMinyanId;
        this.type = type;
        this.organizationName = organizationName;
        this.organizationNusach = organizationNusach;
        this.organizationId = organizationId;
        this.locationName = locationName;
        this.startTime = startTime;
        this.nusach = nusach;
        this.notes = notes;
        this.orgColor = orgColor;
        this.whatsapp = whatsapp;
    }

    public MinyanEvent(String parentMinyanId, MinyanType type, String organizationName, Nusach organizationNusach, String organizationId, String locationName, Date startTime, String dynamicTimeString, Nusach nusach, String notes, String orgColor, String whatsapp) {
        this.parentMinyanId = parentMinyanId;
        this.type = type;
        this.organizationName = organizationName;
        this.organizationNusach = organizationNusach;
        this.organizationId = organizationId;
        this.locationName = locationName;
        this.startTime = startTime;
        this.dynamicTimeString = dynamicTimeString;
        this.nusach = nusach;
        this.notes = notes;
        this.orgColor = orgColor;
        this.whatsapp = whatsapp;
    }

    public MinyanType getType() {
        return type;
    }

    public String getDisplayTypeName() {
        return displayTypeName != null ? displayTypeName : type.displayName();
    }

    public void setDisplayTypeName(String displayTypeName) {
        this.displayTypeName = displayTypeName;
    }

    public String getPublicGroup() {
        return publicGroup != null ? publicGroup : type.publicGroupName();
    }

    public void setPublicGroup(String publicGroup) {
        this.publicGroup = publicGroup;
    }

    public String getPublicGroupDisplayName() {
        return publicGroupDisplayName != null ? publicGroupDisplayName : type.publicGroupDisplayName();
    }

    public void setPublicGroupDisplayName(String publicGroupDisplayName) {
        this.publicGroupDisplayName = publicGroupDisplayName;
    }

//    add getters
    public String getOrganizationName() {
        return organizationName;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public String getOrganizationSlug() {
        return organizationSlug;
    }

    public void setOrganizationSlug(String organizationSlug) {
        this.organizationSlug = organizationSlug;
    }

    public String getLocationName() {
        return locationName;
    }

    public Date getStartTime() {
        return startTime;
    }

    public String getFormattedStartTimeOnly() {
//        return startTime.toString();
        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm aa");
            return timeFormat.format(startTime);
        }

    public String getFormattedStartTime() {
        //        return startTime.toString();
                SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm aa");
                if (dynamicTimeString != null) {
                    // Use system default timezone (set from ApplicationSettings)
                    timeFormat.setTimeZone(TimeZone.getDefault());
                    return timeFormat.format(startTime) +  " (" + dynamicTimeString + ")";
                } else {
        //            time zone already set in db
                    return timeFormat.format(startTime);
                }
            }
    public String dynamicTimeString() {
        return dynamicTimeString;
    }
    
    public Nusach getNusach() {
        return nusach;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean hasPlagGuidance() {
        return notes != null && GENERATED_PLAG_NOTE_PATTERN.matcher(notes).find();
    }

    public String getPlagGuidanceText() {
        return PLAG_GUIDANCE_TEXT;
    }

    public String getOrgColor() {
        return orgColor;
    }

    public String getWhatsapp() {
        return whatsapp;
    }

    public String getInformation() {
        String result = "";
        if (locationName != null) {
            result += locationName;
        }

        return result;
    }
}
