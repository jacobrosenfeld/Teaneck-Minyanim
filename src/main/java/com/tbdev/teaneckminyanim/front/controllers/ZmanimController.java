package com.tbdev.teaneckminyanim.front.controllers;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.Dictionary;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import com.kosherjava.zmanim.util.GeoLocation;
import com.tbdev.teaneckminyanim.admin.structure.location.Location;
import com.tbdev.teaneckminyanim.admin.structure.location.LocationDAO;
import com.tbdev.teaneckminyanim.admin.structure.minyan.Minyan;
import com.tbdev.teaneckminyanim.admin.structure.minyan.MinyanDAO;
import com.tbdev.teaneckminyanim.admin.structure.organization.Organization;
import com.tbdev.teaneckminyanim.admin.structure.organization.OrganizationDAO;
import com.tbdev.teaneckminyanim.front.KolhaMinyanim;
import com.tbdev.teaneckminyanim.front.MinyanEvent;
import com.tbdev.teaneckminyanim.front.ZmanimHandler;
import com.tbdev.teaneckminyanim.global.Nusach;
import com.tbdev.teaneckminyanim.global.Zman;

@Controller
public class ZmanimController {
    TimeZone timeZone = TimeZone.getTimeZone("America/New_York");

    String locationName = "Teaneck, NJ";
    double latitude = 40.906871;
    double longitude = -74.020924;
    double elevation = 0;
    GeoLocation geoLocation = new GeoLocation(locationName, latitude, longitude, elevation, timeZone);

    Calendar calendar = Calendar.getInstance();
    SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM d, yyyy | h:mm aa");
    SimpleDateFormat onlyDateFormat = new SimpleDateFormat("EEEE, MMMM d");
    SimpleDateFormat strippedDayFormat = new SimpleDateFormat("MMMM d");
    SimpleDateFormat timeFormatSec = new SimpleDateFormat("h:mm:ss aa");
    SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm aa");

    ZmanimHandler zmanimHandler = new ZmanimHandler(geoLocation);

    @Autowired
    private MinyanDAO minyanDAO;

    @Autowired
    private OrganizationDAO organizationDAO;

    @Autowired
    private LocationDAO locationDAO;

    @GetMapping("/")
    public ModelAndView home() {
        return todaysZmanim();
    }

