package com.tbdev.teaneckminyanim.controllers;

import java.time.LocalDate;
import java.util.*;

import com.kosherjava.zmanim.util.GeoLocation;
import com.tbdev.teaneckminyanim.service.TNMSettingsService;
import com.tbdev.teaneckminyanim.model.TNMSettings;
import com.tbdev.teaneckminyanim.service.ZmanimHandler;
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
    private final TNMSettingsService tnmSettingsDao;

    TimeZone timeZone = TimeZone.getTimeZone("America/New_York");

    String locationName = "Teaneck, NJ";
    double latitude = 40.906871;
    double longitude = -74.020924;
    double elevation = 24;
    GeoLocation geoLocation = new GeoLocation(locationName, latitude, longitude, elevation, timeZone);

    ZmanimHandler zmanimHandler = new ZmanimHandler(geoLocation);

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

    @GetMapping("/checkAseresYemeiTeshuva")
    public String checkAseresYemeiTeshuva(Model model) {
        boolean isAseresYemeiTeshuva = zmanimHandler.isAseresYemeiTeshuva();
        model.addAttribute("isAseresYemeiTeshuva", isAseresYemeiTeshuva);
        return "checkAseresYemeiTeshuva";
    }

    @GetMapping("/checkSelichos")
    public String checkSelichos(Model model) {
        LocalDate date = LocalDate.now(); // or any specific date you want to check
        boolean isSelichosRecited = zmanimHandler.isSelichosRecited(date);
        model.addAttribute("isSelichosRecited", isSelichosRecited);
        return "checkSelichos";
    }

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
