package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.text.SimpleDateFormat;
import java.util.Date;

@Controller
@RequiredArgsConstructor
public class LoginController {
    
    private final ApplicationSettingsService settingsService;
    
    @ModelAttribute("siteName")
    public String siteName() {
        return settingsService.getSiteName();
    }
    
    @ModelAttribute("supportEmail")
    public String supportEmail() {
        return settingsService.getSupportEmail();
    }
    
    @GetMapping("/admin/login")
    public ModelAndView login(@RequestParam(value = "error",required = false) String error, @RequestParam(value = "logout",	required = false) boolean logout) {
        System.out.println("LoginController.login() called");
        ModelAndView mv = new ModelAndView();
        mv.setViewName("admin/login");

        // Add current time for navbar display
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM d, yyyy h:mm a");
        Date datenow = new Date();
        String timenow = dateFormat.format(datenow);
        mv.getModel().put("timenow", timenow);

        if (logout) {
            System.out.println("Logging out...");
            SecurityContextHolder.getContext().setAuthentication(null);
            System.out.println("Logout Successful");
            mv.getModel().put("logout", true);
        }

        if (error != null && !error.isEmpty()) {
            mv.getModel().put("error", error);
        }

        return mv;
    }

    @GetMapping("/login")
    public ModelAndView loginShortcut(@RequestParam(value = "error",required = false) String error, @RequestParam(value = "logout",	required = false) boolean logout) {
        return login(error, logout);
    }
}
