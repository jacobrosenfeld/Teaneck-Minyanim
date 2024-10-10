package com.tbdev.teaneckminyanim.front;

import com.kosherjava.zmanim.ComplexZmanimCalendar;
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter;
import com.kosherjava.zmanim.hebrewcalendar.JewishDate;
import com.kosherjava.zmanim.util.GeoLocation;
import com.tbdev.teaneckminyanim.global.Zman;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.TimeZone;
import java.util.logging.Logger;

public class ZmanimHandler {
    private GeoLocation geoLocation;
    private static final Logger logger = Logger.getLogger(ZmanimHandler.class.getName());

    public ZmanimHandler(GeoLocation geoLocation) {
        this.geoLocation = geoLocation;
    }

    public ZmanimHandler() {
        TimeZone timeZone = TimeZone.getTimeZone("America/New_York");
        String locationName = "Teaneck, NJ";
        double latitude = 40.906871;
        double longitude = -74.020924;
        double elevation = 24;
        GeoLocation geoLocation = new GeoLocation(locationName, latitude, longitude, elevation, timeZone);
        this.geoLocation = geoLocation;
    }

    public Dictionary<Zman, Date> getZmanimForNow() {
        return getZmanim(LocalDate.now());
    }

    public Dictionary<Zman, Date> getZmanim(LocalDate date) {
        Dictionary<Zman, Date> dictionary = new Hashtable();

        ComplexZmanimCalendar complexZmanimCalendar = new ComplexZmanimCalendar(geoLocation);
        complexZmanimCalendar.getCalendar().set(date.getYear(), date.getMonth().getValue() - 1, date.getDayOfMonth());

        dictionary.put(Zman.ALOT_HASHACHAR, complexZmanimCalendar.getAlosHashachar());
        dictionary.put(Zman.ETT, complexZmanimCalendar.getBeginCivilTwilight());
        dictionary.put(Zman.MISHEYAKIR, complexZmanimCalendar.getMisheyakir7Point65Degrees());
        dictionary.put(Zman.NETZ, complexZmanimCalendar.getSunrise());
        dictionary.put(Zman.SZKS, complexZmanimCalendar.getSofZmanShmaGRA());
        dictionary.put(Zman.MASZKS, complexZmanimCalendar.getSofZmanShmaMGA());
        dictionary.put(Zman.SZT, complexZmanimCalendar.getSofZmanTfilaGRA());
        dictionary.put(Zman.MASZT, complexZmanimCalendar.getSofZmanTfilaMGA());
        dictionary.put(Zman.CHATZOS, complexZmanimCalendar.getChatzos());
        dictionary.put(Zman.MINCHA_GEDOLA, complexZmanimCalendar.getMinchaGedola());
        dictionary.put(Zman.MINCHA_KETANA, complexZmanimCalendar.getMinchaKetana());
        dictionary.put(Zman.PLAG_HAMINCHA, complexZmanimCalendar.getPlagHamincha());
        dictionary.put(Zman.SHEKIYA, complexZmanimCalendar.getSunset());
        dictionary.put(Zman.EARLIEST_SHEMA, complexZmanimCalendar.getTzaisGeonim5Point88Degrees());
        dictionary.put(Zman.TZES, complexZmanimCalendar.getTzais());
        dictionary.put(Zman.CHATZOS_LAILA, complexZmanimCalendar.getSolarMidnight());

        return dictionary;
    }

    public String getHebrewDate(Date date) {
        JewishDate jd = new JewishDate(date);
        HebrewDateFormatter hdf = new HebrewDateFormatter();
        hdf.setHebrewFormat(true);
        return hdf.format(jd);
    }

    public String getTodaysHebrewDate() {
        return getHebrewDate(new Date());
    }

    public boolean isAseresYemeiTeshuva() {
        JewishCalendar jewishCalendar = new JewishCalendar();
        LocalDate now = LocalDate.now();
        
        logger.info("Current date: " + now);
        
        jewishCalendar.setGregorianDate(now.getYear(), now.getMonthValue() - 1, now.getDayOfMonth());
        
        boolean result = jewishCalendar.isAseresYemeiTeshuva();
        
        logger.info("Is Aseres Yemei Teshuva: " + result);
        
        return result;
    }

    public boolean isSelichosRecited(LocalDate date) {
        JewishCalendar jewishCalendar = new JewishCalendar();
        jewishCalendar.setGregorianDate(date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth());
        
        logger.info("Checking date: " + date);
        
        // Check if the date is within Aseres Yemei Teshuva
        boolean isAseresYemeiTeshuva = jewishCalendar.isAseresYemeiTeshuva();
        logger.info("isAseresYemeiTeshuva method called: " + isAseresYemeiTeshuva);
        
        if (isAseresYemeiTeshuva) {
            logger.info("Date is within Aseres Yemei Teshuva");
            return true;
        }
    
        // Determine the date of Rosh HaShana for the current or next Jewish year
        JewishCalendar roshHashana = new JewishCalendar(jewishCalendar.getJewishYear(), JewishCalendar.TISHREI, 1);
        LocalDate roshHashanaDate = roshHashana.getGregorianCalendar().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        
        logger.info("Rosh HaShana date: " + roshHashanaDate);
        
        if (date.isAfter(roshHashanaDate)) {
            roshHashana = new JewishCalendar(jewishCalendar.getJewishYear() + 1, JewishCalendar.TISHREI, 1);
            roshHashanaDate = roshHashana.getGregorianCalendar().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            logger.info("Updated Rosh HaShana date for next year: " + roshHashanaDate);
        }
    
        // Determine the day of the week for Rosh HaShana
        int roshHashanaDayOfWeek = roshHashana.getDayOfWeek();
        logger.info("Rosh HaShana day of week: " + roshHashanaDayOfWeek);
    
        // Determine the start date for Selichos
        LocalDate selichosStartDate;
        if (roshHashanaDayOfWeek == Calendar.MONDAY || roshHashanaDayOfWeek == Calendar.TUESDAY) {
            // Start from two Sundays before Rosh HaShana
            selichosStartDate = roshHashanaDate.minusWeeks(2).with(DayOfWeek.SUNDAY);
        } else {
            // Start from the Sunday before Rosh HaShana
            selichosStartDate = roshHashanaDate.minusWeeks(1).with(DayOfWeek.SUNDAY);
        }
        logger.info("Selichos start date: " + selichosStartDate);
        
        // Check if the given date is on or after the start date for Selichos
        boolean result = !date.isBefore(selichosStartDate);
        logger.info("Is Selichos recited: " + result);
        
        return result;
    }
}
