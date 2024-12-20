package com.tbdev.teaneckminyanim.admin.structure.minyan;

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;
import com.kosherjava.zmanim.util.Time;
import com.tbdev.teaneckminyanim.admin.structure.TNMObject;
import com.tbdev.teaneckminyanim.admin.structure.IDGenerator;
import com.tbdev.teaneckminyanim.admin.structure.location.Location;
import com.tbdev.teaneckminyanim.admin.structure.organization.Organization;
import com.tbdev.teaneckminyanim.global.Nusach;

import javax.persistence.Column;
import javax.persistence.Table;
import java.time.LocalDate;
import java.util.Date;

@Table(name = "MINYAN")
public class Minyan extends TNMObject implements IDGenerator {
    @Column(name = "TYPE", nullable = false)
    private String minyanTypeString;

    private MinyanType minyanType;

    @Column(name = "LOCATION_ID")
    private String locationId;

    private Location location;

    @Column(name = "ORGANIZATION_ID", nullable = false)
    private String organizationId;

    private Organization organization;

    @Column(name = "ENABLED", nullable = false)
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

    @Column(name = "START_TIME_YT")
    private String startTimeYT;

    @Column(name = "START_TIME_CH")
    private String startTimeCH;

    @Column(name = "START_TIME_CHRC")
    private String startTimeCHRC;

    private Schedule schedule;

    @Column(name = "NOTES")
    private String notes;

    @Column(name = "NUSACH", nullable = false)
    private String nusachString;

    private Nusach nusach;

    private String orgColor;

    @Column(name = "WHATSAPP")
    private String whatsapp;

//    @Autowired
//    private OrganizationDAO organizationDAO;

    public Minyan(String id,
                  String minyanTypeString,
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
                  String startTimeYT,
                  String startTimeCH,
                  String startTimeCHRC,
                  String notes,
                  String nusach,
                  String orgColor,
                  String whatsapp
    ) {
        super.id = id;
        this.minyanTypeString = minyanTypeString;
        this.minyanType = MinyanType.fromString(minyanTypeString);
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
        this.startTimeYT = startTimeYT;
        this.startTimeCH = startTimeCH;
        this.startTimeCHRC = startTimeCHRC;
        this.schedule = new Schedule(startTime1, startTime2, startTime3, startTime4, startTime5, startTime6, startTime7, startTimeRC, startTimeYT, startTimeCH, startTimeCHRC);
        this.notes = notes;
        this.nusachString = nusach;
        this.nusach = Nusach.fromString(nusach);
        this.orgColor = orgColor;
        this.whatsapp = whatsapp;
    }

    public Minyan(String minyanTypeString,
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
                  String startTimeYT,
                  String startTimeCH,
                  String startTimeCHRC,
                  String notes,
                  String nusach,
                  String orgColor,
                  String whatsapp
    ) {
        super.id = generateID('M');
        this.minyanTypeString = minyanTypeString;
        this.minyanType = MinyanType.fromString(minyanTypeString);
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
        this.startTimeYT = startTimeYT;
        this.startTimeCH = startTimeCH;
        this.startTimeCHRC = startTimeCHRC;
        this.schedule = new Schedule(startTime1, startTime2, startTime3, startTime4, startTime5, startTime6, startTime7, startTimeRC, startTimeYT, startTimeCH, startTimeCHRC);
        this.notes = notes;
        this.nusachString = nusach;
        this.nusach = Nusach.fromString(nusach);
        this.orgColor = orgColor;
        this.whatsapp = whatsapp;
    }

    public Minyan(Organization organization, MinyanType type, Location location, Schedule schedule, String notes, Nusach nusach, boolean enabled, String orgColor, String whatsapp) {
        super.id = generateID('M');
        this.minyanTypeString = type.toString();
        this.minyanType = type;
        if (location != null) {
            this.locationId = location.getId();
            this.location = location;
        }
        this.organizationId = organization.getId();
        this.organization = organization;
        this.startTime1 = schedule.getSunday().toString();
        this.startTime2 = schedule.getMonday().toString();
        this.startTime3 = schedule.getTuesday().toString();
        this.startTime4 = schedule.getWednesday().toString();
        this.startTime5 = schedule.getThursday().toString();
        this.startTime6 = schedule.getFriday().toString();
        this.startTime7 = schedule.getShabbos().toString();
        this.startTimeRC = schedule.getRoshChodesh().toString();
        this.startTimeYT = schedule.getYomTov().toString();
        this.startTimeCH = schedule.getChanuka().toString();
        this.startTimeCHRC = schedule.getRoshChodeshChanuka().toString();
        this.schedule = schedule;
        this.enabled = enabled;
        this.notes = notes;
        this.nusachString = nusach.toString();
        this.orgColor = orgColor;
        this.whatsapp = whatsapp;
    }

