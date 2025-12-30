package com.tbdev.teaneckminyanim.service;

import com.kosherjava.zmanim.ComplexZmanimCalendar;
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter;
import com.kosherjava.zmanim.hebrewcalendar.JewishDate;
import com.kosherjava.zmanim.util.GeoLocation;
import com.tbdev.teaneckminyanim.enums.Zman;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.TimeZone;

@Slf4j
@Service
public class ZmanimHandler {
    private final ApplicationSettingsService settingsService;
    private GeoLocation geoLocation;

    @Autowired
    public ZmanimHandler(ApplicationSettingsService settingsService) {
        this.settingsService = settingsService;
        this.geoLocation = null; // Will be lazily initialized
    }

    /**
     * No-arg constructor for direct instantiation (fallback to defaults).
     * Used by TimeRule and other legacy code paths.
     */
    public ZmanimHandler() {
        this.settingsService = null;
        this.geoLocation = null; // Will use fallback defaults
    }

    /**
     * Constructor for testing with explicit GeoLocation.
     */
    public ZmanimHandler(GeoLocation geoLocation) {
        this.settingsService = null;
        this.geoLocation = geoLocation;
    }

    /**
     * Get GeoLocation from settings (lazy initialization).
     */
    private GeoLocation getGeoLocation() {
        if (geoLocation == null) {
            if (settingsService != null) {
                geoLocation = settingsService.getGeoLocation();
            } else {
                // Fallback for tests or when settings service is not available
                TimeZone timeZone = TimeZone.getTimeZone("Asia/Jerusalem");
                geoLocation = new GeoLocation("Jerusalem, Israel", 31.7683, 35.2137, 24, timeZone);
            }
        }
        return geoLocation;
    }

    public Dictionary<Zman, Date> getZmanimForNow() {
        return getZmanim(LocalDate.now());
    }

    public Dictionary<Zman, Date> getZmanim(LocalDate date) {
        Dictionary<Zman, Date> dictionary = new Hashtable();

        ComplexZmanimCalendar complexZmanimCalendar = new ComplexZmanimCalendar(getGeoLocation());
        complexZmanimCalendar.getCalendar().set(date.getYear(), date.getMonth().getValue() - 1, date.getDayOfMonth());

        dictionary.put(Zman.ALOS_HASHACHAR, complexZmanimCalendar.getAlosHashachar());
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

        log.info("Current date: " + now);

        jewishCalendar.setGregorianDate(now.getYear(), now.getMonthValue() - 1, now.getDayOfMonth());

        boolean result = jewishCalendar.isAseresYemeiTeshuva();

        log.info("Is Aseres Yemei Teshuva: " + result);

        return result;
    }
    public boolean isSelichosRecited(LocalDate date) {
        JewishCalendar jewishCalendar = new JewishCalendar();
        jewishCalendar.setGregorianDate(date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth());
        
        // Check if the date is within Aseres Yemei Teshuva
        if (jewishCalendar.isAseresYemeiTeshuva()) {
            System.out.println("Date " + date + " is within Aseres Yemei Teshuva.");
            return true;
        }
    
        // Determine the date of Rosh HaShana for the current or next Jewish year
        JewishCalendar roshHashana = new JewishCalendar(jewishCalendar.getJewishYear(), JewishDate.TISHREI, 1);
        LocalDate roshHashanaDate = roshHashana.getGregorianCalendar().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        System.out.println("Rosh HaShana Date: " + roshHashanaDate);
        
        if (date.isAfter(roshHashanaDate)) {
            roshHashana = new JewishCalendar(jewishCalendar.getJewishYear() + 1, JewishDate.TISHREI, 1);
            roshHashanaDate = roshHashana.getGregorianCalendar().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            System.out.println("Next Year Rosh HaShana Date: " + roshHashanaDate);
        }
    
        // Determine the day of the week for Rosh HaShana
        int roshHashanaDayOfWeek = roshHashana.getDayOfWeek();
        System.out.println("Rosh HaShana Day of Week: " + roshHashanaDayOfWeek);
    
        // Determine the start date for Selichos
        LocalDate selichosStartDate;
        if (roshHashanaDayOfWeek == Calendar.MONDAY || roshHashanaDayOfWeek == Calendar.TUESDAY) {
            // Start from two Sundays before Rosh HaShana
            selichosStartDate = roshHashanaDate.minusWeeks(2).with(DayOfWeek.SUNDAY);
        } else {
            // Start from the Sunday before Rosh HaShana
            selichosStartDate = roshHashanaDate.minusWeeks(1).with(DayOfWeek.SUNDAY);
        }
        System.out.println("Selichos Start Date: " + selichosStartDate);
    
        // Check if the given date is on or after the start date for Selichos
        boolean result = !date.isBefore(selichosStartDate);
        System.out.println("Is Selichos Recited on " + date + ": " + result);
        return result;
    }
}
