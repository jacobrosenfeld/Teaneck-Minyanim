package com.tbdev.teaneckminyanim.front;

import com.tbdev.teaneckminyanim.minyan.MinyanType;
import com.tbdev.teaneckminyanim.enums.Nusach;

//import javax.persistence.Id;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class MinyanEvent {
    TimeZone timeZone = TimeZone.getTimeZone("America/New_York");

    private String parentMinyanId;

    private MinyanType type;

    private String organizationName;

    private Nusach organizationNusach;

    private String organizationId;

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

//    add getters
    public String getOrganizationName() {
        return organizationName;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public String getLocationName() {
        return locationName;
    }

    public Date getStartTime() {
        return startTime;
    }

    public String getFormattedStartTimeOnly() {
//        return startTime.toString();
        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm aa");
            return timeFormat.format(startTime);
        }

    public String getFormattedStartTime() {
        //        return startTime.toString();
                SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm aa");
                if (dynamicTimeString != null) {
                    timeFormat.setTimeZone(timeZone);
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

            if (organizationNusach != nusach && nusach != Nusach.UNSPECIFIED) {
                result += String.format(" (%s)", nusach.displayName());
            }
        } else if (organizationNusach != nusach && nusach != Nusach.UNSPECIFIED) {
            result += nusach.displayName();
        }

        return result;
    }
}