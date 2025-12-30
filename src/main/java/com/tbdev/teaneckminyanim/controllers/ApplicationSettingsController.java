package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.enums.SettingKey;
import com.tbdev.teaneckminyanim.model.ApplicationSettings;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.VersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import java.util.List;
import java.util.Map;

/**
 * Controller for managing application-wide settings.
 * Only accessible to super administrators.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ApplicationSettingsController {

    private final ApplicationSettingsService settingsService;
    private final TNMUserService userService;
    private final VersionService versionService;

    /**
     * Display the application settings page.
     */
    @GetMapping("/admin/application-settings")
    public ModelAndView showSettings(
            @RequestParam(required = false) String success,
            @RequestParam(required = false) String error) {
        
        if (!isSuperAdmin()) {
            throw new AccessDeniedException("You are not authorized to access this page");
        }

        ModelAndView mv = new ModelAndView("admin/application-settings");

        // Get settings grouped by category
        Map<String, List<ApplicationSettings>> settingsByCategory = 
                settingsService.getSettingsByCategory();
        
        mv.addObject("settingsByCategory", settingsByCategory);
        mv.addObject("user", getCurrentUser());
        mv.addObject("appVersion", versionService.getVersion());
        mv.addObject("success", success);
        mv.addObject("error", error);

        return mv;
    }

    /**
     * Update a single application setting.
     */
    @PostMapping("/admin/application-settings/update")
    public ModelAndView updateSetting(
            @RequestParam("settingKey") String settingKey,
            @RequestParam("settingValue") String settingValue,
            RedirectAttributes redirectAttributes) {
        
        if (!isSuperAdmin()) {
            throw new AccessDeniedException("You are not authorized to perform this action");
        }

        try {
            // Update the setting
            settingsService.updateSettingByKey(settingKey, settingValue);
            
            log.info("Setting updated: {} = {} by user {}", 
                    settingKey, settingValue, getCurrentUser().getUsername());
            
            redirectAttributes.addAttribute("success", 
                    "Setting updated successfully!");
            
        } catch (ApplicationSettingsService.ValidationException e) {
            log.warn("Validation error updating setting {}: {}", settingKey, e.getMessage());
            redirectAttributes.addAttribute("error", 
                    "Validation error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error updating setting {}: {}", settingKey, e.getMessage(), e);
            redirectAttributes.addAttribute("error", 
                    "Error updating setting: " + e.getMessage());
        }

        return new ModelAndView(new RedirectView("/admin/application-settings", true));
    }

    /**
     * Refresh settings cache (force reload from database).
     */
    @PostMapping("/admin/application-settings/refresh-cache")
    public ModelAndView refreshCache(RedirectAttributes redirectAttributes) {
        
        if (!isSuperAdmin()) {
            throw new AccessDeniedException("You are not authorized to perform this action");
        }

        try {
            settingsService.refreshCache();
            log.info("Settings cache refreshed by user {}", getCurrentUser().getUsername());
            redirectAttributes.addAttribute("success", "Settings cache refreshed successfully!");
        } catch (Exception e) {
            log.error("Error refreshing settings cache: {}", e.getMessage(), e);
            redirectAttributes.addAttribute("error", "Error refreshing cache: " + e.getMessage());
        }

        return new ModelAndView(new RedirectView("/admin/application-settings", true));
    }

    private boolean isSuperAdmin() {
        TNMUser user = getCurrentUser();
        return user != null && user.getOrganizationId() == null && user.role().equals(com.tbdev.teaneckminyanim.enums.Role.ADMIN);
    }

    private TNMUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        return userService.findByName(auth.getName());
    }
}