    @GetMapping("/subscription")
    public ModelAndView subscription() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("subscription");
        return mv;
    }

    private void setTimeZone(TimeZone tz) {
        // set time format
        timeFormat.setTimeZone(tz);
        dateFormat.setTimeZone(tz);
        onlyDateFormat.setTimeZone(tz);
        strippedDayFormat.setTimeZone(tz);
    }

    @GetMapping("/zmanim")
    public ModelAndView todaysZmanim() {
        System.out.println("Displaying today's zmanim...");
        return zmanim(new Date());
    }

    private String timeFormatWithRoundingToMinute(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.SECOND, 30);
        calendar.setTimeZone(timeZone);
        return timeFormat.format(calendar.getTime());
    }

    private String timeFormatWithRoundingToSecond(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return timeFormatSec.format(calendar.getTime());
    }

    public ModelAndView zmanim(Date date) {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("homepage");

        System.out.println("DEBUG: Adding dates to model");

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

        System.out.println("DEBUG: Fetching zmanim for model");

        LocalDate localDate = dateToLocalDate(date);
        System.out.println("Showing zmanim for date: " + localDate.getMonth() + ":" + localDate.getMonthValue() + ":"
                + localDate.getMonth().getValue() + ":" + localDate.toString());

        Dictionary<Zman, Date> zmanim = zmanimHandler.getZmanim(localDate);
        Dictionary<Zman, Date> zmanimtoday = zmanimHandler.getZmanimForNow();

        System.out.println("DEBUG: Putting zmanim in model");

        System.out.println("ALOT HASH: " + zmanim.get(Zman.ALOT_HASHACHAR));
        mv.getModel().put("alotHashachar", timeFormatWithRoundingToSecond(zmanim.get(Zman.ALOT_HASHACHAR)));
        mv.getModel().put("ett", timeFormatWithRoundingToSecond(zmanim.get(Zman.ETT)));
        mv.getModel().put("sunrise", timeFormatWithRoundingToSecond(zmanim.get(Zman.NETZ)));
        mv.getModel().put("szks", timeFormatWithRoundingToSecond(zmanim.get(Zman.SZKS)));
        mv.getModel().put("szt", timeFormatWithRoundingToSecond(zmanim.get(Zman.SZT)));
        mv.getModel().put("chatzot", timeFormatWithRoundingToSecond(zmanim.get(Zman.CHATZOT)));
        mv.getModel().put("minchaGedola", timeFormatWithRoundingToSecond(zmanim.get(Zman.MINCHA_GEDOLA)));
        mv.getModel().put("minchaKetana", timeFormatWithRoundingToSecond(zmanim.get(Zman.MINCHA_KETANA)));
        mv.getModel().put("plagHamincha", timeFormatWithRoundingToSecond(zmanim.get(Zman.PLAG_HAMINCHA)));
        mv.getModel().put("shekiya", timeFormatWithRoundingToSecond(zmanim.get(Zman.SHEKIYA)));
        mv.getModel().put("earliestShema", timeFormatWithRoundingToSecond(zmanim.get(Zman.EARLIEST_SHEMA)));
        mv.getModel().put("tzet", timeFormatWithRoundingToSecond(zmanim.get(Zman.TZET)));

        System.out.println("DEBUG: Fetching minyanim");

        // get minyanim closest in time to now
        // todo: only get items with non null time for date
        List<Minyan> enabledMinyanim = minyanDAO.getEnabled();
        List<MinyanEvent> minyanEvents = new ArrayList<>();

        System.out.println("DEBUG: Filtering through minyanim");

        for (Minyan minyan : enabledMinyanim) {
            LocalDate ref = dateToLocalDate(date);
            Date startDate = minyan.getStartDate(ref);
            Date now = new Date();
            Date terminationDate = new Date(now.getTime() - (60000 * 8));
            System.out.println("SD: " + startDate);
            System.out.println("TD: " + terminationDate);
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
                            && startDate.after(zmanim.get(Zman.ALOT_HASHACHAR))) {
                        minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                organizationNusach, organizationId, locationName, startDate, dynamicDisplayName,
                                minyan.getNusach(), minyan.getNotes(), organizationColor));
                    } else {
                        if (minyan.getType().isMincha() && startDate.before(zmanim.get(Zman.SHEKIYA))
                                && (startDate.after(mgMinusOneMinute.getTime())
                                        || (startDate.equals(mgMinusOneMinute.getTime())))) {
                            minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                    organizationNusach, organizationId, locationName, startDate, dynamicDisplayName,
                                    minyan.getNusach(), minyan.getNotes(), organizationColor));
                        } else {
                            if (minyan.getType().isMaariv() && (startDate.after(shekiyaMinusOneMinute.getTime())
                                    || startDate.equals((shekiyaMinusOneMinute.getTime())))) {
                                minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                        organizationNusach, organizationId, locationName, startDate, dynamicDisplayName,
                                        minyan.getNusach(), minyan.getNotes(), organizationColor));
                            }
                        }
                    }
                } else if (roundedDisplayName != null) {
                    if (minyan.getType().isShacharis() && startDate.before(zmanim.get(Zman.SZT))
                            && startDate.after(zmanim.get(Zman.ALOT_HASHACHAR))) {
                        minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                organizationNusach, organizationId, locationName, startDate, roundedDisplayName,
                                minyan.getNusach(), minyan.getNotes(), organizationColor));
                    } else {
                        if (minyan.getType().isMincha() && startDate.before(zmanim.get(Zman.SHEKIYA))
                                && (startDate.after(mgMinusOneMinute.getTime())
                                        || (startDate.equals(mgMinusOneMinute.getTime())))) {
                            minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                    organizationNusach, organizationId, locationName, startDate, roundedDisplayName,
                                    minyan.getNusach(), minyan.getNotes(), organizationColor));
                        } else {
                            if (minyan.getType().isMaariv() && (startDate.after(shekiyaMinusOneMinute.getTime())
                                    || startDate.equals((shekiyaMinusOneMinute.getTime())))) {
                                minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                        organizationNusach, organizationId, locationName, startDate, roundedDisplayName,
                                        minyan.getNusach(), minyan.getNotes(), organizationColor));
                            }
                        }
                    }
                } else {
                    if (minyan.getType().isShacharis() && startDate.before(zmanim.get(Zman.SZT))
                            && startDate.after(zmanim.get(Zman.ALOT_HASHACHAR))) {
                        minyanEvents
                                .add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                        organizationNusach,
                                        organizationId, locationName, startDate, minyan.getNusach(),
                                        minyan.getNotes(), organizationColor));
                    } else {
                        if (minyan.getType().isMincha() && startDate.before(zmanim.get(Zman.SHEKIYA))
                                && startDate.after(zmanim.get(Zman.MINCHA_GEDOLA))) {
                            minyanEvents
                                    .add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                            organizationNusach,
                                            organizationId, locationName, startDate, minyan.getNusach(),
                                            minyan.getNotes(), organizationColor));
                        } else {
                            if (minyan.getType().isMaariv() && (startDate.after(shekiyaMinusOneMinute.getTime())
                                    || startDate.equals((shekiyaMinusOneMinute.getTime())))) {
                                minyanEvents
                                        .add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                                organizationNusach,
                                                organizationId, locationName, startDate, minyan.getNusach(),
                                                minyan.getNotes(), organizationColor));
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
            System.out.println("SD: " + startDate);
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

    private static LocalDate dateToLocalDate(Date date) {
        Instant instant = date.toInstant();
        ZonedDateTime zonedDateTime = instant.atZone(ZoneId.systemDefault());
        return zonedDateTime.toLocalDate();
    }

    private static boolean sameDayOfMonth(Date date1, Date date2) {
        Calendar calendar1 = Calendar.getInstance();
        calendar1.setTime(date1);
        Calendar calendar2 = Calendar.getInstance();
        calendar2.setTime(date2);
        return calendar1.get(Calendar.DAY_OF_MONTH) == calendar2.get(Calendar.DAY_OF_MONTH);
    }

    static <T> Predicate<T> distinctByKey(Function<? super T, Object> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    @GetMapping("/zmanim/next")
    public ModelAndView nextZmanimAfter(@RequestParam(value = "after", required = true) String dateString) {
        Date date = new Date(dateString);
        return navigateZmanim(new Date(date.getYear(), date.getMonth(), date.getDate() + 1, date.getHours(),
                date.getMinutes(), date.getSeconds()));
    }

    @GetMapping("/zmanim/last")
    public ModelAndView lastZmanimBefore(@RequestParam(value = "before", required = true) String dateString) {
        Date date = new Date(dateString);
        return navigateZmanim(new Date(date.getYear(), date.getMonth(), date.getDate() - 1, date.getHours(),
                date.getMinutes(), date.getSeconds()));
    }

    @GetMapping("/orgs/{id}/next")
    public ModelAndView nextOrgAfter(@PathVariable String id,
            @RequestParam(value = "after", required = true) String dateString) throws Exception {
        Date date = new Date(dateString);
        return navigateOrg(id, new Date(date.getYear(), date.getMonth(), date.getDate() + 1, date.getHours(),
                date.getMinutes(), date.getSeconds()));
    }

    @GetMapping("/orgs/{id}/last")
    public ModelAndView lastOrgBefore(@PathVariable String id,
            @RequestParam(value = "before", required = true) String dateString) throws Exception {
        Date date = new Date(dateString);
        return navigateOrg(id, new Date(date.getYear(), date.getMonth(), date.getDate() - 1, date.getHours(),
                date.getMinutes(), date.getSeconds()));
    }

    public ModelAndView navigateZmanim(Date date) {
        return zmanim(date);
    }

    public ModelAndView navigateOrg(String orgId, Date date) throws Exception {
        return org(orgId, date);
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

        System.out.println("DEBUG: Putting zmanim in model");

        System.out.println("ALOT HASH: " + zmanim.get(Zman.ALOT_HASHACHAR));
        mv.getModel().put("alotHashachar", timeFormatWithRoundingToSecond(zmanim.get(Zman.ALOT_HASHACHAR)));
        mv.getModel().put("ETT", timeFormatWithRoundingToSecond(zmanim.get(Zman.ETT)));
        mv.getModel().put("sunrise", timeFormatWithRoundingToSecond(zmanim.get(Zman.NETZ)));
        mv.getModel().put("szks", timeFormatWithRoundingToSecond(zmanim.get(Zman.SZKS)));
        mv.getModel().put("szt", timeFormatWithRoundingToSecond(zmanim.get(Zman.SZT)));
        mv.getModel().put("chatzot", timeFormatWithRoundingToSecond(zmanim.get(Zman.CHATZOT)));
        mv.getModel().put("minchaGedola", timeFormatWithRoundingToSecond(zmanim.get(Zman.MINCHA_GEDOLA)));
        mv.getModel().put("minchaKetana", timeFormatWithRoundingToSecond(zmanim.get(Zman.MINCHA_KETANA)));
        mv.getModel().put("plagHamincha", timeFormatWithRoundingToSecond(zmanim.get(Zman.PLAG_HAMINCHA)));
        mv.getModel().put("shekiya", timeFormatWithRoundingToSecond(zmanim.get(Zman.SHEKIYA)));
        mv.getModel().put("earliestShema", timeFormatWithRoundingToSecond(zmanim.get(Zman.EARLIEST_SHEMA)));
        mv.getModel().put("tzet", timeFormatWithRoundingToSecond(zmanim.get(Zman.TZET)));

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
                            && startDate.after(zmanim.get(Zman.ALOT_HASHACHAR))) {
                        minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                organizationNusach, organizationId, locationName, startDate, dynamicDisplayName,
                                minyan.getNusach(), minyan.getNotes(), organizationColor));
                    } else {
                        if (minyan.getType().isMincha() && startDate.before(zmanim.get(Zman.SHEKIYA))
                                && (startDate.after(mgMinusOneMinute.getTime())
                                        || (startDate.equals(mgMinusOneMinute.getTime())))) {
                            minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                    organizationNusach, organizationId, locationName, startDate, dynamicDisplayName,
                                    minyan.getNusach(), minyan.getNotes(), organizationColor));
                        } else {
                            if (minyan.getType().isMaariv() && (startDate.after(shekiyaMinusOneMinute.getTime())
                                    || startDate.equals((shekiyaMinusOneMinute.getTime())))) {
                                minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                        organizationNusach, organizationId, locationName, startDate, dynamicDisplayName,
                                        minyan.getNusach(), minyan.getNotes(), organizationColor));
                            }
                        }
                    }
                } else if (roundedDisplayName != null) {
                    if (minyan.getType().isShacharis() && startDate.before(zmanim.get(Zman.SZT))
                            && startDate.after(zmanim.get(Zman.ALOT_HASHACHAR))) {
                        minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                organizationNusach, organizationId, locationName, startDate, roundedDisplayName,
                                minyan.getNusach(), minyan.getNotes(), organizationColor));
                    } else {
                        if (minyan.getType().isMincha() && startDate.before(zmanim.get(Zman.SHEKIYA))
                                && (startDate.after(mgMinusOneMinute.getTime())
                                        || (startDate.equals(mgMinusOneMinute.getTime())))) {
                            minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                    organizationNusach, organizationId, locationName, startDate, roundedDisplayName,
                                    minyan.getNusach(), minyan.getNotes(), organizationColor));
                        } else {
                            if (minyan.getType().isMaariv() && (startDate.after(shekiyaMinusOneMinute.getTime())
                                    || startDate.equals((shekiyaMinusOneMinute.getTime())))) {
                                minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                        organizationNusach, organizationId, locationName, startDate, roundedDisplayName,
                                        minyan.getNusach(), minyan.getNotes(), organizationColor));
                            }
                        }
                    }
                } else {
                    if (minyan.getType().isShacharis() && startDate.before(zmanim.get(Zman.SZT))
                            && startDate.after(zmanim.get(Zman.ALOT_HASHACHAR))) {
                        minyanEvents
                                .add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                        organizationNusach,
                                        organizationId, locationName, startDate, minyan.getNusach(),
                                        minyan.getNotes(), organizationColor));
                    } else {
                        if (minyan.getType().isMincha() && startDate.before(zmanim.get(Zman.SHEKIYA))
                                && startDate.after(zmanim.get(Zman.MINCHA_GEDOLA))) {
                            minyanEvents
                                    .add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                            organizationNusach,
                                            organizationId, locationName, startDate, minyan.getNusach(),
                                            minyan.getNotes(), organizationColor));
                        } else {
                            if (minyan.getType().isMaariv() && (startDate.after(shekiyaMinusOneMinute.getTime())
                                    || startDate.equals((shekiyaMinusOneMinute.getTime())))) {
                                minyanEvents
                                        .add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                                organizationNusach,
                                                organizationId, locationName, startDate, minyan.getNusach(),
                                                minyan.getNotes(), organizationColor));
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
                    if (dynamicDisplayName != null) {
                        if (minyan.getType().isShacharis() && startDate.before(zmanimtoday.get(Zman.SZT))
                                && startDate.after(zmanimtoday.get(Zman.ALOT_HASHACHAR))) {
                            nextMinyan.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                    organizationNusach, organizationId, locationName, startDate, dynamicDisplayName,
                                    minyan.getNusach(), minyan.getNotes(), organizationColor));
                        } else {
                            if (minyan.getType().isMincha() && startDate.before(zmanimtoday.get(Zman.SHEKIYA))
                                    && (startDate.after(mgMinusOneMinute.getTime())
                                            || (startDate.equals(mgMinusOneMinute.getTime())))) {
                                nextMinyan.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                        organizationNusach, organizationId, locationName, startDate, dynamicDisplayName,
                                        minyan.getNusach(), minyan.getNotes(), organizationColor));
                            } else {
                                if (minyan.getType().isMaariv() && (startDate.after(shekiyaMinusOneMinute.getTime())
                                        || startDate.equals((shekiyaMinusOneMinute.getTime())))) {
                                    nextMinyan.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                            organizationNusach, organizationId, locationName, startDate,
                                            dynamicDisplayName,
                                            minyan.getNusach(), minyan.getNotes(), organizationColor));
                                }
                            }
                        }
                    } else if (roundedDisplayName != null) {
                        if (minyan.getType().isShacharis() && startDate.before(zmanimtoday.get(Zman.SZT))
                                && startDate.after(zmanimtoday.get(Zman.ALOT_HASHACHAR))) {
                            nextMinyan.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                    organizationNusach, organizationId, locationName, startDate, roundedDisplayName,
                                    minyan.getNusach(), minyan.getNotes(), organizationColor));
                        } else {
                            if (minyan.getType().isMincha() && startDate.before(zmanimtoday.get(Zman.SHEKIYA))
                                    && (startDate.after(mgMinusOneMinute.getTime())
                                            || (startDate.equals(mgMinusOneMinute.getTime())))) {
                                nextMinyan.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                        organizationNusach, organizationId, locationName, startDate, roundedDisplayName,
                                        minyan.getNusach(), minyan.getNotes(), organizationColor));
                            } else {
                                if (minyan.getType().isMaariv() && (startDate.after(shekiyaMinusOneMinute.getTime())
                                        || startDate.equals((shekiyaMinusOneMinute.getTime())))) {
                                    nextMinyan.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                            organizationNusach, organizationId, locationName, startDate,
                                            roundedDisplayName,
                                            minyan.getNusach(), minyan.getNotes(), organizationColor));
                                }
                            }
                        }
                    } else {
                        if (minyan.getType().isShacharis() && startDate.before(zmanimtoday.get(Zman.SZT))
                                && startDate.after(zmanimtoday.get(Zman.ALOT_HASHACHAR))) {
                            nextMinyan.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                            organizationNusach,
                                            organizationId, locationName, startDate, minyan.getNusach(),
                                            minyan.getNotes(), organizationColor));
                        } else {
                            if (minyan.getType().isMincha() && startDate.before(zmanimtoday.get(Zman.SHEKIYA))
                                    && startDate.after(zmanimtoday.get(Zman.MINCHA_GEDOLA))) {
                                nextMinyan.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                                organizationNusach,
                                                organizationId, locationName, startDate, minyan.getNusach(),
                                                minyan.getNotes(), organizationColor));
                            } else {
                                if (minyan.getType().isMaariv() && (startDate.after(shekiyaMinusOneMinute.getTime())
                                        || startDate.equals((shekiyaMinusOneMinute.getTime())))) {
                                    nextMinyan.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                                    organizationNusach,
                                                    organizationId, locationName, startDate, minyan.getNusach(),
                                                    minyan.getNotes(), organizationColor));
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

    @RequestMapping("/orgs/{orgId}")
    public ModelAndView orgToday(@PathVariable String orgId) throws Exception {
        return org(orgId, new Date());
    }
}
