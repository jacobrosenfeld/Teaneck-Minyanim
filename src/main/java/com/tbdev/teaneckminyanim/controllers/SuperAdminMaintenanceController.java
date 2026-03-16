package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.repo.OrganizationCalendarEntryRepository;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.CalendarMaterializationScheduler;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.calendar.CalendarImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import java.util.List;
import java.util.Map;

/**
 * Super-admin-only maintenance panel.
 *
 * <p>Available at {@code /admin/super/maintenance}. All actions require SUPER_ADMIN role.</p>
 *
 * <p>Current tools:</p>
 * <ul>
 *   <li>Reimport All Calendars — deletes all calendar entries for every org that has a
 *       calendar URL and runs a fresh import so the classifier re-processes every event
 *       from scratch (fixes stale notes such as "Shkiya:" that should have been replaced
 *       during the original import).</li>
 *   <li>Rematerialize All — triggers a full rebuild of the {@code calendar_events} table
 *       from the current set of Minyan rules and imported calendar entries.</li>
 * </ul>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class SuperAdminMaintenanceController {

    private final CalendarImportService calendarImportService;
    private final CalendarMaterializationScheduler materializationScheduler;
    private final OrganizationService organizationService;
    private final TNMUserService userService;
    private final ApplicationSettingsService settingsService;
    private final OrganizationCalendarEntryRepository calendarEntryRepo;

    @ModelAttribute("siteName")
    public String siteName() {
        return settingsService.getSiteName();
    }

    @ModelAttribute("supportEmail")
    public String supportEmail() {
        return settingsService.getSupportEmail();
    }

    // -----------------------------------------------------------------------
    // View
    // -----------------------------------------------------------------------

    @GetMapping("/admin/super/maintenance")
    public ModelAndView maintenancePage() {
        requireSuperAdmin();

        ModelAndView mv = new ModelAndView("admin/maintenance");
        mv.addObject("user", userService.getCurrentUser());

        List<Organization> allOrgs = organizationService.getAll();
        long orgsWithCalendars = allOrgs.stream()
                .filter(o -> o.getCalendar() != null && !o.getCalendar().trim().isEmpty())
                .count();
        long totalCalendarEntries = calendarEntryRepo.count();
        long enabledCalendarEntries = allOrgs.stream()
                .mapToLong(o -> calendarEntryRepo.countByOrganizationIdAndEnabledTrue(o.getId()))
                .sum();

        mv.addObject("totalOrgs", allOrgs.size());
        mv.addObject("orgsWithCalendars", orgsWithCalendars);
        mv.addObject("totalCalendarEntries", totalCalendarEntries);
        mv.addObject("enabledCalendarEntries", enabledCalendarEntries);

        return mv;
    }

    // -----------------------------------------------------------------------
    // Actions
    // -----------------------------------------------------------------------

    /**
     * Deletes all calendar entries for every org that has a calendar URL, then
     * re-fetches and re-classifies each one from scratch.  This ensures the
     * classifier's current logic (including Plag vs. Shkiya note generation) is
     * applied uniformly to all stored data.
     */
    @PostMapping("/admin/super/maintenance/reimport-all")
    public RedirectView reimportAll(RedirectAttributes redirectAttributes) {
        requireSuperAdmin();

        log.info("Super admin {} triggered force-reimport-all",
                userService.getCurrentUser().getUsername());

        try {
            Map<String, CalendarImportService.ImportResult> results =
                    calendarImportService.forceReimportAllOrganizations();

            long succeeded = results.values().stream().filter(r -> r.success).count();
            long failed = results.values().stream().filter(r -> !r.success).count();
            long totalNew = results.values().stream().mapToLong(r -> r.newEntries).sum();

            String msg = "Reimport complete: %d org(s) succeeded, %d failed, %d total entries imported."
                    .formatted(succeeded, failed, totalNew);
            if (failed > 0) {
                String failedNames = results.entrySet().stream()
                        .filter(e -> !e.getValue().success)
                        .map(e -> e.getKey() + ": " + e.getValue().errorMessage)
                        .reduce((a, b) -> a + "; " + b).orElse("");
                redirectAttributes.addFlashAttribute("warningMessage", msg + " Failures: " + failedNames);
            } else {
                redirectAttributes.addFlashAttribute("successMessage", msg);
            }

        } catch (Exception e) {
            log.error("Force-reimport-all failed", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Reimport failed: " + e.getMessage());
        }

        return new RedirectView("/admin/super/maintenance");
    }

    /**
     * Triggers a full rebuild of the materialized {@code calendar_events} table.
     */
    @PostMapping("/admin/super/maintenance/rematerialize-all")
    public RedirectView rematerializeAll(RedirectAttributes redirectAttributes) {
        requireSuperAdmin();

        log.info("Super admin {} triggered rematerialize-all",
                userService.getCurrentUser().getUsername());

        try {
            materializationScheduler.triggerMaterialization();
            redirectAttributes.addFlashAttribute("successMessage",
                    "Full calendar rematerialization completed successfully.");
        } catch (Exception e) {
            log.error("Rematerialize-all failed", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Rematerialization failed: " + e.getMessage());
        }

        return new RedirectView("/admin/super/maintenance");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void requireSuperAdmin() {
        if (!userService.isSuperAdmin()) {
            throw new AccessDeniedException("This page is restricted to super admins.");
        }
    }
}
