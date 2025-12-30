package com.tbdev.teaneckminyanim.service;

import com.kosherjava.zmanim.util.GeoLocation;
import com.tbdev.teaneckminyanim.minyan.MinyanType;
import com.tbdev.teaneckminyanim.model.Location;
import com.tbdev.teaneckminyanim.model.Minyan;
import com.tbdev.teaneckminyanim.front.KolhaMinyanim;
import com.tbdev.teaneckminyanim.front.MinyanEvent;
import com.tbdev.teaneckminyanim.enums.Nusach;
import com.tbdev.teaneckminyanim.enums.Zman;
import com.tbdev.teaneckminyanim.model.Organization;
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
    
    private final ApplicationSettingsService settingsService;
    private final LocationService locationDAO;
    private final OrganizationService organizationDAO;
    private final MinyanService minyanService;
    private final com.tbdev.teaneckminyanim.service.provider.OrgScheduleResolver scheduleResolver;
    private final ZmanimHandler zmanimHandler;

    Calendar calendar = Calendar.getInstance();
    SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM d, yyyy | h:mm aa");
    SimpleDateFormat onlyDateFormat = new SimpleDateFormat("EEEE, MMMM d");
    SimpleDateFormat strippedDayFormat = new SimpleDateFormat("MMMM d");
    SimpleDateFormat timeFormatSec = new SimpleDateFormat("h:mm:ss aa");
    SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm aa");



    public ModelAndView getZmanim(Date date) {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("homepage");

        log.info("DEBUG: Adding dates to model");

        // Get timezone from settings
        TimeZone timeZone = settingsService.getTimeZone();
        
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

        log.info(": Fetching zmanim for model");

        LocalDate localDate = dateToLocalDate(date);
        log.info("Showing zmanim for date: " + localDate.getMonth() + ":" + localDate.getMonthValue() + ":"
                + localDate.getMonth().getValue() + ":" + localDate.toString());

        Dictionary<Zman, Date> zmanim = zmanimHandler.getZmanim(localDate);
        Dictionary<Zman, Date> zmanimtoday = zmanimHandler.getZmanimForNow();

        log.info(": Putting zmanim in model");

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

        log.info(": Fetching minyanim");

        List<MinyanEvent> minyanEvents = new ArrayList<>();
        LocalDate localDateRef = dateToLocalDate(date);
        Date now = new Date();
        Date terminationDate = new Date(now.getTime() - (60000 * 8));

        // Get all organizations
        List<Organization> allOrganizations = organizationDAO.getAll();
        log.info("Processing {} organizations for homepage", allOrganizations.size());

        // For each organization, check if calendar import is enabled and get events accordingly
        for (Organization org : allOrganizations) {
            String orgId = org.getId();
            boolean useCalendarImport = scheduleResolver.isCalendarImportEnabled(orgId);
            
            if (useCalendarImport) {
                log.info("Using calendar import provider for organization: {} ({})", org.getName(), orgId);
                // Get events from calendar import provider
                List<MinyanEvent> calendarEvents = scheduleResolver.getEventsForDate(orgId, localDateRef);
                
                // Filter calendar events based on time (same logic as rule-based)
                for (MinyanEvent event : calendarEvents) {
                    if (event.getStartTime() != null && 
                        (event.getStartTime().after(terminationDate) || !sameDayOfMonth(now, date))) {
                        minyanEvents.add(event);
                    }
                }
                log.info("Added {} calendar-imported events from {}", calendarEvents.size(), org.getName());
            } else {
                log.info("Using rule-based provider for organization: {} ({})", org.getName(), orgId);
                // Use existing rule-based logic for this organization
                List<Minyan> orgMinyanim = minyanService.findEnabledMatching(orgId);
                
                LocalDate ref = localDateRef;
                Calendar shekiyaMinusOneMinute = Calendar.getInstance();
                shekiyaMinusOneMinute.setTime(zmanim.get(Zman.SHEKIYA));
                shekiyaMinusOneMinute.add(Calendar.MINUTE, -1);
                Calendar mgMinusOneMinute = Calendar.getInstance();
                mgMinusOneMinute.setTime(zmanim.get(Zman.MINCHA_GEDOLA));
                mgMinusOneMinute.add(Calendar.MINUTE, -1);
                boolean isSelichosRecited = zmanimHandler.isSelichosRecited(ref);

        for (Minyan minyan : orgMinyanim) {
            Date startDate = minyan.getStartDate(ref);
            log.info("SD: " + startDate);
            log.info("TD: " + terminationDate);
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
                String organizationColor;
                Optional<Organization> organization = organizationDAO.findById(minyan.getOrganizationId());
                if (organization.isEmpty()) {
                    Organization temp = organizationDAO.findById(minyan.getOrganizationId()).get();
                    organizationName = temp.getName();
                    organizationNusach = temp.getNusach();
                    organizationId = temp.getId();
                    organizationColor = temp.getOrgColor();
                } else {
                    organizationName = organization.get().getName();
                    organizationNusach = organization.get().getNusach();
                    organizationId = organization.get().getId();
                    organizationColor = organization.get().getOrgColor();
                }

                String locationName = null;
                Location location = locationDAO.findById(minyan.getLocationId());
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
                    if (minyan.getType().equals(MinyanType.SHACHARIS) && startDate.before(zmanim.get(Zman.SZT))
                            && startDate.after(zmanim.get(Zman.ALOS_HASHACHAR))) {
                        minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                organizationNusach, organizationId, locationName, startDate, dynamicDisplayName,
                                minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                    } else {
                        if (minyan.getType().equals(MinyanType.MINCHA) && startDate.before(zmanim.get(Zman.SHEKIYA))
                                && (startDate.after(mgMinusOneMinute.getTime())
                                || (startDate.equals(mgMinusOneMinute.getTime())))) {
                            minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                    organizationNusach, organizationId, locationName, startDate, dynamicDisplayName,
                                    minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                        } else {
                            if (minyan.getType().equals(MinyanType.MAARIV) && (startDate.after(shekiyaMinusOneMinute.getTime())
                                    || startDate.equals((shekiyaMinusOneMinute.getTime())) || dynamicDisplayName.contains("Plag"))) {
                                minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                        organizationNusach, organizationId, locationName, startDate, dynamicDisplayName,
                                        minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                            }
                            else {
                                if (minyan.getType().equals(MinyanType.SELICHOS) && isSelichosRecited){
                                    minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                                organizationNusach, organizationId, locationName, startDate,
                                                roundedDisplayName,
                                                minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                                    }
                                }
                        }
                    }
                } else if (roundedDisplayName != null) {
                    if (minyan.getType().equals(MinyanType.SHACHARIS) && startDate.before(zmanim.get(Zman.SZT))
                            && startDate.after(zmanim.get(Zman.ALOS_HASHACHAR))) {
                        minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                organizationNusach, organizationId, locationName, startDate, roundedDisplayName,
                                minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                    } else {
                        if (minyan.getType().equals(MinyanType.MINCHA) && startDate.before(zmanim.get(Zman.SHEKIYA))
                                && (startDate.after(mgMinusOneMinute.getTime())
                                || (startDate.equals(mgMinusOneMinute.getTime())))) {
                            minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                    organizationNusach, organizationId, locationName, startDate, roundedDisplayName,
                                    minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                        } else {
                            if (minyan.getType().equals(MinyanType.MAARIV) && (startDate.after(shekiyaMinusOneMinute.getTime())
                                    || startDate.equals((shekiyaMinusOneMinute.getTime())) || roundedDisplayName.contains("plag"))) {
                                minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                        organizationNusach, organizationId, locationName, startDate, roundedDisplayName,
                                        minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                            }
                            else {
                                if (minyan.getType().equals(MinyanType.SELICHOS) && isSelichosRecited){
                                    minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                                organizationNusach, organizationId, locationName, startDate,
                                                roundedDisplayName,
                                                minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                                    }
                                }
                        }
                    }
                } else {
                    if (minyan.getType().equals(MinyanType.SHACHARIS) && startDate.before(zmanim.get(Zman.SZT))
                            && startDate.after(zmanim.get(Zman.ALOS_HASHACHAR))) {
                        minyanEvents
                                .add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                        organizationNusach,
                                        organizationId, locationName, startDate, minyan.getNusach(),
                                        minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                    } else {
                        if (minyan.getType().equals(MinyanType.MINCHA) && startDate.before(zmanim.get(Zman.SHEKIYA))
                                && startDate.after(zmanim.get(Zman.MINCHA_GEDOLA))) {
                            minyanEvents
                                    .add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                            organizationNusach,
                                            organizationId, locationName, startDate, minyan.getNusach(),
                                            minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                        } else {
                            if (minyan.getType().equals(MinyanType.MAARIV) && (startDate.after(shekiyaMinusOneMinute.getTime())
                                    || startDate.equals((shekiyaMinusOneMinute.getTime())))) {
                                minyanEvents
                                        .add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                                organizationNusach,
                                                organizationId, locationName, startDate, minyan.getNusach(),
                                                minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                            }
                            else {
                                if (minyan.getType().equals(MinyanType.SELICHOS) && isSelichosRecited){
                                    minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                                organizationNusach, organizationId, locationName, startDate,
                                                roundedDisplayName,
                                                minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                                    }
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
            } // End of rule-based provider loop for this organization
        } // End of organization loop

        log.info("Total events collected for homepage: {}", minyanEvents.size());
        
        // KolhaMinyanim insertion
        List<KolhaMinyanim> kolhaMinyanims = new ArrayList<>();

        // Process all organizations again for KolhaMinyanim
        for (Organization org : allOrganizations) {
            String orgId = org.getId();
            boolean useCalendarImport = scheduleResolver.isCalendarImportEnabled(orgId);
            
            if (useCalendarImport) {
                // For calendar imports, convert MinyanEvents to KolhaMinyanim
                List<MinyanEvent> calendarEvents = scheduleResolver.getEventsForDate(orgId, localDateRef);
                for (MinyanEvent event : calendarEvents) {
                    if (event.getStartTime() != null) {
                        // Use the correct fields from MinyanEvent
                        String parentMinyanId = "imported"; // Calendar events don't have a parent minyan ID
                        String dynamicTimeStr = event.dynamicTimeString();
                        kolhaMinyanims.add(new KolhaMinyanim(
                            parentMinyanId,
                            event.getType(),
                            event.getOrganizationName(),
                            org.getNusach(), // Use org nusach as organizationNusach
                            event.getOrganizationId(),
                            event.getLocationName(),
                            event.getStartTime(),
                            dynamicTimeStr != null ? dynamicTimeStr : "",
                            event.getNusach(),
                            event.getNotes(),
                            event.getOrgColor()
                        ));
                    }
                }
            } else {
                // Use rule-based minyanim for this organization
                List<Minyan> orgMinyanim = minyanService.findEnabledMatching(orgId);
        for (Minyan minyan : orgMinyanim) {
            LocalDate ref = dateToLocalDate(date);
            Date startDate = minyan.getStartDate(ref);
            log.info("SD: " + startDate);
            if (startDate != null) {
                String organizationName;
                Nusach organizationNusach;
                String organizationId;
                String organizationColor = minyan.getOrgColor();
//                Organization organization = minyan();
                Optional<Organization> organization = organizationDAO.findById(minyan.getOrganizationId());
                if (organization.isEmpty()) {
                    Organization temp = organizationDAO.findById(minyan.getOrganizationId()).get();
                    organizationName = temp.getName();
                    organizationNusach = temp.getNusach();
                    organizationId = temp.getId();
                    organizationColor = temp.getOrgColor();
                } else {
                    organizationName = organization.get().getName();
                    organizationNusach = organization.get().getNusach();
                    organizationId = organization.get().getId();
                    organizationColor = organization.get().getOrgColor();
                }

                String locationName = null;
                Location location = locationDAO.findById(minyan.getLocationId());
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
            } // End of rule-based provider for KolhaMinyanim
        } // End of organization loop for KolhaMinyanim
        
        kolhaMinyanims.sort(Comparator.comparing(KolhaMinyanim::getStartTime));
        
        // Populate organization slugs for KolhaMinyanim objects
        populateOrganizationSlugsForKolha(kolhaMinyanims);
        
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
        
        // Populate organization slugs for all minyan events
        populateOrganizationSlugs(minyanEvents);
        
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

        dateFormat.setTimeZone(settingsService.getTimeZone());

        mv.getModel().put("date", dateFormat.format(date));
        mv.getModel().put("onlyDate", onlyDateFormat.format(date));

        Calendar c = Calendar.getInstance();

        LocalDate localDate = dateToLocalDate(date);

        Dictionary<Zman, Date> zmanim = zmanimHandler.getZmanim(localDate);
        Dictionary<Zman, Date> zmanimtoday = zmanimHandler.getZmanimForNow();

        log.info(": Putting zmanim in model");

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
            Organization org = organizationDAO.findById(orgId).orElse(new Organization());
            organizationDAO.setupOrg(org);
            mv.addObject("org", org);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Sorry, there was an error finding the organization.");
        }

        List<MinyanEvent> minyanEvents = new ArrayList<>();
        
        // Check if calendar import is enabled for this organization
        boolean useCalendarImport = scheduleResolver.isCalendarImportEnabled(orgId);
        
        if (useCalendarImport) {
            log.info("Using calendar import provider for organization: {}", orgId);
            // Get events from calendar import provider
            LocalDate localDateRef = dateToLocalDate(date);
            List<MinyanEvent> calendarEvents = scheduleResolver.getEventsForDate(orgId, localDateRef);
            minyanEvents.addAll(calendarEvents);
            log.info("Added {} calendar-imported events", calendarEvents.size());
        } else {
            log.info("Using rule-based provider for organization: {}", orgId);
            // Use existing rule-based logic
            List<Minyan> enabledMinyanim = minyanService.findEnabledMatching(orgId);
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
                Optional<Organization> organization = organizationDAO.findById(minyan.getOrganizationId());
                String organizationColor = minyan.getOrgColor();
                boolean isSelichosRecited = zmanimHandler.isSelichosRecited(ref);
                if (organization.isEmpty()) {
                    Optional<Organization> temp = organizationDAO.findById(minyan.getOrganizationId());
                    organizationName = temp.get().getName();
                    organizationId = temp.get().getId();
                    organizationNusach = temp.get().getNusach();
                } else {
                    organizationName = organization.get().getName();
                    organizationId = organization.get().getId();
                    organizationNusach = organization.get().getNusach();
                    organizationColor = organization.get().getOrgColor();
                }

                String locationName = null;
                Location location = locationDAO.findById(minyan.getLocationId());
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
                    if (minyan.getType().equals(MinyanType.SHACHARIS) && startDate.before(zmanim.get(Zman.SZT))
                            && startDate.after(zmanim.get(Zman.ALOS_HASHACHAR))) {
                        minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                organizationNusach, organizationId, locationName, startDate, dynamicDisplayName,
                                minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                    } else {
                        if (minyan.getType().equals(MinyanType.MINCHA) && startDate.before(zmanim.get(Zman.SHEKIYA))
                                && (startDate.after(mgMinusOneMinute.getTime())
                                || (startDate.equals(mgMinusOneMinute.getTime())))) {
                            minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                    organizationNusach, organizationId, locationName, startDate, dynamicDisplayName,
                                    minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                        } else {
                            if (minyan.getType().equals(MinyanType.MAARIV) && (startDate.after(shekiyaMinusOneMinute.getTime())
                                    || startDate.equals((shekiyaMinusOneMinute.getTime())) || dynamicDisplayName.contains("Plag"))) {
                                minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                        organizationNusach, organizationId, locationName, startDate, dynamicDisplayName,
                                        minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                            }
                            else {
                                if (minyan.getType().equals(MinyanType.SELICHOS) && isSelichosRecited){
                                    minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                                organizationNusach, organizationId, locationName, startDate,
                                                roundedDisplayName,
                                                minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                                    }
                                }
                        }
                    }
                } else if (roundedDisplayName != null) {
                    if (minyan.getType().equals(MinyanType.SHACHARIS) && startDate.before(zmanim.get(Zman.SZT))
                            && startDate.after(zmanim.get(Zman.ALOS_HASHACHAR))) {
                        minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                organizationNusach, organizationId, locationName, startDate, roundedDisplayName,
                                minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                    } else {
                        if (minyan.getType().equals(MinyanType.MINCHA) && startDate.before(zmanim.get(Zman.SHEKIYA))
                                && (startDate.after(mgMinusOneMinute.getTime())
                                || (startDate.equals(mgMinusOneMinute.getTime())))) {
                            minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                    organizationNusach, organizationId, locationName, startDate, roundedDisplayName,
                                    minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                        } else {
                            if (minyan.getType().equals(MinyanType.MAARIV) && (startDate.after(shekiyaMinusOneMinute.getTime())
                                    || startDate.equals((shekiyaMinusOneMinute.getTime())) || roundedDisplayName.contains("plag"))) {
                                minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                        organizationNusach, organizationId, locationName, startDate, roundedDisplayName,
                                        minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                            }
                            else {
                                if (minyan.getType().equals(MinyanType.SELICHOS) && isSelichosRecited){
                                    minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                                organizationNusach, organizationId, locationName, startDate,
                                                roundedDisplayName,
                                                minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                                    }
                                }
                        }
                    }
                } else {
                    if (minyan.getType().equals(MinyanType.SHACHARIS) && startDate.before(zmanim.get(Zman.SZT))
                            && startDate.after(zmanim.get(Zman.ALOS_HASHACHAR))) {
                        minyanEvents
                                .add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                        organizationNusach,
                                        organizationId, locationName, startDate, minyan.getNusach(),
                                        minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                    } else {
                        if (minyan.getType().equals(MinyanType.MINCHA) && startDate.before(zmanim.get(Zman.SHEKIYA))
                                && startDate.after(zmanim.get(Zman.MINCHA_GEDOLA))) {
                            minyanEvents
                                    .add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                            organizationNusach,
                                            organizationId, locationName, startDate, minyan.getNusach(),
                                            minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                        } else {
                            if (minyan.getType().equals(MinyanType.MAARIV) && (startDate.after(shekiyaMinusOneMinute.getTime())
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
        } // End of else block for rule-based provider
        
        minyanEvents.sort(Comparator.comparing(MinyanEvent::getStartTime));
        mv.getModel().put("allminyanim", minyanEvents);

        List<MinyanEvent> shacharisMinyanim = new ArrayList<>();
        List<MinyanEvent> minchaMinyanim = new ArrayList<>();
        List<MinyanEvent> maarivMinyanim = new ArrayList<>();
        for (MinyanEvent me : minyanEvents) {
            if (me.getType().isShacharis()) {
                shacharisMinyanim.add(me);
            } else if (me.getType().isMincha() || me.getType().isMinchaMariv()) {
                minchaMinyanim.add(me);
            } else if (me.getType().isMaariv()) {
                maarivMinyanim.add(me);
            }
        }

        // upcoming minyanim for org
        List<MinyanEvent> nextMinyan = new ArrayList<>();

        // IMPORTANT: Next minyan must ALWAYS be based on TODAY, not the viewed date
        // This ensures the "Next minyan" button shows upcoming minyanim for today
        // even when the user is viewing a different date (e.g., tomorrow's schedule)
        // Note: 'today' variable is already defined earlier in this method
        LocalDate todayLocalDate = dateToLocalDate(today);
        
        if (useCalendarImport) {
            // For calendar imports, get TODAY's events and filter for upcoming ones
            log.info("Computing next minyan from calendar imports for TODAY: {}", todayLocalDate);
            List<MinyanEvent> todayEvents = scheduleResolver.getEventsForDate(orgId, todayLocalDate);
            
            Date now = new Date();
            Date terminationDate = new Date(now.getTime() - (60000 * 3)); // 3 minutes ago
            
            // Filter today's events to find upcoming ones
            for (MinyanEvent event : todayEvents) {
                if (event.getStartTime().after(terminationDate)) {
                    nextMinyan.add(event);
                }
            }
            log.info("Found {} upcoming calendar-imported events for today", nextMinyan.size());
        } else {
            // For rule-based: compute next minyan from TODAY's schedule (not viewed date)
            log.info("Computing next minyan from rules for TODAY: {}", todayLocalDate);
            List<Minyan> enabledMinyanim2 = minyanService.findEnabledMatching(orgId);
            for (Minyan minyan : enabledMinyanim2) {
            LocalDate ref = todayLocalDate; // Use TODAY, not the viewed date
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
                    Optional<Organization> organization = organizationDAO.findById(minyan.getOrganizationId());
                    String organizationColor = minyan.getOrgColor();
                    if (organization.isEmpty()) {
                        Optional<Organization> temp = organizationDAO.findById(minyan.getOrganizationId());
                        organizationName = temp.get().getName();
                        organizationId = temp.get().getId();
                        organizationNusach = temp.get().getNusach();
                        organizationColor = temp.get().getOrgColor();
                    } else {
                        organizationName = organization.get().getName();
                        organizationId = organization.get().getId();
                        organizationNusach = organization.get().getNusach();
                        organizationColor = organization.get().getOrgColor();
                    }

                    String locationName = null;
                    Location location = locationDAO.findById(minyan.getLocationId());
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
                    Boolean isSelichosRecited = zmanimHandler.isSelichosRecited(ref);
                    if (dynamicDisplayName != null) {
                        if (minyan.getType().equals(MinyanType.SHACHARIS) && startDate.before(zmanimtoday.get(Zman.SZT))
                                && startDate.after(zmanimtoday.get(Zman.ALOS_HASHACHAR))) {
                            nextMinyan.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                    organizationNusach, organizationId, locationName, startDate, dynamicDisplayName,
                                    minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                        } else {
                            if (minyan.getType().equals(MinyanType.MINCHA) && startDate.before(zmanimtoday.get(Zman.SHEKIYA))
                                    && (startDate.after(mgMinusOneMinute.getTime())
                                    || (startDate.equals(mgMinusOneMinute.getTime())))) {
                                nextMinyan.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                        organizationNusach, organizationId, locationName, startDate, dynamicDisplayName,
                                        minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                            } else {
                                if (minyan.getType().equals(MinyanType.MAARIV) && (startDate.after(shekiyaMinusOneMinute.getTime())
                                        || startDate.equals((shekiyaMinusOneMinute.getTime())) || dynamicDisplayName.contains("Plag"))) {
                                    nextMinyan.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                            organizationNusach, organizationId, locationName, startDate,
                                            dynamicDisplayName,
                                            minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                                }
                                else {
                                    if (minyan.getType().equals(MinyanType.SELICHOS) && isSelichosRecited){
                                        minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                                    organizationNusach, organizationId, locationName, startDate,
                                                    roundedDisplayName,
                                                    minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                                        }
                                    }
                            }
                        }
                    } else if (roundedDisplayName != null) {
                        if (minyan.getType().equals(MinyanType.SHACHARIS) && startDate.before(zmanimtoday.get(Zman.SZT))
                                && startDate.after(zmanimtoday.get(Zman.ALOS_HASHACHAR))) {
                            nextMinyan.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                    organizationNusach, organizationId, locationName, startDate, roundedDisplayName,
                                    minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                        } else {
                            if (minyan.getType().equals(MinyanType.MINCHA) && startDate.before(zmanimtoday.get(Zman.SHEKIYA))
                                    && (startDate.after(mgMinusOneMinute.getTime())
                                    || (startDate.equals(mgMinusOneMinute.getTime())))) {
                                nextMinyan.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                        organizationNusach, organizationId, locationName, startDate, roundedDisplayName,
                                        minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                            } else {
                                if (minyan.getType().equals(MinyanType.MAARIV)&& (startDate.after(shekiyaMinusOneMinute.getTime())
                                        || startDate.equals((shekiyaMinusOneMinute.getTime())) || roundedDisplayName.contains("plag"))) {
                                    nextMinyan.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                            organizationNusach, organizationId, locationName, startDate,
                                            roundedDisplayName,
                                            minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                                }
                                else {
                                    if (minyan.getType().equals(MinyanType.SELICHOS) && isSelichosRecited){
                                        minyanEvents.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                                    organizationNusach, organizationId, locationName, startDate,
                                                    roundedDisplayName,
                                                    minyan.getNusach(), minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                                        }
                                    }
                            }
                        }
                    } else {
                        if (minyan.getType().equals(MinyanType.SHACHARIS) && startDate.before(zmanimtoday.get(Zman.SZT))
                                && startDate.after(zmanimtoday.get(Zman.ALOS_HASHACHAR))) {
                            nextMinyan.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                    organizationNusach,
                                    organizationId, locationName, startDate, minyan.getNusach(),
                                    minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                        } else {
                            if (minyan.getType().equals(MinyanType.MINCHA) && startDate.before(zmanimtoday.get(Zman.SHEKIYA))
                                    && startDate.after(zmanimtoday.get(Zman.MINCHA_GEDOLA))) {
                                nextMinyan.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                        organizationNusach,
                                        organizationId, locationName, startDate, minyan.getNusach(),
                                        minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                            } else {
                                if (minyan.getType().equals(MinyanType.MAARIV) && (startDate.after(shekiyaMinusOneMinute.getTime())
                                        || startDate.equals((shekiyaMinusOneMinute.getTime())))) {
                                    nextMinyan.add(new MinyanEvent(minyan.getId(), minyan.getType(), organizationName,
                                            organizationNusach,
                                            organizationId, locationName, startDate, minyan.getNusach(),
                                            minyan.getNotes(), organizationColor, minyan.getWhatsapp()));
                                }
                                else {
                                    if (minyan.getType().equals(MinyanType.SELICHOS) && isSelichosRecited){
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
        } // End of if (!useCalendarImport) for nextMinyan
        
        nextMinyan.sort(Comparator.comparing(MinyanEvent::getStartTime));
        
        // Populate organization slugs for next minyan events
        populateOrganizationSlugs(nextMinyan);
        // Populate organization slugs for all minyan events
        populateOrganizationSlugs(minyanEvents);
        
        mv.getModel().put("nextMinyan", nextMinyan);

        if(!nextMinyan.isEmpty()) {
            MinyanEvent firstEvent = nextMinyan.get(0);
            mv.getModel().put("upcoming", firstEvent);
        }
        // end upcoming

        mv.getModel().put("shacharisMinyanim", shacharisMinyanim);
        mv.getModel().put("minchaMinyanim", minchaMinyanim);
        mv.getModel().put("maarivMinyanim", maarivMinyanim);

        // Add application settings for frontend use
        mv.getModel().put("mapboxAccessToken", settingsService.getMapboxAccessToken());
        mv.getModel().put("siteName", settingsService.getSiteName());
        mv.getModel().put("appColor", settingsService.getAppColor());

        // mv.getModel().put("usesLocations", minyanEvents.)

        return mv;
    }
    
    /**
     * Populates the organizationSlug field for all MinyanEvent objects.
     * This helper method looks up the organization by ID and sets the slug.
     * 
     * @param events List of MinyanEvent objects to populate
     */
    private void populateOrganizationSlugs(List<MinyanEvent> events) {
        for (MinyanEvent event : events) {
            Optional<Organization> org = organizationDAO.findById(event.getOrganizationId());
            if (org.isPresent()) {
                String slug = org.get().getUrlSlug();
                // Fall back to org ID if slug is not set
                event.setOrganizationSlug(slug != null && !slug.isEmpty() ? slug : event.getOrganizationId());
            } else {
                // Fall back to org ID if organization not found
                event.setOrganizationSlug(event.getOrganizationId());
            }
        }
    }
    
    /**
     * Populates the organizationSlug field for all KolhaMinyanim objects.
     * This helper method looks up the organization by ID and sets the slug.
     * 
     * @param minyanims List of KolhaMinyanim objects to populate
     */
    private void populateOrganizationSlugsForKolha(List<KolhaMinyanim> minyanims) {
        for (KolhaMinyanim minyanim : minyanims) {
            Optional<Organization> org = organizationDAO.findById(minyanim.getOrganizationId());
            if (org.isPresent()) {
                String slug = org.get().getUrlSlug();
                // Fall back to org ID if slug is not set
                minyanim.setOrganizationSlug(slug != null && !slug.isEmpty() ? slug : minyanim.getOrganizationId());
            } else {
                // Fall back to org ID if organization not found
                minyanim.setOrganizationSlug(minyanim.getOrganizationId());
            }
        }
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
