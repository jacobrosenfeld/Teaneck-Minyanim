package com.tbdev.teaneckminyanim.front.controllers;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.tbdev.teaneckminyanim.admin.structure.settings.TNMSettings;
import com.tbdev.teaneckminyanim.admin.structure.settings.TNMSettingsDAO;
import com.tbdev.teaneckminyanim.service.ZmanimService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequiredArgsConstructor
public class ZmanimController {
    private final ZmanimService zmanimService;
    private final TNMSettingsDAO tnmSettingsDao;

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

    @ModelAttribute("settings")
    public List<TNMSettings> settings() {
        // Load and return the settings here
        List<TNMSettings> settings = tnmSettingsDao.getAll();
        Collections.sort(settings, Comparator.comparing(TNMSettings::getId)); // sort by id
        return settings;
    }

    @GetMapping("/checkAseresYemeiTeshuva") // @TODO: aseres won't work as it's own check as it looks at current date not queried date
    public String checkAseresYemeiTeshuva(Model model) {
        boolean isAseresYemeiTeshuva = zmanimService.isAseresYemeiTeshuva();
        model.addAttribute("isAseresYemeiTeshuva", isAseresYemeiTeshuva);
        return "checkAseresYemeiTeshuva";
    }

    @GetMapping("/checkSelichos")
    public String checkSelichos(Model model) {
        LocalDate date = LocalDate.now(); // or any specific date you want to check
        boolean isSelichosRecited = zmanimService.isSelichosRecited(date);
        model.addAttribute("isSelichosRecited", isSelichosRecited);
        return "checkSelichos";
    }

//    private void setTimeZone(TimeZone tz) {
//        // set time format
//        timeFormat.setTimeZone(tz);
//        dateFormat.setTimeZone(tz);
//        onlyDateFormat.setTimeZone(tz);
//        strippedDayFormat.setTimeZone(tz);
//    }

    @GetMapping("/zmanim")
    public ModelAndView todaysZmanim() {
        System.out.println("Displaying today's zmanim...");
        return zmanimService.getZmanim(new Date());
    }

    @GetMapping("/zmanim/next")
    public ModelAndView nextZmanimAfter(@RequestParam(value = "after", required = true) String dateString) {
        Date date = new Date(dateString);
        return zmanimService.getZmanim(new Date(date.getYear(), date.getMonth(), date.getDate() + 1, date.getHours(),
                date.getMinutes(), date.getSeconds()));
    }

    @GetMapping("/zmanim/last")
    public ModelAndView lastZmanimBefore(@RequestParam(value = "before", required = true) String dateString) {
        Date date = new Date(dateString);
        return zmanimService.getZmanim(new Date(date.getYear(), date.getMonth(), date.getDate() - 1, date.getHours(),
                date.getMinutes(), date.getSeconds()));
    }

    @GetMapping("/orgs/{id}/next")
    public ModelAndView nextOrgAfter(@PathVariable String id,
            @RequestParam(value = "after", required = true) String dateString) throws Exception {
        Date date = new Date(dateString);
        return zmanimService.org(id, new Date(date.getYear(), date.getMonth(), date.getDate() + 1, date.getHours(),
                date.getMinutes(), date.getSeconds()));
    }

    @GetMapping("/orgs/{id}/last")
    public ModelAndView lastOrgBefore(@PathVariable String id,
            @RequestParam(value = "before", required = true) String dateString) throws Exception {
        Date date = new Date(dateString);
        return zmanimService.org(id, new Date(date.getYear(), date.getMonth(), date.getDate() - 1, date.getHours(),
                date.getMinutes(), date.getSeconds()));
    }

    @RequestMapping("/orgs/{orgId}")
    public ModelAndView orgToday(@PathVariable String orgId) throws Exception {
        return zmanimService.org(orgId, new Date());
    }
}
