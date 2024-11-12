package com.tbdev.teaneckminyanim.minyan;

import java.util.HashMap;

public class Schedule {
    private MinyanTime sunday;
    private MinyanTime monday;
    private MinyanTime tuesday;
    private MinyanTime wednesday;
    private MinyanTime thursday;
    private MinyanTime friday;
    private MinyanTime shabbos;
    private MinyanTime roshChodesh;
    private MinyanTime yomTov;
    private MinyanTime chanuka;
    private MinyanTime roshChodeshChanuka;

    public Schedule(String sunday, String monday, String tuesday, String wednesday, String thursday, String friday, String shabbos, String roshChodesh, String yomTov, String chanuka, String roshChodeshChanuka) {
        this.sunday = new MinyanTime(sunday);
        this.monday = new MinyanTime(monday);
        this.tuesday = new MinyanTime(tuesday);
        this.wednesday = new MinyanTime(wednesday);
        this.thursday = new MinyanTime(thursday);
        this.friday = new MinyanTime(friday);
        this.shabbos = new MinyanTime(shabbos);
        this.roshChodesh = new MinyanTime(roshChodesh);
        this.yomTov = new MinyanTime(yomTov);
        this.chanuka = new MinyanTime(chanuka);
        this.roshChodeshChanuka = new MinyanTime(roshChodeshChanuka);
    }

    public Schedule(MinyanTime sunday, MinyanTime monday, MinyanTime tuesday, MinyanTime wednesday, MinyanTime thursday, MinyanTime friday, MinyanTime shabbos, MinyanTime roshChodesh, MinyanTime yomTov, MinyanTime chanuka, MinyanTime roshChodeshChanuka) {
        this.sunday = sunday;
        this.monday = monday;
        this.tuesday = tuesday;
        this.wednesday = wednesday;
        this.thursday = thursday;
        this.friday = friday;
        this.shabbos = shabbos;
        this.roshChodesh = roshChodesh;
        this.yomTov = yomTov;
        this.chanuka = chanuka;
        this.roshChodeshChanuka = roshChodeshChanuka;
    }

    public MinyanTime getSunday() {
        return sunday;
    }

    public MinyanTime getMonday() {
        return monday;
    }

    public MinyanTime getTuesday() {
        return tuesday;
    }

    public MinyanTime getWednesday() {
        return wednesday;
    }

    public MinyanTime getThursday() {
        return thursday;
    }

    public MinyanTime getFriday() {
        return friday;
    }

    public MinyanTime getShabbos() {
        return shabbos;
    }

    public MinyanTime getRoshChodesh() {
        return roshChodesh;
    }

    public MinyanTime getYomTov() {
        return yomTov;
    }

    public MinyanTime getChanuka() {
        return chanuka;
    }

    public MinyanTime getRoshChodeshChanuka() {
        return roshChodeshChanuka;
    }

    public HashMap<MinyanDay, MinyanTime> getMappedSchedule() {
        HashMap<MinyanDay, MinyanTime> schedule = new HashMap<>();
        schedule.put(MinyanDay.SUNDAY, sunday);
        schedule.put(MinyanDay.MONDAY, monday);
        schedule.put(MinyanDay.TUESDAY, tuesday);
        schedule.put(MinyanDay.WEDNESDAY, wednesday);
        schedule.put(MinyanDay.THURSDAY, thursday);
        schedule.put(MinyanDay.FRIDAY, friday);
        schedule.put(MinyanDay.SHABBOS, shabbos);
        schedule.put(MinyanDay.ROSH_CHODESH, roshChodesh);
        schedule.put(MinyanDay.YOM_TOV, yomTov);
        schedule.put(MinyanDay.CHANUKA, chanuka);
        schedule.put(MinyanDay.ROSH_CHODESH_CHANUKA, roshChodeshChanuka);

        return schedule;
    }

//    public MinyanTime nearestTimeToNow() {
//        HashMap<MinyanDay, MinyanTime> schedule = getMappedSchedule();
//        for (MinyanDay minyanDay : schedule.keySet()) {
//            MinyanTime time = schedule.get(minyanDay);
//
//            minyanDay
//
//            if (time.isFixed()) {
//                Time fixedTime = time.getTime();
//
//
////                Date nextDate = new Date(""
//            }
//        }
//    }

    public void printTimes() {
        System.out.println("Sunday minyan time: " + sunday);
        System.out.println("Monday minyan time: " + monday);
        System.out.println("Tuesday minyan time: " + tuesday);
        System.out.println("Wednesday minyan time: " + wednesday);
        System.out.println("Thursday minyan time: " + thursday);
        System.out.println("Friday minyan time: " + friday);
        System.out.println("Shabbos minyan time: " + shabbos);
        System.out.println("Rosh Chodesh minyan time: " + roshChodesh);
        System.out.println("Yom Tov minyan time: " + yomTov);
        System.out.println("Chanuka minyan time: " + chanuka);
        System.out.println("RCC minyan time: " + roshChodeshChanuka);
    }
}
