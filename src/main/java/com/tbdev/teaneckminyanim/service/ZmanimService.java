package com.tbdev.teaneckminyanim.service;

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;
import com.kosherjava.zmanim.util.GeoLocation;
import com.tbdev.teaneckminyanim.admin.structure.location.Location;
import com.tbdev.teaneckminyanim.admin.structure.location.LocationDAO;
import com.tbdev.teaneckminyanim.admin.structure.minyan.Minyan;
import com.tbdev.teaneckminyanim.admin.structure.minyan.MinyanDAO;
import com.tbdev.teaneckminyanim.admin.structure.organization.Organization;
import com.tbdev.teaneckminyanim.admin.structure.organization.OrganizationDAO;
import com.tbdev.teaneckminyanim.admin.structure.settings.TNMSettings;
import com.tbdev.teaneckminyanim.admin.structure.settings.TNMSettingsDAO;
import com.tbdev.teaneckminyanim.front.KolhaMinyanim;
import com.tbdev.teaneckminyanim.front.MinyanEvent;
import com.tbdev.teaneckminyanim.front.ZmanimHandler;
import com.tbdev.teaneckminyanim.global.Nusach;
import com.tbdev.teaneckminyanim.global.Zman;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.ModelAndView;

import java.text.SimpleDateFormat;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZmanimService {
    TimeZone timeZone = TimeZone.getTimeZone("America/New_York");

    String locationName = "Teaneck, NJ";
    double latitude = 40.906871;
    double longitude = -74.020924;
    double elevation = 24;
    GeoLocation geoLocation = new GeoLocation(locationName, latitude, longitude, elevation, timeZone);

    Calendar calendar = Calendar.getInstance();
    SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM d, yyyy | h:mm aa");
    SimpleDateFormat onlyDateFormat = new SimpleDateFormat("EEEE, MMMM d");
    SimpleDateFormat strippedDayFormat = new SimpleDateFormat("MMMM d");
    SimpleDateFormat timeFormatSec = new SimpleDateFormat("h:mm:ss aa");
    SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm aa");

    ZmanimHandler zmanimHandler = new ZmanimHandler(geoLocation);

    private final LocationDAO locationDAO;
    private final OrganizationDAO organizationDAO;
    private final MinyanDAO minyanDAO;

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

        log.info("Checking date: " + date);

        // Check if the date is within Aseres Yemei Teshuva
        boolean isAseresYemeiTeshuva = jewishCalendar.isAseresYemeiTeshuva();
        log.info("isAseresYemeiTeshuva method called: " + isAseresYemeiTeshuva);

        if (isAseresYemeiTeshuva) {
            log.info("Date is within Aseres Yemei Teshuva");
            return true;
        }

        // Determine the date of Rosh HaShana for the current or next Jewish year
        JewishCalendar roshHashana = new JewishCalendar(jewishCalendar.getJewishYear(), JewishCalendar.TISHREI, 1);
        LocalDate roshHashanaDate = roshHashana.getGregorianCalendar().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

        // log.info("Rosh HaShana date: " + roshHashanaDate);

        if (date.isAfter(roshHashanaDate)) {
            roshHashana = new JewishCalendar(jewishCalendar.getJewishYear() + 1, JewishCalendar.TISHREI, 1);
            roshHashanaDate = roshHashana.getGregorianCalendar().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            // log.info("Updated Rosh HaShana date for next year: " + roshHashanaDate);
        }

        // Determine the day of the week for Rosh HaShana
        int roshHashanaDayOfWeek = roshHashana.getDayOfWeek();
        // log.info("Rosh HaShana day of week: " + roshHashanaDayOfWeek);

        // Determine the start date for Selichos
        LocalDate selichosStartDate;
        if (roshHashanaDayOfWeek == Calendar.MONDAY || roshHashanaDayOfWeek == Calendar.TUESDAY) {
            // Start from two Sundays before Rosh HaShana
            selichosStartDate = roshHashanaDate.minusWeeks(2).with(DayOfWeek.SUNDAY);
        } else {
            // Start from the Sunday before Rosh HaShana
            selichosStartDate = roshHashanaDate.minusWeeks(1).with(DayOfWeek.SUNDAY);
        }
        log.info("Selichos start date: " + selichosStartDate);

        // Check if the given date is on or after the start date for Selichos
        boolean result = !date.isBefore(selichosStartDate);
        log.info("Is Selichos recited: " + result);

        return result;
    }

    public ModelAndView getZmanim(Date date) {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("homepage");

        log.debug("DEBUG: Adding dates to model");

        // adding dates to model data
        setTimeZone(timeZone);
        // String month = calendar.getDisplayName(Calendar.MONTH, Calendar.LONG,
        // java.util.Locale.US);
        mv.getModel().put("date", dateFormat.format(date));
        mv.getModel().put("onlyDate", onlyDateFormat.format(date));

        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM d, yyyy h:mm a");
        Date datenow = new Date();
        String timenow = dateFormat.format(datenow);
        mv.getModel().put("timenow", timenow);

        Calendar c = Calendar.getInstance();

        // adds model data for tomorrow's date
        c.setTime(date);
        c.add(Calendar.DATE, 1);
        mv.getModel().put("tommorowOnlyDate", onlyDateFormat.format(c.getTime()));
        mv.getModel().put("tommorowStrippedDay", strippedDayFormat.format(c.getTime()));

        // adds model data for yesterday's date
        c.setTime(date);
        c.add(Calendar.DATE, -1);
        mv.getModel().put("yesterdayOnlyDate", onlyDateFormat.format(c.getTime()));
        mv.getModel().put("yesterdayStrippedDay", strippedDayFormat.format(c.getTime()));

        Date today = new Date();
        mv.getModel().put("isToday", onlyDateFormat.format(date).equals(onlyDateFormat.format(today)));

        mv.getModel().put("dateString", date.toString());

        // add today's hebrew date
        mv.getModel().put("hebrewDate", zmanimHandler.getHebrewDate(date));

        log.debug(": Fetching zmanim for model");

        LocalDate localDate = dateToLocalDate(date);
        log.info("Showing zmanim for date: " + localDate.getMonth() + ":" + localDate.getMonthValue() + ":"
                + localDate.getMonth().getValue() + ":" + localDate.toString());

        Dictionary<Zman, Date> zmanim = zmanimHandler.getZmanim(localDate);
        Dictionary<Zman, Date> zmanimtoday = zmanimHandler.getZmanimForNow();

        log.debug(": Putting zmanim in model");

        log.info("ALOS HASH: " + zmanim.get(Zman.ALOS_HASHACHAR));
        mv.getModel().put("alotHashachar", timeFormatWithRoundingToSecond(zmanim.get(Zman.ALOS_HASHACHAR)));
        mv.getModel().put("ett", timeFormatWithRoundingToSecond(zmanim.get(Zman.ETT)));
        mv.getModel().put("misheyakir", timeFormatWithRoundingToSecond(zmanim.get(Zman.MISHEYAKIR)));
        mv.getModel().put("netz", timeFormatWithRoundingToSecond(zmanim.get(Zman.NETZ)));
        mv.getModel().put("szks", timeFormatWithRoundingToSecond(zmanim.get(Zman.SZKS)));
        mv.getModel().put("maszks", timeFormatWithRoundingToSecond(zmanim.get(Zman.MASZKS)));
        mv.getModel().put("szt", timeFormatWithRoundingToSecond(zmanim.get(Zman.SZT)));
        mv.getModel().put("maszt", timeFormatWithRoundingToSecond(zmanim.get(Zman.MASZT)));
        mv.getModel().put("chatzos", timeFormatWithRoundingToSecond(zmanim.get(Zman.CHATZOS)));
        mv.getModel().put("minchaGedola", timeFormatWithRoundingToSecond(zmanim.get(Zman.MINCHA_GEDOLA)));
        mv.getModel().put("minchaKetana", timeFormatWithRoundingToSecond(zmanim.get(Zman.MINCHA_KETANA)));
        mv.getModel().put("plagHamincha", timeFormatWithRoundingToSecond(zmanim.get(Zman.PLAG_HAMINCHA)));
        mv.getModel().put("shekiya", timeFormatWithRoundingToSecond(zmanim.get(Zman.SHEKIYA)));
        mv.getModel().put("earliestShema", timeFormatWithRoundingToSecond(zmanim.get(Zman.EARLIEST_SHEMA)));
        mv.getModel().put("tzes", timeFormatWithRoundingToSecond(zmanim.get(Zman.TZES)));

        log.debug(": Fetching minyanim");

        // get minyanim closest in time to now
        // todo: only get items with non null time for date
        List<Minyan> enabledMinyanim = minyanDAO.getEnabled();
        List<MinyanEvent> minyanEvents = new ArrayList<>();

        log.debug(": Filtering through minyanim");

        for (Minyan minyan : enabledMinyanim) {
            LocalDate ref = dateToLocalDate(date);
            Date startDate = minyan.getStartDate(ref);
            Date now = new Date();
            Date terminationDate = new Date(now.getTime() - (60000 * 8));
            log.info("SD: " + startDate);
            log.info("TD: " + terminationDate);
            Calendar shekiyaMinusOneMinute = Calendar.getInstance();
            shekiyaMinusOneMinute.setTime(zmanim.get(Zman.SHEKIYA));
            shekiyaMinusOneMinute.add(Calendar.MINUTE, -1);
            Calendar mgMinusOneMinute = Calendar.getInstance();
            mgMinusOneMinute.setTime(zmanim.get(Zman.MINCHA_GEDOLA));
            mgMinusOneMinute.add(Calendar.MINUTE, -1);
            // if (startDate != null && (startDate.after(terminationDate) || now.getDate()
            // != startDate.getDate())) {
            // if (startDate != null && (startDate.after(terminationDate))) {
            // start date must be valid AND (be after the termination date OR date must not
            // be the same date as today, to disregard the termination time when the user is
            // looking ahead)
            if (startDate != null && (startDate.after(terminationDate) || !sameDayOfMonth(now, date))) {
                // show the minyan
                String organizationName;
                Nusach organizationNusach;
                String organizationId;
                String organizationColor = minyan.getOrgColor();
                Organization organization = minyan.getOrganization();
                if (organization == null) {
                    Organization temp = organizationDAO.findById(minyan.getOrganizationId());
                    organizationName = temp.getName();
                    organizationNusach = temp.getNusach();
                    organizationId = temp.getId();
                    organizationColor = temp.getOrgColor();
                } else {
                    organizationName = organization.getName();
                    organizationNusach = organization.getNusach();
                    organizationId = organization.getId();
                    organizationColor = organization.getOrgColor();
                }

                String locationName = null;
                Location location = minyan.getLocation();
                if (location == null) {
                    location = locationDAO.findById(minyan.getLocationId());
                    if (location != null) {
                        locationName = location.getName();
                    }
                } else {
                    locationName = location.getName();
                }

                String dynamicDisplayName = minyan.getMinyanTime().dynamicDisplayName();
                String roundedDisplayName = minyan.getMinyanTime().roundedDisplayName();
                if (dynamicDisplayName != null) {
                    if (minyan.getType().isShacharis() && startDate.before(zmanim.get(Zman.SZT))
                            && startDate.after(zmanim.get(Zman.ALOS_HASHACHAR))) {
                        minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                organizationNusach, organizationId, locationName, startDate, dynamicDisplayName,
                                minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                    } else {
                        if (minyan.getType().isMincha() && startDate.before(zmanim.get(Zman.SHEKIYA))
                                && (startDate.after(mgMinusOneMinute.getTime())
                                || (startDate.equals(mgMinusOneMinute.getTime())))) {
                            minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                    organizationNusach, organizationId, locationName, startDate, dynamicDisplayName,
                                    minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                        } else {
                            if (minyan.getType().isMaariv() && (startDate.after(shekiyaMinusOneMinute.getTime())
                                    || startDate.equals((shekiyaMinusOneMinute.getTime())) || dynamicDisplayName.contains("Plag"))) {
                                minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                        organizationNusach, organizationId, locationName, startDate, dynamicDisplayName,
                                        minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                            }
                        }
                    }
                } else if (roundedDisplayName != null) {
                    if (minyan.getType().isShacharis() && startDate.before(zmanim.get(Zman.SZT))
                            && startDate.after(zmanim.get(Zman.ALOS_HASHACHAR))) {
                        minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                organizationNusach, organizationId, locationName, startDate, roundedDisplayName,
                                minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                    } else {
                        if (minyan.getType().isMincha() && startDate.before(zmanim.get(Zman.SHEKIYA))
                                && (startDate.after(mgMinusOneMinute.getTime())
                                || (startDate.equals(mgMinusOneMinute.getTime())))) {
                            minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                    organizationNusach, organizationId, locationName, startDate, roundedDisplayName,
                                    minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                        } else {
                            if (minyan.getType().isMaariv() && (startDate.after(shekiyaMinusOneMinute.getTime())
                                    || startDate.equals((shekiyaMinusOneMinute.getTime())) || roundedDisplayName.contains("plag"))) {
                                minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                        organizationNusach, organizationId, locationName, startDate, roundedDisplayName,
                                        minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                            }
                        }
                    }
                } else {
                    if (minyan.getType().isShacharis() && startDate.before(zmanim.get(Zman.SZT))
                            && startDate.after(zmanim.get(Zman.ALOS_HASHACHAR))) {
                        minyanEvents
                                .add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                        organizationNusach,
                                        organizationId, locationName, startDate, minyan.getNusach(),
                                        minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                    } else {
                        if (minyan.getType().isMincha() && startDate.before(zmanim.get(Zman.SHEKIYA))
                                && startDate.after(zmanim.get(Zman.MINCHA_GEDOLA))) {
                            minyanEvents
                                    .add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                            organizationNusach,
                                            organizationId, locationName, startDate, minyan.getNusach(),
                                            minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                        } else {
                            if (minyan.getType().isMaariv() && (startDate.after(shekiyaMinusOneMinute.getTime())
                                    || startDate.equals((shekiyaMinusOneMinute.getTime())))) {
                                minyanEvents
                                        .add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                                organizationNusach,
                                                organizationId, locationName, startDate, minyan.getNusach(),
                                                minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                            }
                        }
                    }
                }
            } /*
             * else {
             * if (startDate != null) {
             * System.out.println("Skipping minyan with start date: " +
             * startDate.toString());
             * } else {
             * System.out.println("Skipping minyan with null start date.");
             * }
             * }
             */
        }
        // KolhaMinyanim insertion
        List<KolhaMinyanim> kolhaMinyanims = new ArrayList<>();

        for (Minyan minyan : enabledMinyanim) {
            LocalDate ref = dateToLocalDate(date);
            Date startDate = minyan.getStartDate(ref);
            Date now = new Date();
            log.info("SD: " + startDate);
            if (startDate != null) {
                String organizationName;
                Nusach organizationNusach;
                String organizationId;
                String organizationColor = minyan.getOrgColor();
                Organization organization = minyan.getOrganization();
                if (organization == null) {
                    Organization temp = organizationDAO.findById(minyan.getOrganizationId());
                    organizationName = temp.getName();
                    organizationNusach = temp.getNusach();
                    organizationId = temp.getId();
                    organizationColor = temp.getOrgColor();
                } else {
                    organizationName = organization.getName();
                    organizationNusach = organization.getNusach();
                    organizationId = organization.getId();
                    organizationColor = organization.getOrgColor();
                }

                String locationName = null;
                Location location = minyan.getLocation();
                if (location == null) {
                    location = locationDAO.findById(minyan.getLocationId());
                    if (location != null) {
                        locationName = location.getName();
                    }
                } else {
                    locationName = location.getName();
                }

                String dynamicDisplayName = minyan.getMinyanTime().dynamicDisplayName();
                if (dynamicDisplayName != null) {
                    kolhaMinyanims.add(new KolhaMinyanim(minyan.getId(), minyan.getType(), organizationName,
                            organizationNusach, organizationId, locationName, startDate, dynamicDisplayName,
                            minyan.getNusach(), minyan.getNotes(), organizationColor));
                } else {
                    kolhaMinyanims.add(
                            new KolhaMinyanim(minyan.getId(), minyan.getType(), organizationName, organizationNusach,
                                    organizationId, locationName, startDate, minyan.getNusach(), minyan.getNotes(),
                                    organizationColor));
                }
            }
        }
        kolhaMinyanims.sort(Comparator.comparing(KolhaMinyanim::getStartTime));
        mv.getModel().put("kolminyanim", kolhaMinyanims);
        Stream<KolhaMinyanim> stream = kolhaMinyanims.stream();

        // Get the unique values based on the 'organizationId' property
        List<KolhaMinyanim> uniqueKolhaMinyanims = stream.filter(distinctByKey(KolhaMinyanim::getOrganizationId))
                .collect(Collectors.toList());

        mv.getModel().put("uniqueKolhaMinyanims", uniqueKolhaMinyanims);
        // end kol

        minyanEvents.sort(Comparator.comparing(MinyanEvent::getStartTime));
        mv.getModel().put("allminyanim", minyanEvents);

        List<MinyanEvent> shacharisMinyanim = new ArrayList<>();
        List<MinyanEvent> minchaMinyanim = new ArrayList<>();
        List<MinyanEvent> maarivMinyanim = new ArrayList<>();
        for (MinyanEvent me : minyanEvents) {
            if (me.getType().isShacharis()) {
                shacharisMinyanim.add(me);
            } else if (me.getType().isMincha()) {
                minchaMinyanim.add(me);
            } else if (me.getType().isMaariv()) {
                maarivMinyanim.add(me);
            }
        }
        mv.getModel().put("shacharisMinyanim", shacharisMinyanim);
        mv.getModel().put("minchaMinyanim", minchaMinyanim);
        mv.getModel().put("maarivMinyanim", maarivMinyanim);

        return mv;
    }

    public ModelAndView org(String orgId, Date date) throws Exception {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("org");

        dateFormat.setTimeZone(timeZone);

        mv.getModel().put("date", dateFormat.format(date));
        mv.getModel().put("onlyDate", onlyDateFormat.format(date));

        Calendar c = Calendar.getInstance();

        LocalDate localDate = dateToLocalDate(date);

        Dictionary<Zman, Date> zmanim = zmanimHandler.getZmanim(localDate);
        Dictionary<Zman, Date> zmanimtoday = zmanimHandler.getZmanimForNow();

        log.debug(": Putting zmanim in model");

        log.info("ALOS HASH: " + zmanim.get(Zman.ALOS_HASHACHAR));
        mv.getModel().put("alotHashachar", timeFormatWithRoundingToSecond(zmanim.get(Zman.ALOS_HASHACHAR)));
        mv.getModel().put("ETT", timeFormatWithRoundingToSecond(zmanim.get(Zman.ETT)));
        mv.getModel().put("netz", timeFormatWithRoundingToSecond(zmanim.get(Zman.NETZ)));
        mv.getModel().put("szks", timeFormatWithRoundingToSecond(zmanim.get(Zman.SZKS)));
        mv.getModel().put("szt", timeFormatWithRoundingToSecond(zmanim.get(Zman.SZT)));
        mv.getModel().put("chatzos", timeFormatWithRoundingToSecond(zmanim.get(Zman.CHATZOS)));
        mv.getModel().put("minchaGedola", timeFormatWithRoundingToSecond(zmanim.get(Zman.MINCHA_GEDOLA)));
        mv.getModel().put("minchaKetana", timeFormatWithRoundingToSecond(zmanim.get(Zman.MINCHA_KETANA)));
        mv.getModel().put("plagHamincha", timeFormatWithRoundingToSecond(zmanim.get(Zman.PLAG_HAMINCHA)));
        mv.getModel().put("shekiya", timeFormatWithRoundingToSecond(zmanim.get(Zman.SHEKIYA)));
        mv.getModel().put("earliestShema", timeFormatWithRoundingToSecond(zmanim.get(Zman.EARLIEST_SHEMA)));
        mv.getModel().put("tzes", timeFormatWithRoundingToSecond(zmanim.get(Zman.TZES)));

        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM d, yyyy h:mm a");
        Date datenow = new Date();
        String timenow = dateFormat.format(datenow);
        mv.getModel().put("timenow", timenow);

        c.setTime(date);
        c.add(Calendar.DATE, 1);
        mv.getModel().put("tommorowOnlyDate", onlyDateFormat.format(c.getTime()));
        mv.getModel().put("tommorowStrippedDay", strippedDayFormat.format(c.getTime()));

        c.setTime(date);
        c.add(Calendar.DATE, -1);
        mv.getModel().put("yesterdayOnlyDate", onlyDateFormat.format(c.getTime()));
        mv.getModel().put("yesterdayStrippedDay", strippedDayFormat.format(c.getTime()));

        Date today = new Date();
        mv.getModel().put("isToday", onlyDateFormat.format(date).equals(onlyDateFormat.format(today)));

        mv.getModel().put("dateString", date.toString());

        // add hebrew date
        mv.getModel().put("hebrewDate", zmanimHandler.getHebrewDate(date));

        try {
            Organization org = organizationDAO.findById(orgId);
            mv.addObject("org", org);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Sorry, there was an error finding the organization.");
        }

        List<Minyan> enabledMinyanim = minyanDAO.findEnabledMatching(orgId);
        List<MinyanEvent> minyanEvents = new ArrayList<>();
        // boolean usesLocations;
        // boolean nusachChanges;
        // Nusach lastNusach;
        // boolean usesNotes;

        for (Minyan minyan : enabledMinyanim) {
            LocalDate ref = dateToLocalDate(date);
            Date startDate = minyan.getStartDate(ref);
            // Date terminationDate = new Date((new Date()).getTime() - (60000 * 20));
            // if (startDate != null && startDate.after(terminationDate)) {
            Calendar shekiyaMinusOneMinute = Calendar.getInstance();
            shekiyaMinusOneMinute.setTime(zmanim.get(Zman.SHEKIYA));
            shekiyaMinusOneMinute.add(Calendar.MINUTE, -1);
            Calendar mgMinusOneMinute = Calendar.getInstance();
            mgMinusOneMinute.setTime(zmanim.get(Zman.MINCHA_GEDOLA));
            mgMinusOneMinute.add(Calendar.MINUTE, -1);
            if (startDate != null) {
                String organizationName;
                Nusach organizationNusach;
                String organizationId;
                Organization organization = minyan.getOrganization();
                String organizationColor = minyan.getOrgColor();
                boolean isSelichosRecited = isSelichosRecited(ref);
                if (organization == null) {
                    Organization temp = organizationDAO.findById(minyan.getOrganizationId());
                    organizationName = temp.getName();
                    organizationId = temp.getId();
                    organizationNusach = temp.getNusach();
                } else {
                    organizationName = organization.getName();
                    organizationId = organization.getId();
                    organizationNusach = organization.getNusach();
                    organizationColor = organization.getOrgColor();
                }

                String locationName = null;
                Location location = minyan.getLocation();
                if (location == null) {
                    location = locationDAO.findById(minyan.getLocationId());
                    if (location != null) {
                        locationName = location.getName();
                    }
                } else {
                    locationName = location.getName();
                }

                String dynamicDisplayName = minyan.getMinyanTime().dynamicDisplayName();
                String roundedDisplayName = minyan.getMinyanTime().roundedDisplayName();
                if (dynamicDisplayName != null) {
                    if (minyan.getType().isShacharis() && startDate.before(zmanim.get(Zman.SZT))
                            && startDate.after(zmanim.get(Zman.ALOS_HASHACHAR))) {
                        minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                organizationNusach, organizationId, locationName, startDate, dynamicDisplayName,
                                minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                    } else {
                        if (minyan.getType().isMincha() && startDate.before(zmanim.get(Zman.SHEKIYA))
                                && (startDate.after(mgMinusOneMinute.getTime())
                                || (startDate.equals(mgMinusOneMinute.getTime())))) {
                            minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                    organizationNusach, organizationId, locationName, startDate, dynamicDisplayName,
                                    minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                        } else {
                            if (minyan.getType().isMaariv() && (startDate.after(shekiyaMinusOneMinute.getTime())
                                    || startDate.equals((shekiyaMinusOneMinute.getTime())) || dynamicDisplayName.contains("Plag"))) {
                                minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                        organizationNusach, organizationId, locationName, startDate, dynamicDisplayName,
                                        minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                            }
                        }
                    }
                } else if (roundedDisplayName != null) {
                    if (minyan.getType().isShacharis() && startDate.before(zmanim.get(Zman.SZT))
                            && startDate.after(zmanim.get(Zman.ALOS_HASHACHAR))) {
                        minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                organizationNusach, organizationId, locationName, startDate, roundedDisplayName,
                                minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                    } else {
                        if (minyan.getType().isMincha() && startDate.before(zmanim.get(Zman.SHEKIYA))
                                && (startDate.after(mgMinusOneMinute.getTime())
                                || (startDate.equals(mgMinusOneMinute.getTime())))) {
                            minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                    organizationNusach, organizationId, locationName, startDate, roundedDisplayName,
                                    minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                        } else {
                            if (minyan.getType().isMaariv() && (startDate.after(shekiyaMinusOneMinute.getTime())
                                    || startDate.equals((shekiyaMinusOneMinute.getTime())) || roundedDisplayName.contains("plag"))) {
                                minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                        organizationNusach, organizationId, locationName, startDate, roundedDisplayName,
                                        minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                            }
                            else {
                                if (minyan.getType().isSelichos() && isSelichosRecited){
                                    minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                                organizationNusach, organizationId, locationName, startDate,
                                                roundedDisplayName,
                                                minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                                    }
                                }
                        }
                    }
                } else {
                    if (minyan.getType().isShacharis() && startDate.before(zmanim.get(Zman.SZT))
                            && startDate.after(zmanim.get(Zman.ALOS_HASHACHAR))) {
                        minyanEvents
                                .add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                        organizationNusach,
                                        organizationId, locationName, startDate, minyan.getNusach(),
                                        minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                    } else {
                        if (minyan.getType().isMincha() && startDate.before(zmanim.get(Zman.SHEKIYA))
                                && startDate.after(zmanim.get(Zman.MINCHA_GEDOLA))) {
                            minyanEvents
                                    .add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                            organizationNusach,
                                            organizationId, locationName, startDate, minyan.getNusach(),
                                            minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                        } else {
                            if (minyan.getType().isMaariv() && (startDate.after(shekiyaMinusOneMinute.getTime())
                                    || startDate.equals((shekiyaMinusOneMinute.getTime())))) {
                                minyanEvents
                                        .add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                                organizationNusach,
                                                organizationId, locationName, startDate, minyan.getNusach(),
                                                minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                            }
                        }
                    }
                }
            }
        }
        minyanEvents.sort(Comparator.comparing(MinyanEvent::getStartTime));
        mv.getModel().put("allminyanim", minyanEvents);

        List<MinyanEvent> shacharisMinyanim = new ArrayList<>();
        List<MinyanEvent> minchaMinyanim = new ArrayList<>();
        List<MinyanEvent> maarivMinyanim = new ArrayList<>();
        for (MinyanEvent me : minyanEvents) {
            if (me.getType().isShacharis()) {
                shacharisMinyanim.add(me);
            } else if (me.getType().isMincha()) {
                minchaMinyanim.add(me);
            } else if (me.getType().isMaariv()) {
                maarivMinyanim.add(me);
            }
        }

        // upcoming minyanim for org
        List<MinyanEvent> nextMinyan = new ArrayList<>();

        for (Minyan minyan : enabledMinyanim) {
            LocalDate ref = dateToLocalDate(today);
            Date startDate = minyan.getStartDate(ref);
            Date now = new Date();
            Date terminationDate = new Date(now.getTime() - (60000 * 3));
            Calendar shekiyaMinusOneMinute = Calendar.getInstance();
            shekiyaMinusOneMinute.setTime(zmanimtoday.get(Zman.SHEKIYA));
            shekiyaMinusOneMinute.add(Calendar.MINUTE, -1);
            Calendar mgMinusOneMinute = Calendar.getInstance();
            mgMinusOneMinute.setTime(zmanimtoday.get(Zman.MINCHA_GEDOLA));
            mgMinusOneMinute.add(Calendar.MINUTE, -1);
            if (startDate != null && (startDate.after(terminationDate))) {
                if (startDate != null) {
                    String organizationName;
                    Nusach organizationNusach;
                    String organizationId;
                    Organization organization = minyan.getOrganization();
                    String organizationColor = minyan.getOrgColor();
                    if (organization == null) {
                        Organization temp = organizationDAO.findById(minyan.getOrganizationId());
                        organizationName = temp.getName();
                        organizationId = temp.getId();
                        organizationNusach = temp.getNusach();
                        organizationColor = temp.getOrgColor();
                    } else {
                        organizationName = organization.getName();
                        organizationId = organization.getId();
                        organizationNusach = organization.getNusach();
                        organizationColor = organization.getOrgColor();
                    }

                    String locationName = null;
                    Location location = minyan.getLocation();
                    if (location == null) {
                        location = locationDAO.findById(minyan.getLocationId());
                        if (location != null) {
                            locationName = location.getName();
                        }
                    } else {
                        locationName = location.getName();
                    }

                    String dynamicDisplayName = minyan.getMinyanTime().dynamicDisplayName();
                    String roundedDisplayName = minyan.getMinyanTime().roundedDisplayName();
                    Boolean isSelichosRecited = isSelichosRecited(ref);
                    if (dynamicDisplayName != null) {
                        if (minyan.getType().isShacharis() && startDate.before(zmanimtoday.get(Zman.SZT))
                                && startDate.after(zmanimtoday.get(Zman.ALOS_HASHACHAR))) {
                            nextMinyan.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                    organizationNusach, organizationId, locationName, startDate, dynamicDisplayName,
                                    minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                        } else {
                            if (minyan.getType().isMincha() && startDate.before(zmanimtoday.get(Zman.SHEKIYA))
                                    && (startDate.after(mgMinusOneMinute.getTime())
                                    || (startDate.equals(mgMinusOneMinute.getTime())))) {
                                nextMinyan.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                        organizationNusach, organizationId, locationName, startDate, dynamicDisplayName,
                                        minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                            } else {
                                if (minyan.getType().isMaariv() && (startDate.after(shekiyaMinusOneMinute.getTime())
                                        || startDate.equals((shekiyaMinusOneMinute.getTime())) || dynamicDisplayName.contains("Plag"))) {
                                    nextMinyan.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                            organizationNusach, organizationId, locationName, startDate,
                                            dynamicDisplayName,
                                            minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                                }
                                else {
                                    if (minyan.getType().isSelichos() && isSelichosRecited){
                                        minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                                    organizationNusach, organizationId, locationName, startDate,
                                                    roundedDisplayName,
                                                    minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                                        }
                                    }
                            }
                        }
                    } else if (roundedDisplayName != null) {
                        if (minyan.getType().isShacharis() && startDate.before(zmanimtoday.get(Zman.SZT))
                                && startDate.after(zmanimtoday.get(Zman.ALOS_HASHACHAR))) {
                            nextMinyan.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                    organizationNusach, organizationId, locationName, startDate, roundedDisplayName,
                                    minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                        } else {
                            if (minyan.getType().isMincha() && startDate.before(zmanimtoday.get(Zman.SHEKIYA))
                                    && (startDate.after(mgMinusOneMinute.getTime())
                                    || (startDate.equals(mgMinusOneMinute.getTime())))) {
                                nextMinyan.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                        organizationNusach, organizationId, locationName, startDate, roundedDisplayName,
                                        minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                            } else {
                                if (minyan.getType().isMaariv() && (startDate.after(shekiyaMinusOneMinute.getTime())
                                        || startDate.equals((shekiyaMinusOneMinute.getTime())) || roundedDisplayName.contains("plag"))) {
                                    nextMinyan.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                            organizationNusach, organizationId, locationName, startDate,
                                            roundedDisplayName,
                                            minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                                }
                                else {
                                    if (minyan.getType().isSelichos() && isSelichosRecited){
                                        minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                                    organizationNusach, organizationId, locationName, startDate,
                                                    roundedDisplayName,
                                                    minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                                        }
                                    }
                            }
                        }
                    } else {
                        if (minyan.getType().isShacharis() && startDate.before(zmanimtoday.get(Zman.SZT))
                                && startDate.after(zmanimtoday.get(Zman.ALOS_HASHACHAR))) {
                            nextMinyan.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                    organizationNusach,
                                    organizationId, locationName, startDate, minyan.getNusach(),
                                    minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                        } else {
                            if (minyan.getType().isMincha() && startDate.before(zmanimtoday.get(Zman.SHEKIYA))
                                    && startDate.after(zmanimtoday.get(Zman.MINCHA_GEDOLA))) {
                                nextMinyan.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                        organizationNusach,
                                        organizationId, locationName, startDate, minyan.getNusach(),
                                        minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                            } else {
                                if (minyan.getType().isMaariv() && (startDate.after(shekiyaMinusOneMinute.getTime())
                                        || startDate.equals((shekiyaMinusOneMinute.getTime())))) {
                                    nextMinyan.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                            organizationNusach,
                                            organizationId, locationName, startDate, minyan.getNusach(),
                                            minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                                }
                                else {
                                    if (minyan.getType().isSelichos() && isSelichosRecited){
                                        minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                                    organizationNusach, organizationId, locationName, startDate,
                                                    roundedDisplayName,
                                                    minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                                        }
                                    }
                            }
                        }
                    }
                }
            }
        }
        nextMinyan.sort(Comparator.comparing(MinyanEvent::getStartTime));
        mv.getModel().put("nextMinyan", nextMinyan);

        if(!nextMinyan.isEmpty()) {
            MinyanEvent firstEvent = nextMinyan.get(0);
            mv.getModel().put("upcoming", firstEvent);
        }
        // end upcoming

        mv.getModel().put("shacharisMinyanim", shacharisMinyanim);
        mv.getModel().put("minchaMinyanim", minchaMinyanim);
        mv.getModel().put("maarivMinyanim", maarivMinyanim);

        // mv.getModel().put("usesLocations", minyanEvents.)

        return mv;
    }
    private void setTimeZone(TimeZone tz) {
        // set time format
        timeFormat.setTimeZone(tz);
        dateFormat.setTimeZone(tz);
        onlyDateFormat.setTimeZone(tz);
        strippedDayFormat.setTimeZone(tz);
    }

    private String timeFormatWithRoundingToSecond(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return timeFormatSec.format(calendar.getTime());
    }

    private static LocalDate dateToLocalDate(Date date) {
        Instant instant = date.toInstant();
        ZonedDateTime zonedDateTime = instant.atZone(ZoneId.systemDefault());
        return zonedDateTime.toLocalDate();
    }
    static <T> Predicate<T> distinctByKey(Function<? super T, Object> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    private static boolean sameDayOfMonth(Date date1, Date date2) {
        Calendar calendar1 = Calendar.getInstance();
        calendar1.setTime(date1);
        Calendar calendar2 = Calendar.getInstance();
        calendar2.setTime(date2);
        return calendar1.get(Calendar.DAY_OF_MONTH) == calendar2.get(Calendar.DAY_OF_MONTH);
    }
}
