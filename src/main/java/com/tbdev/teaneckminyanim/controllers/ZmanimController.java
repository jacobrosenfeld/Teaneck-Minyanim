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

import com.tbdev.teaneckminyanim.service.ZmanimService;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class ZmanimController {
    private final ZmanimService zmanimService;

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
