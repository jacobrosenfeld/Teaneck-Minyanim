package com.reesedevelopment.greatneckzmanim.admin.structure.minyanim;

import com.reesedevelopment.greatneckzmanim.admin.structure.GNZObject;
import com.reesedevelopment.greatneckzmanim.admin.structure.IDGenerator;
import com.reesedevelopment.greatneckzmanim.admin.structure.location.GNZLocation;
import com.reesedevelopment.greatneckzmanim.admin.structure.organization.GNZOrganization;

import javax.persistence.Column;
import javax.persistence.Table;

@Table(name = "MINYAN")
public class Minyan extends GNZObject implements IDGenerator {
    @Column(name = "TYPE")
    private String minyanTypeString;

    private MinyanType minyanType;

    @Column(name = "LOCATION_ID")
    private String locationId;

    private GNZLocation location;

    @Column(name = "ORGANIZATION_ID")
    private String organizationId;

    private GNZOrganization organization;

    @Column(name = "ENABLED")
    private boolean enabled;

    @Column(name = "START_TIME_1")
    private String startTime1;

    @Column(name = "START_TIME_2")
    private String startTime2;

    @Column(name = "START_TIME_3")
    private String startTime3;

    @Column(name = "START_TIME_4")
    private String startTime4;

    @Column(name = "START_TIME_5")
    private String startTime5;

    @Column(name = "START_TIME_6")
    private String startTime6;

    @Column(name = "START_TIME_7")
    private String startTime7;

    @Column(name = "START_TIME_RC")
    private String startTimeRC;

    @Column(name = "START_TIME_CH")
    private String startTimeCH;

    @Column(name = "START_TIME_CHRC")
    private String startTimeCHRC;

    @Column(name = "START_TIME_YT")
    private String startTimeYT;

    private Schedule schedule;

    @Column(name = "NOTES")
    private String notes;

    @Column(name = "NUSACH")
    private String nusach;

    public Minyan(String id,
                  String minyanTypeString,
                  MinyanType minyanType,
                  String locationId,
                  String organizationId,
                  boolean enabled,
                  String startTime1,
                  String startTime2,
                  String startTime3,
                  String startTime4,
                  String startTime5,
                  String startTime6,
                  String startTime7,
                  String startTimeRC,
                  String startTimeCH,
                  String startTimeCHRC,
                  String startTimeYT,
                  String notes,
                  String nusach
    ) {
        super.id = id;
        this.minyanTypeString = minyanTypeString;
        this.minyanType = minyanType;
        this.locationId = locationId;
        this.organizationId = organizationId;
        this.enabled = enabled;
        this.startTime1 = startTime1;
        this.startTime2 = startTime2;
        this.startTime3 = startTime3;
        this.startTime4 = startTime4;
        this.startTime5 = startTime5;
        this.startTime6 = startTime6;
        this.startTime7 = startTime7;
        this.startTimeRC = startTimeRC;
        this.startTimeCH = startTimeCH;
        this.startTimeCHRC = startTimeCHRC;
        this.startTimeYT = startTimeYT;
        this.notes = notes;
        this.nusach = nusach;
    }

    public Minyan(String minyanTypeString,
                  MinyanType minyanType,
                  String locationId,
                  String organizationId,
                  boolean enabled,
                  String startTime1,
                  String startTime2,
                  String startTime3,
                  String startTime4,
                  String startTime5,
                  String startTime6,
                  String startTime7,
                  String startTimeRC,
                  String startTimeCH,
                  String startTimeCHRC,
                  String startTimeYT,
                  String notes,
                  String nusach
    ) {
        super.id = generateID('M');
        this.minyanTypeString = minyanTypeString;
        this.minyanType = minyanType;
        this.locationId = locationId;
        this.organizationId = organizationId;
        this.enabled = enabled;
        this.startTime1 = startTime1;
        this.startTime2 = startTime2;
        this.startTime3 = startTime3;
        this.startTime4 = startTime4;
        this.startTime5 = startTime5;
        this.startTime6 = startTime6;
        this.startTime7 = startTime7;
        this.startTimeRC = startTimeRC;
        this.startTimeCH = startTimeCH;
        this.startTimeCHRC = startTimeCHRC;
        this.startTimeYT = startTimeYT;
        this.notes = notes;
        this.nusach = nusach;
    }

    public String getMinyanTypeString() {
        return minyanTypeString;
    }

    public MinyanType getMinyanType() {
        return minyanType;
    }

    public String getLocationId() {
        return locationId;
    }

    public GNZLocation getLocation() {
        return location;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public GNZOrganization getOrganization() {
        return organization;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getStartTime1() {
        return startTime1;
    }

    public String getStartTime2() {
        return startTime2;
    }

    public String getStartTime3() {
        return startTime3;
    }

    public String getStartTime4() {
        return startTime4;
    }

    public String getStartTime5() {
        return startTime5;
    }

    public String getStartTime6() {
        return startTime6;
    }

    public String getStartTime7() {
        return startTime7;
    }

    public String getStartTimeRC() {
        return startTimeRC;
    }

    public String getStartTimeCH() {
        return startTimeCH;
    }

    public String getStartTimeCHRC() {
        return startTimeCHRC;
    }

    public String getStartTimeYT() {
        return startTimeYT;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public String getNotes() {
        return notes;
    }

    public String getNusach() {
        return nusach;
    }
}
