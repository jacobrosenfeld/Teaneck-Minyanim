package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.model.Notification;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.security.AdminAction;
import com.tbdev.teaneckminyanim.service.NotificationService;
import com.tbdev.teaneckminyanim.service.NotificationService.NotificationCommand;
import com.tbdev.teaneckminyanim.service.NotificationService.NotificationNotFoundException;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.VersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
        return tnmUserService.isSuperAdmin();
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
        if (!isSuperAdmin()) {
            throw new AccessDeniedException("This page is restricted to super admins.");
        }
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
     * Create a new notification (super admins only; enforced in the service).
     */
    @AdminAction("notification.create")
    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<?> createNotification(
            @RequestParam String title,
            @RequestParam String message,
            @RequestParam String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime expirationDate,
            @RequestParam(required = false) Integer maxDisplays) {
        return run("creating", () -> ResponseEntity.ok(notificationService.create(
                NotificationCommand.full(title, message, type, true, expirationDate, maxDisplays))));
    }

    /**
     * Update an existing notification
     */
    @AdminAction("notification.update")
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
        return run("updating", () -> ResponseEntity.ok(notificationService.update(id,
                NotificationCommand.full(title, message, type, enabled, expirationDate, maxDisplays))));
    }

    /**
     * Toggle notification enabled status
     */
    @AdminAction("notification.toggle")
    @PostMapping("/toggle/{id}")
    @ResponseBody
    public ResponseEntity<?> toggleNotification(@PathVariable String id) {
        return run("toggling", () -> ResponseEntity.ok(notificationService.toggleEnabled(id)));
    }

    /**
     * Delete a notification
     */
    @AdminAction("notification.delete")
    @PostMapping("/delete/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteNotification(@PathVariable String id) {
        return run("deleting", () -> {
            notificationService.delete(id);
            return ResponseEntity.ok().build();
        });
    }

    /** Maps service errors to plain-text responses the page shows in a toast. */
    private ResponseEntity<?> run(String verb, java.util.function.Supplier<ResponseEntity<?>> action) {
        try {
            return action.get();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only super admins can manage notifications.");
        } catch (NotificationNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Error {} notification", verb, e);
            return ResponseEntity.badRequest().body("Error " + verb + " notification.");
        }
    }
}
