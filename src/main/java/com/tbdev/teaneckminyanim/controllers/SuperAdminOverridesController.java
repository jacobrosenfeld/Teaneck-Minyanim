package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.enums.EventSource;
import com.tbdev.teaneckminyanim.enums.Nusach;
import com.tbdev.teaneckminyanim.minyan.MinyanType;
import com.tbdev.teaneckminyanim.model.CalendarEvent;
import com.tbdev.teaneckminyanim.model.Location;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.repo.CalendarEventRepository;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.LocationService;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import com.tbdev.teaneckminyanim.service.SuperAdminOverrideXlsxService;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
public class SuperAdminOverridesController {

    private final TNMUserService userService;
    private final ApplicationSettingsService settingsService;
    private final OrganizationService organizationService;
    private final LocationService locationService;
    private final CalendarEventRepository calendarEventRepository;
    private final SuperAdminOverrideXlsxService superAdminOverrideXlsxService;

    @ModelAttribute("siteName")
    public String siteName() {
        return settingsService.getSiteName();
    }

    @ModelAttribute("supportEmail")
    public String supportEmail() {
        return settingsService.getSupportEmail();
    }

    @ModelAttribute("allOrganizations")
    public List<Organization> allOrganizations() {
        if (!userService.isSuperAdmin()) return List.of();
        List<Organization> orgs = organizationService.getAll();
        orgs.sort(Comparator.comparing(Organization::getName, String.CASE_INSENSITIVE_ORDER));
        return orgs;
    }

    @ModelAttribute("date")
    public String currentDate() {
        SimpleDateFormat fmt = new SimpleDateFormat("MMMM d, yyyy | hh:mm aa");
        fmt.setTimeZone(settingsService.getTimeZone());
        return fmt.format(new Date());
    }

