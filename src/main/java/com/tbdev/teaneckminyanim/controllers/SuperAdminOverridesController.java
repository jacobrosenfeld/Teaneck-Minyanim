package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.enums.EventSource;
import com.tbdev.teaneckminyanim.model.CalendarEvent;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.repo.CalendarEventRepository;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
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

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
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

        List<CalendarEvent> manualEvents = calendarEventRepository.findBySourceAndDateBetween(
                        EventSource.MANUAL, effectiveStartDate, effectiveEndDate)
                .stream()
                .sorted(Comparator.comparing(CalendarEvent::getDate).thenComparing(CalendarEvent::getStartTime))
                .collect(Collectors.toList());

        mv.addObject("organizations", organizations);
        mv.addObject("orgNames", orgNames);
        mv.addObject("manualEvents", manualEvents);
        mv.addObject("startDate", effectiveStartDate);
        mv.addObject("endDate", effectiveEndDate);
        mv.addObject("totalManualEvents", manualEvents.size());

        return mv;
    }

    @GetMapping("/admin/super/overrides/template.xlsx")
    public ResponseEntity<byte[]> downloadSuperOverridesTemplate() throws Exception {
        requireSuperAdmin();

        byte[] bytes = superAdminOverrideXlsxService.buildTemplate(organizationService.getAll());
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
                    superAdminOverrideXlsxService.importWorkbook(file, username);

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

    private void requireSuperAdmin() {
        if (!userService.isSuperAdmin()) {
            throw new AccessDeniedException("This page is restricted to super admins.");
        }
    }
}
