package com.tbdev.teaneckminyanim.front.controllers;

import com.tbdev.teaneckminyanim.admin.structure.settings.TNMSettings;
import com.tbdev.teaneckminyanim.admin.structure.settings.TNMSettingsDAO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@Controller
public class Setting {

    @Autowired
    private TNMSettingsDAO tnmsettingsDAO;

    @ModelAttribute("settings")
    public List<TNMSettings> settings() {
        // Load and return the settings here
        List<TNMSettings> settings = this.tnmsettingsDAO.getAll();
        Collections.sort(settings, Comparator.comparing(TNMSettings::getId)); // sort by id
        return settings;
    }
}