package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.model.Notification;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.NotificationService;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.VersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Controller
@RequestMapping("/admin/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;
    private final TNMUserService tnmUserService;
    private final ApplicationSettingsService applicationSettingsService;
    private final VersionService versionService;
    private final OrganizationService organizationService;

    /**
     * Get current authenticated user
     */
    private TNMUser getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return tnmUserService.findByName(username);
    }
    
    /**
     * Check if current user is super admin
     */
    private boolean isSuperAdmin() {
        TNMUser user = getCurrentUser();
        return user.getOrganizationId() == null && user.isAdmin();
    }

    /**
     * Provide site name for all views
     */
    @ModelAttribute("siteName")
    public String siteName() {
        return applicationSettingsService.getSiteName();
    }

    /**
     * Provide app color for all views
     */
    @ModelAttribute("appColor")
    public String appColor() {
        return applicationSettingsService.getAppColor();
    }

    /**
     * Provide app version for all views
     */
    @ModelAttribute("appVersion")
    public String appVersion() {
        return versionService.getVersion();
    }

    /**
     * Show notifications management page
     */
    @GetMapping
    public ModelAndView showNotifications() {
        ModelAndView mv = new ModelAndView("admin/notifications");
        
        // Add user to model for sidebar
        TNMUser currentUser = getCurrentUser();
        mv.addObject("user", currentUser);
        
        // Add all organizations for super admin dropdown (sorted alphabetically)
        if (isSuperAdmin()) {
            List<Organization> organizations = organizationService.getAll();
            organizations.sort(Comparator.comparing(Organization::getName, String.CASE_INSENSITIVE_ORDER));
            mv.addObject("allOrganizations", organizations);
        }
        
        List<Notification> allNotifications = notificationService.getAll();
        mv.addObject("notifications", allNotifications);
        
        return mv;
    }

    /**
     * Create a new notification
     */
    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<?> createNotification(
            @RequestParam String title,
            @RequestParam String message,
            @RequestParam String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime expirationDate,
            @RequestParam(required = false) Integer maxDisplays) {
        
        try {
            Notification notification = new Notification();
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setType(type);
            notification.setEnabled(true);
            notification.setExpirationDate(expirationDate);
            notification.setMaxDisplays(maxDisplays);
            
            Notification saved = notificationService.save(notification);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            log.error("Error creating notification", e);
            return ResponseEntity.badRequest().body("Error creating notification: " + e.getMessage());
        }
    }

    /**
     * Update an existing notification
     */
    @PostMapping("/update/{id}")
    @ResponseBody
    public ResponseEntity<?> updateNotification(
            @PathVariable String id,
            @RequestParam String title,
            @RequestParam String message,
            @RequestParam String type,
            @RequestParam Boolean enabled,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime expirationDate,
            @RequestParam(required = false) Integer maxDisplays) {
        
        try {
            Notification notification = notificationService.findById(id);
            if (notification == null) {
                return ResponseEntity.notFound().build();
            }
            
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setType(type);
            notification.setEnabled(enabled);
            notification.setExpirationDate(expirationDate);
            notification.setMaxDisplays(maxDisplays);
            
            Notification saved = notificationService.save(notification);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            log.error("Error updating notification", e);
            return ResponseEntity.badRequest().body("Error updating notification: " + e.getMessage());
        }
    }

    /**
     * Toggle notification enabled status
     */
    @PostMapping("/toggle/{id}")
    @ResponseBody
    public ResponseEntity<?> toggleNotification(@PathVariable String id) {
        try {
            boolean enabled = notificationService.toggleEnabled(id);
            return ResponseEntity.ok(enabled);
        } catch (Exception e) {
            log.error("Error toggling notification", e);
            return ResponseEntity.badRequest().body("Error toggling notification: " + e.getMessage());
        }
    }

    /**
     * Delete a notification
     */
    @PostMapping("/delete/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteNotification(@PathVariable String id) {
        try {
            notificationService.delete(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error deleting notification", e);
            return ResponseEntity.badRequest().body("Error deleting notification: " + e.getMessage());
        }
    }
}
