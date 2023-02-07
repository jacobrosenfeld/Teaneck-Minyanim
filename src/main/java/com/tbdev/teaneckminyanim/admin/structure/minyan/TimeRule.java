package com.tbdev.teaneckminyanim.admin.structure.minyan;

import com.kosherjava.zmanim.util.Time;
import com.tbdev.teaneckminyanim.front.ZmanimHandler;
import com.tbdev.teaneckminyanim.global.Zman;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Date;

class TimeRule {
    private Zman zman;
    private Integer offsetMinutes;
    private Boolean rounded; 

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
        } else{
            LocalDate sunday = temp.with(DayOfWeek.SUNDAY);
            LocalDate monday = sunday.plusDays(1);
            LocalDate tuesday = sunday.plusDays(2);
            LocalDate wednesday = sunday.plusDays(3);
            LocalDate thursday = sunday.plusDays(4);
            LocalDate friday = sunday.plusDays(5);
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