    public Minyan(String id, Organization organization, MinyanType type, Location location, Schedule schedule, String notes, Nusach nusach, boolean enabled, String orgColor, String whatsapp) {
        super.id = id;
        this.minyanTypeString = type.toString();
        this.minyanType = type;
        if (location != null) {
            this.locationId = location.getId();
            this.location = location;
        }
        this.organizationId = organization.getId();
        this.organization = organization;
        this.startTime1 = schedule.getSunday().toString();
        this.startTime2 = schedule.getMonday().toString();
        this.startTime3 = schedule.getTuesday().toString();
        this.startTime4 = schedule.getWednesday().toString();
        this.startTime5 = schedule.getThursday().toString();
        this.startTime6 = schedule.getFriday().toString();
        this.startTime7 = schedule.getShabbos().toString();
        this.startTimeRC = schedule.getRoshChodesh().toString();
        this.startTimeYT = schedule.getYomTov().toString();
        this.startTimeCH = schedule.getChanuka().toString();
        this.startTimeCHRC = schedule.getRoshChodeshChanuka().toString();
        this.schedule = schedule;
        this.enabled = enabled;
        this.notes = notes;
        this.nusachString = nusach.toString();
        this.orgColor = orgColor;
        this.whatsapp = whatsapp;
    }

    public String getMinyanTypeString() {
        return minyanTypeString;
    }

    public MinyanType getType() {
        return minyanType;
    }

    public String getLocationId() {
        return locationId;
    }

    public Location getLocation() {
        return location;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public Organization getOrganization() {
//        if (organization == null) {
//            organization = organizationDAO.findById(organizationId);
//        }
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

    public String getStartTimeYT() {
        return startTimeYT;
    }

    public String getStartTimeCH() {
        return startTimeCH;
    }

    public String getStartTimeCHRC() {
        return startTimeCHRC;
    }
    
    public Schedule getSchedule() {
        return schedule;
    }

    public String getNotes() {
        return notes;
    }

    public String getNusachString() {
        return nusachString;
    }

    public Nusach getNusach() {
        return nusach;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getOrgColor() {
        return orgColor;
    }

    public String getWhatsapp() {
        return whatsapp;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

//    public [Date] getStartTimes(int next)

    public Date getStartDateFromNow() {
        return getStartDate(LocalDate.now());
    }

    public Date getStartDate(LocalDate date) {
//        need to check if that date is a special day
        MinyanTime mt = getMinyanTime(date);
        Time t = mt.getTime(date);
        if (t == null) {
            return null;
        }
        LocalDate temp = date.minusMonths(1).minusYears(1900);
        return new Date(temp.getYear(), temp.getMonthValue(), date.getDayOfMonth(), t.getHours(), t.getMinutes(), t.getSeconds());
    }

    public Time getStartTime() {
        return getStartTime(LocalDate.now());
    }

    public Time getStartTime(LocalDate date) {
//        need to check if that date is a special day
        MinyanTime mt = getMinyanTime(date);
        return mt.getTime(date);
    }

    public MinyanTime getMinyanTime() {
        return getMinyanTime(LocalDate.now());
    }

    public MinyanTime getMinyanTime(LocalDate date) {
        LocalDate temp = date;
        JewishCalendar jc = new JewishCalendar(temp);
        if (jc.isRoshChodesh()) {
            if (jc.isChanukah()) {
                return schedule.getRoshChodeshChanuka();
            } else {
                return schedule.getRoshChodesh();
            }
        } else if (jc.isChanukah()) {
            return schedule.getChanuka();
        } else if (jc.isYomTovAssurBemelacha()) {
            return schedule.getYomTov();
        } else {
            return switch (temp.getDayOfWeek()) {
                case SUNDAY -> schedule.getSunday();
                case MONDAY -> schedule.getMonday();
                case TUESDAY -> schedule.getTuesday();
                case WEDNESDAY -> schedule.getWednesday();
                case THURSDAY -> schedule.getThursday();
                case FRIDAY -> schedule.getFriday();
                case SATURDAY -> schedule.getShabbos();
            };
        }
    }
}