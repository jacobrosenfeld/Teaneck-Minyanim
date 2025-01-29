package com.tbdev.teaneckminyanim.model;

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;
import com.kosherjava.zmanim.util.Time;
import com.tbdev.teaneckminyanim.enums.Nusach;
import com.tbdev.teaneckminyanim.minyan.MinyanTime;
import com.tbdev.teaneckminyanim.minyan.MinyanType;
import com.tbdev.teaneckminyanim.minyan.Schedule;
import com.tbdev.teaneckminyanim.tools.IDGenerator;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDate;
import java.util.Date;

import static com.tbdev.teaneckminyanim.tools.IDGenerator.generateID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "Minyan")
public class Minyan {
    @Id
    @Column(name = "ID")
    private String id;

    @Column(name = "TYPE", nullable = false)
    @Enumerated(EnumType.STRING)
    private MinyanType type;

    @Column(name = "LOCATION_ID", nullable = false)
    private String locationId;

    @Column(name = "ORGANIZATION_ID", nullable = false)
    private String organizationId;

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

    @Transient
    private Schedule schedule;

    @Column(name = "NOTES")
    private String notes;

    @Column(name = "NUSACH", nullable = false)
    @Enumerated(EnumType.STRING)
    private Nusach nusach;

    @Column(name = "WHATSAPP")
    private String whatsapp;

    @Transient
    private String orgColor;

    @Transient
    private String nusachStr;


    public Minyan(String id,
                  MinyanType type,
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
                  Nusach nusach,
                  String orgColor,
                  String whatsapp) {
        this.id = id;
        this.type = type;
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
        this.notes = notes;
        this.nusach = nusach;
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
                  String nusachStr,
                  String orgColor,
                  String whatsapp) {
        this.id = generateID('M');
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
        this.notes = notes;
        this.nusachStr = nusachStr;
        this.orgColor = orgColor;
        this.whatsapp = whatsapp;
    }

    public Minyan(Organization organization, MinyanType type, Location location, Schedule schedule, String notes, String nusachStr, boolean enabled, String orgColor, String whatsapp) {
        this.id = generateID('M');
        this.type = type;
        this.locationId = location.getId();
        this.organizationId = organization.getId();
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
        this.nusachStr = nusachStr;
        this.orgColor = orgColor;
        this.whatsapp = whatsapp;
    }

    public Minyan(String id, Organization organization, MinyanType type, Location location, Schedule schedule, String notes, Nusach nusach, boolean enabled, String orgColor, String whatsapp) {
        this.id = id;
        this.type = type;
        this.locationId = location.getId();
        this.organizationId = organization.getId();
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
        this.nusach = nusach;
        this.orgColor = orgColor;
        this.whatsapp = whatsapp;
    }

    // Additional methods for getting start times and handling dates
    public Date getStartDateFromNow() {
        return getStartDate(LocalDate.now());
    }

    public Date getStartDate(LocalDate date) {
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
                default -> null; // Handle unexpected cases
            };
        }
    }
}