    @GetMapping("/admin/super/overrides")
    public ModelAndView superOverridesPage(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        requireSuperAdmin();

        ModelAndView mv = new ModelAndView("admin/super-overrides");
        mv.addObject("user", userService.getCurrentUser());

        LocalDate today = LocalDate.now();
        LocalDate effectiveStartDate = startDate != null ? startDate : today.minusDays(7);
        LocalDate effectiveEndDate = endDate != null ? endDate : today.plusDays(60);

        List<Organization> organizations = organizationService.getAll();
        organizations.sort(Comparator.comparing(Organization::getName, String.CASE_INSENSITIVE_ORDER));

        Map<String, String> orgNames = organizations.stream()
                .collect(Collectors.toMap(Organization::getId, Organization::getName));

        Map<String, List<Location>> locationsByOrgId = new LinkedHashMap<>();
        for (Organization org : organizations) {
            List<Location> locations = locationService.findMatching(org.getId())
                    .stream()
                    .sorted(Comparator.comparing(Location::getName, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
            locationsByOrgId.put(org.getId(), locations);
        }

        List<CalendarEvent> manualEvents = calendarEventRepository.findBySourceAndDateBetween(
                        EventSource.MANUAL, effectiveStartDate, effectiveEndDate)
                .stream()
                .sorted(Comparator.comparing(CalendarEvent::getDate).thenComparing(CalendarEvent::getStartTime))
                .collect(Collectors.toList());

        mv.addObject("organizations", organizations);
        mv.addObject("orgNames", orgNames);
        mv.addObject("locationsByOrgId", locationsByOrgId);
        mv.addObject("manualEvents", manualEvents);
        mv.addObject("startDate", effectiveStartDate);
        mv.addObject("endDate", effectiveEndDate);
        mv.addObject("totalManualEvents", manualEvents.size());
        mv.addObject("nusachOptions", Nusach.values());
        mv.addObject("newOverrideStartDate", today);
        mv.addObject("newOverrideEndDate", today);

        return mv;
    }

    @PostMapping("/admin/super/overrides/manual")
    public RedirectView createManualOverride(
            @RequestParam String organizationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam @DateTimeFormat(pattern = "HH:mm") LocalTime startTime,
            @RequestParam String minyanType,
            @RequestParam(defaultValue = "ADDITIVE") String overrideMode,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) String locationId,
            @RequestParam(required = false) String locationName,
            @RequestParam(required = false) String nusach,
            RedirectAttributes redirectAttributes) {
        requireSuperAdmin();

        try {
            Optional<Organization> orgOpt = organizationService.findById(organizationId);
            if (orgOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Organization not found.");
                return new RedirectView("/admin/super/overrides");
            }
            Organization org = orgOpt.get();

            LocalDate rangeEnd = endDate != null ? endDate : startDate;
            if (rangeEnd.isBefore(startDate)) {
                redirectAttributes.addFlashAttribute("errorMessage", "End date cannot be before start date.");
                return new RedirectView("/admin/super/overrides");
            }

            MinyanType parsedType;
            try {
                parsedType = MinyanType.valueOf(minyanType);
            } catch (IllegalArgumentException ex) {
                redirectAttributes.addFlashAttribute("errorMessage", "Invalid minyan type.");
                return new RedirectView("/admin/super/overrides");
            }

            String normalizedMode = "FULL_DAY_REPLACE".equalsIgnoreCase(overrideMode)
                    ? "FULL_DAY_REPLACE"
                    : "ADDITIVE";

            String resolvedLocationId = null;
            String resolvedLocationName = trimToNull(locationName);
            if (locationId != null && !locationId.isBlank()) {
                Location selectedLocation = locationService.findById(locationId);
                if (selectedLocation == null || !organizationId.equals(selectedLocation.getOrganizationId())) {
                    redirectAttributes.addFlashAttribute("errorMessage", "Invalid location for selected organization.");
                    return new RedirectView("/admin/super/overrides");
                }
                resolvedLocationId = selectedLocation.getId();
                if (resolvedLocationName == null) {
                    resolvedLocationName = selectedLocation.getName();
                }
            }

            Nusach resolvedNusach = org.getNusach();
            if (nusach != null && !nusach.isBlank()) {
                try {
                    resolvedNusach = Nusach.valueOf(nusach.toUpperCase(Locale.US));
                } catch (IllegalArgumentException ex) {
                    redirectAttributes.addFlashAttribute("errorMessage", "Invalid nusach.");
                    return new RedirectView("/admin/super/overrides");
                }
            }

            String username = userService.getCurrentUser() != null
                    ? userService.getCurrentUser().getUsername()
                    : "super-admin";

            int createdCount = 0;
            int updatedCount = 0;
            for (LocalDate d = startDate; !d.isAfter(rangeEnd); d = d.plusDays(1)) {
                boolean updated = superAdminOverrideXlsxService.upsertManualEvent(
                        organizationId,
                        d,
                        startTime,
                        parsedType,
                        normalizedMode,
                        resolvedLocationId,
                        resolvedLocationName,
                        trimToNull(notes),
                        resolvedNusach,
                        true,
                        username
                );
                if (updated) {
                    updatedCount++;
                } else {
                    createdCount++;
                }
            }

            redirectAttributes.addFlashAttribute("successMessage",
                    "Manual overrides saved (" + ("FULL_DAY_REPLACE".equals(normalizedMode) ? "full-day replace" : "additive")
                            + "). Created: " + createdCount + ", Updated: " + updatedCount + ".");
        } catch (Exception e) {
            log.error("Error creating super-admin manual overrides", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Save failed: " + e.getMessage());
        }

        return new RedirectView("/admin/super/overrides");
    }

    @GetMapping("/admin/super/overrides/template.xlsx")
    public ResponseEntity<byte[]> downloadSuperOverridesTemplate() throws Exception {
        requireSuperAdmin();

        byte[] bytes = superAdminOverrideXlsxService.buildSuperAdminTemplate(organizationService.getAll());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"super-admin-overrides-template.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @PostMapping("/admin/super/overrides/import")
    public RedirectView importSuperOverrides(
            @RequestParam("file") MultipartFile file,
            RedirectAttributes redirectAttributes) {
        requireSuperAdmin();

        try {
            String username = userService.getCurrentUser() != null
                    ? userService.getCurrentUser().getUsername()
                    : "super-admin";

            SuperAdminOverrideXlsxService.ImportResult result =
                    superAdminOverrideXlsxService.importSuperAdminWorkbook(file, username);

            if (result.hasErrors()) {
                String topErrors = result.getErrors().stream().limit(3).collect(Collectors.joining(" | "));
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Import finished with issues. Created: " + result.getCreatedCount()
                                + ", Updated: " + result.getUpdatedCount()
                                + ", Errors: " + result.getErrors().size()
                                + ". " + topErrors);
            } else {
                redirectAttributes.addFlashAttribute("successMessage",
                        "XLSX import complete. Created: " + result.getCreatedCount()
                                + ", Updated: " + result.getUpdatedCount()
                                + ", Replaced manual rows: " + result.getDeletedManualCount() + ".");
            }
        } catch (Exception e) {
            log.error("Super-admin XLSX override import failed", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Import failed: " + e.getMessage());
        }

        return new RedirectView("/admin/super/overrides");
    }

    @PostMapping("/admin/super/overrides/{eventId}/delete")
    public RedirectView deleteManualOverride(
            @PathVariable Long eventId,
            RedirectAttributes redirectAttributes) {
        requireSuperAdmin();

        try {
            Optional<CalendarEvent> eventOpt = calendarEventRepository.findById(eventId);
            if (eventOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Manual override not found.");
                return new RedirectView("/admin/super/overrides");
            }
            CalendarEvent event = eventOpt.get();
            if (event.getSource() != EventSource.MANUAL) {
                redirectAttributes.addFlashAttribute("errorMessage", "Only MANUAL overrides can be deleted here.");
                return new RedirectView("/admin/super/overrides");
            }

            calendarEventRepository.delete(event);
            redirectAttributes.addFlashAttribute("successMessage", "Manual override deleted.");
        } catch (Exception e) {
            log.error("Error deleting manual override {}", eventId, e);
            redirectAttributes.addFlashAttribute("errorMessage", "Delete failed: " + e.getMessage());
        }

        return new RedirectView("/admin/super/overrides");
    }

    @PostMapping("/admin/super/overrides/{eventId}/toggle")
    public RedirectView toggleManualOverride(
            @PathVariable Long eventId,
            RedirectAttributes redirectAttributes) {
        requireSuperAdmin();

        try {
            Optional<CalendarEvent> eventOpt = calendarEventRepository.findById(eventId);
            if (eventOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Manual override not found.");
                return new RedirectView("/admin/super/overrides");
            }
            CalendarEvent event = eventOpt.get();
            if (event.getSource() != EventSource.MANUAL) {
                redirectAttributes.addFlashAttribute("errorMessage", "Only MANUAL overrides can be toggled here.");
                return new RedirectView("/admin/super/overrides");
            }

            event.setEnabled(!event.isEnabled());
            calendarEventRepository.save(event);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Manual override " + (event.isEnabled() ? "enabled." : "disabled."));
        } catch (Exception e) {
            log.error("Error toggling manual override {}", eventId, e);
            redirectAttributes.addFlashAttribute("errorMessage", "Toggle failed: " + e.getMessage());
        }

        return new RedirectView("/admin/super/overrides");
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void requireSuperAdmin() {
        if (!userService.isSuperAdmin()) {
            throw new AccessDeniedException("This page is restricted to super admins.");
        }
    }
}
