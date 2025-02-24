package com.tbdev.teaneckminyanim.minyan;

import com.kosherjava.zmanim.util.Time;
import com.tbdev.teaneckminyanim.service.ZmanimHandler;
import com.tbdev.teaneckminyanim.enums.Zman;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Date;
import java.util.Locale;

class TimeRule {
    private Zman zman;
    private Integer offsetMinutes;
    protected Boolean rounded; 

    public TimeRule(Zman zman, Integer offsetMinutes) {
        this.zman = zman;
        this.offsetMinutes = offsetMinutes;
        this.rounded = false; //if used the "old" constrctor we are going to assume that classic offset minyan
    }

    public TimeRule(Zman zman, Integer offsetMinutes, Boolean rounded) {
        this.zman = zman;
        this.offsetMinutes = offsetMinutes;
        this.rounded = rounded; 
    }

    public Zman getZman() {
        return zman;
    }

    public Integer getOffsetMinutes() {
        return offsetMinutes;
    }

    public Time getTime(LocalDate date) {
        ZmanimHandler zmanimHandler = new ZmanimHandler();
        LocalDate temp = date;
        Date zmanTime = zmanimHandler.getZmanim(temp).get(zman);
        Time t = null; 
//        TODO: DEAL WITH DEPRECATED FUNCTIONS
        if(!rounded){
            t = new Time(zmanTime.getHours(), zmanTime.getMinutes() + offsetMinutes, zmanTime.getSeconds() + 59, 0);
        } else {
            LocalDate sunday = temp.with(WeekFields.of(Locale.US).dayOfWeek(), 1);
            LocalDate monday = temp.with(WeekFields.of(Locale.US).dayOfWeek(), 2);
            LocalDate tuesday = temp.with(WeekFields.of(Locale.US).dayOfWeek(), 3);
            LocalDate wednesday = temp.with(WeekFields.of(Locale.US).dayOfWeek(), 4);
            LocalDate thursday = temp.with(WeekFields.of(Locale.US).dayOfWeek(), 5);
            LocalDate friday = temp.with(WeekFields.of(Locale.US).dayOfWeek(), 6);
            LocalDate[] ldArray = {sunday, monday, tuesday, wednesday, thursday, friday}; 
            Date min = zmanimHandler.getZmanim(temp).get(zman); 
            Date cur = null; 

            for (LocalDate ld : ldArray){
                cur = zmanimHandler.getZmanim(ld).get(zman);
                if(cur.compareTo(min)<0) min=cur; //compareTo return negative value if first date is before
            }
            int minutes =  ((min.getMinutes() + offsetMinutes)/5) * 5; //rounds down to the nearest 5
            t = new Time(min.getHours(), minutes, 0, 0);
        }
        
        return t;
    }
}