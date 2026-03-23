package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.enums.EventSource;
import com.tbdev.teaneckminyanim.enums.Nusach;
import com.tbdev.teaneckminyanim.minyan.MinyanType;
import com.tbdev.teaneckminyanim.model.CalendarEvent;
import com.tbdev.teaneckminyanim.model.Location;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.repo.CalendarEventRepository;
import com.tbdev.teaneckminyanim.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import java.text.SimpleDateFormat;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin controller for managing materialized calendar events.
 * Provides UI for viewing, enabling/disabling, and editing calendar_events table.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class CalendarEventsAdminController {

    private final CalendarEventRepository calendarEventRepository;
    private final OrganizationService organizationService;
    private final LocationService locationService;
    private final CalendarMaterializationService materializationService;
    private final CalendarMaterializationScheduler materializationScheduler;
    private final TNMUserService userService;
    private final ApplicationSettingsService settingsService;
    private final EffectiveScheduleService effectiveScheduleService;
    private final ManualOverrideCsvImportService manualOverrideCsvImportService;

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
        if (!userService.isSuperAdmin()) return Collections.emptyList();
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

    private static final List<Integer> PAGE_SIZE_OPTIONS = List.of(25, 50, 100);
    private static final int DEFAULT_PAGE_SIZE = 25;

    /**
     * Master calendar view for super admin (all organizations)
     */
    @GetMapping("/admin/calendar-events/all")
    public ModelAndView viewAllCalendarEvents(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String organizationId,
            @RequestParam(required = false) String minyanType,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int pageSize) {

        ModelAndView mv = new ModelAndView("admin/calendar-events-all");

        // Add current user for sidebar
        mv.addObject("user", userService.getCurrentUser());

        // Check if super admin
        if (!userService.isSuperAdmin()) {
            throw new AccessDeniedException("Only super admins can access master calendar");
        }

        // Clamp pageSize to allowed options
        if (!PAGE_SIZE_OPTIONS.contains(pageSize)) pageSize = DEFAULT_PAGE_SIZE;
        if (page < 0) page = 0;

        // Default date range to materialization window
        CalendarMaterializationService.WindowBounds bounds = materializationService.getWindowBounds();
        LocalDate effectiveStartDate = startDate != null ? startDate : bounds.getStartDate();
        LocalDate effectiveEndDate = endDate != null ? endDate : bounds.getEndDate();

        // Get all organizations for filter dropdown and name lookup
        List<Organization> allOrgs = organizationService.getAll();
        mv.addObject("organizations", allOrgs);
        Map<String, String> orgNames = allOrgs.stream()
                .collect(Collectors.toMap(Organization::getId, Organization::getName));
        mv.addObject("orgNames", orgNames);

        // Query and collect all matching events
        List<CalendarEvent> allEvents;
        if (organizationId != null && !organizationId.isEmpty()) {
            allEvents = queryEventsWithFilters(organizationId, effectiveStartDate, effectiveEndDate,
                    minyanType, source, buildSort(sortBy, sortDir));
        } else {
            allEvents = new ArrayList<>();
            for (Organization org : allOrgs) {
                allEvents.addAll(queryEventsWithFilters(org.getId(), effectiveStartDate, effectiveEndDate,
                        minyanType, source, buildSort(sortBy, sortDir)));
            }
            allEvents.sort(getComparator(buildSort(sortBy, sortDir)));
        }

        // Statistics from the full filtered set
        long totalEvents    = allEvents.size();
        long rulesEvents    = allEvents.stream().filter(e -> e.getSource() == EventSource.RULES).count();
        long importedEvents = allEvents.stream().filter(e -> e.getSource() == EventSource.IMPORTED).count();
        long manualEvents   = allEvents.stream().filter(e -> e.getSource() == EventSource.MANUAL).count();

        mv.addObject("totalEvents", totalEvents);
        mv.addObject("rulesEvents", rulesEvents);
        mv.addObject("importedEvents", importedEvents);
        mv.addObject("manualEvents", manualEvents);

        // Pagination
        int totalPages = totalEvents == 0 ? 1 : (int) Math.ceil((double) totalEvents / pageSize);
        if (page >= totalPages) page = totalPages - 1;
        int fromIndex = page * pageSize;
        int toIndex   = (int) Math.min(fromIndex + pageSize, totalEvents);
        List<CalendarEvent> pageEvents = fromIndex < totalEvents
                ? allEvents.subList(fromIndex, toIndex) : Collections.emptyList();

        mv.addObject("events", pageEvents);
        mv.addObject("currentPage", page);
        mv.addObject("pageSize", pageSize);
        mv.addObject("totalPages", totalPages);
        mv.addObject("totalCount", totalEvents);
        mv.addObject("fromIndex", totalEvents == 0 ? 0 : fromIndex + 1);
        mv.addObject("toIndex", toIndex);
        mv.addObject("pageSizeOptions", PAGE_SIZE_OPTIONS);

        // Filter/sort state for form persistence and pagination links
        mv.addObject("startDate", effectiveStartDate);
        mv.addObject("endDate", effectiveEndDate);
        mv.addObject("organizationFilter", organizationId);
        mv.addObject("minyanTypeFilter", minyanType);
        mv.addObject("sourceFilter", source);
        mv.addObject("sortBy", sortBy != null ? sortBy : "date");
        mv.addObject("sortDir", sortDir != null ? sortDir : "asc");

        mv.addObject("minyanTypes", MinyanType.values());
        mv.addObject("eventSources", EventSource.values());
        mv.addObject("windowBounds", bounds);

        return mv;
    }

    /**
     * Display calendar events for an organization with filters
     */
    @GetMapping("/admin/{orgId}/calendar-events")
    public ModelAndView viewCalendarEvents(
            @PathVariable String orgId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String minyanType,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {

        ModelAndView mv = new ModelAndView("admin/calendar-events");
        
        // Add current user for sidebar
        mv.addObject("user", userService.getCurrentUser());
        
        // Check authorization
        if (!userService.canAccessOrganization(orgId)) {
            throw new AccessDeniedException("Not authorized to access this organization");
        }
        
        // Load organization
        Optional<Organization> orgOpt = organizationService.findById(orgId);
        if (orgOpt.isEmpty()) {
            mv.setViewName("error/404");
            return mv;
        }
        Organization org = orgOpt.get();
        mv.addObject("organization", org);
        
        // Default to current date through 2 weeks ahead (compact preview window)
        LocalDate today = LocalDate.now();
        LocalDate effectiveStartDate = startDate != null ? startDate : today;
        LocalDate effectiveEndDate = endDate != null ? endDate : today.plusDays(13);

        // Build sort (always date+time ascending for schedule view)
        Sort sort = buildSort(null, null);

        // Query effective events
        List<CalendarEvent> events = queryEventsWithFilters(
                orgId, effectiveStartDate, effectiveEndDate, minyanType, source, sort);

        // Group by date for compact schedule view
        java.util.LinkedHashMap<LocalDate, List<CalendarEvent>> eventsByDate = events.stream()
                .collect(Collectors.groupingBy(CalendarEvent::getDate,
                        java.util.LinkedHashMap::new, Collectors.toList()));

        // Track which dates have imported overrides (for visual indicator)
        java.util.Set<LocalDate> importedDates = events.stream()
                .filter(e -> e.getSource() == EventSource.IMPORTED)
                .map(CalendarEvent::getDate)
                .collect(Collectors.toSet());

        mv.addObject("eventsByDate", eventsByDate);
        mv.addObject("importedDates", importedDates);
        mv.addObject("startDate", effectiveStartDate);
        mv.addObject("endDate", effectiveEndDate);
        mv.addObject("minyanTypeFilter", minyanType);
        mv.addObject("sourceFilter", source);
        mv.addObject("minyanTypes", MinyanType.values());
        mv.addObject("eventSources", EventSource.values());
        mv.addObject("windowBounds", materializationService.getWindowBounds());

        // Stats
        long totalDays = eventsByDate.size();
        long importedDaysCount = importedDates.size();
        long rulesDaysCount = totalDays - importedDaysCount;

        mv.addObject("totalDays", totalDays);
        mv.addObject("importedDaysCount", importedDaysCount);
        mv.addObject("rulesDaysCount", rulesDaysCount);

        return mv;
    }

    /**
     * Dedicated manual overrides page.
     */
    @GetMapping("/admin/{orgId}/overrides")
    public ModelAndView viewManualOverrides(
            @PathVariable String orgId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        ModelAndView mv = new ModelAndView("admin/manual-overrides");
        mv.addObject("user", userService.getCurrentUser());

        if (!userService.canAccessOrganization(orgId)) {
            throw new AccessDeniedException("Not authorized to access this organization");
        }

        Optional<Organization> orgOpt = organizationService.findById(orgId);
        if (orgOpt.isEmpty()) {
            mv.setViewName("error/404");
            return mv;
        }
        Organization org = orgOpt.get();
        mv.addObject("organization", org);

        LocalDate today = LocalDate.now();
        LocalDate effectiveStartDate = startDate != null ? startDate : today.minusDays(7);
        LocalDate effectiveEndDate = endDate != null ? endDate : today.plusDays(60);

        List<CalendarEvent> manualEvents = calendarEventRepository
                .findByOrganizationIdAndSource(orgId, EventSource.MANUAL, Sort.by(Sort.Direction.ASC, "date", "startTime"))
                .stream()
                .filter(e -> !e.getDate().isBefore(effectiveStartDate) && !e.getDate().isAfter(effectiveEndDate))
                .collect(Collectors.toList());

        mv.addObject("manualEvents", manualEvents);
        mv.addObject("startDate", effectiveStartDate);
        mv.addObject("endDate", effectiveEndDate);
        mv.addObject("locations", locationService.findMatching(orgId));
        mv.addObject("minyanTypes", MinyanType.values());
        mv.addObject("nusachOptions", Nusach.values());
        mv.addObject("totalManualEvents", manualEvents.size());
        mv.addObject("windowBounds", materializationService.getWindowBounds());

        return mv;
    }

    /**
     * Create a manual override event.
     * Supports two modes:
     * - ADDITIVE: append manual event to winning day source (IMPORTED or RULES)
     * - FULL_DAY_REPLACE: use only MANUAL events for that day
     */
    @PostMapping({"/admin/{orgId}/calendar-events/manual", "/admin/{orgId}/overrides/manual"})
    public RedirectView createManualOverride(
            @PathVariable String orgId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(pattern = "HH:mm") LocalTime startTime,
            @RequestParam String minyanType,
            @RequestParam(defaultValue = "ADDITIVE") String overrideMode,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) String locationId,
            @RequestParam(required = false) String locationName,
            @RequestParam(required = false) String nusach,
            RedirectAttributes redirectAttributes) {

        String redirectPath = "/admin/" + orgId + "/overrides";
        try {
            // Check authorization
            if (!userService.canAccessOrganization(orgId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Not authorized");
                return new RedirectView(redirectPath);
            }

            Optional<Organization> orgOpt = organizationService.findById(orgId);
            if (orgOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Organization not found");
                return new RedirectView(redirectPath);
            }
            Organization org = orgOpt.get();

            MinyanType parsedType;
            try {
                parsedType = MinyanType.valueOf(minyanType);
            } catch (IllegalArgumentException ex) {
                redirectAttributes.addFlashAttribute("errorMessage", "Invalid minyan type");
                return new RedirectView(redirectPath);
            }

            String resolvedLocationId = null;
            String resolvedLocationName = trimToNull(locationName);
            if (locationId != null && !locationId.isBlank()) {
                Location selectedLocation = locationService.findById(locationId);
                if (selectedLocation == null || !orgId.equals(selectedLocation.getOrganizationId())) {
                    redirectAttributes.addFlashAttribute("errorMessage", "Invalid location for this organization");
                    return new RedirectView(redirectPath);
                }
                resolvedLocationId = selectedLocation.getId();
                if (resolvedLocationName == null) {
                    resolvedLocationName = selectedLocation.getName();
                }
            }

            Nusach resolvedNusach = org.getNusach();
            if (nusach != null && !nusach.isBlank()) {
                try {
                    resolvedNusach = Nusach.valueOf(nusach);
                } catch (IllegalArgumentException ex) {
                    redirectAttributes.addFlashAttribute("errorMessage", "Invalid nusach");
                    return new RedirectView(redirectPath);
                }
            }

            String normalizedMode = "FULL_DAY_REPLACE".equalsIgnoreCase(overrideMode)
                    ? "FULL_DAY_REPLACE"
                    : "ADDITIVE";

            String sourceRefPrefix = "FULL_DAY_REPLACE".equals(normalizedMode)
                    ? EffectiveScheduleService.MANUAL_FULL_DAY_SOURCE_REF_PREFIX
                    : "manual:ADDITIVE";

            CalendarEvent newEvent = CalendarEvent.builder()
                    .organizationId(orgId)
                    .date(date)
                    .minyanType(parsedType)
                    .startTime(startTime)
                    .notes(trimToNull(notes))
                    .locationId(resolvedLocationId)
                    .locationName(resolvedLocationName)
                    .enabled(true)
                    .source(EventSource.MANUAL)
                    .sourceRef(sourceRefPrefix + ":" + UUID.randomUUID())
                    .nusach(resolvedNusach)
                    .manuallyEdited(true)
                    .editedBy(userService.getCurrentUser().getUsername())
                    .editedAt(LocalDateTime.now())
                    .build();

            calendarEventRepository.save(newEvent);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Manual override added (" + ("FULL_DAY_REPLACE".equals(normalizedMode) ? "full-day replace" : "additive") + ").");
        } catch (Exception e) {
            log.error("Error creating manual override", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error: " + e.getMessage());
        }

        return new RedirectView(redirectPath);
    }

    /**
     * Download CSV template for manual override import.
     */
    @GetMapping(value = {"/admin/{orgId}/calendar-events/manual/template", "/admin/{orgId}/overrides/template"}, produces = "text/csv")
    public ResponseEntity<byte[]> downloadManualOverrideTemplate(@PathVariable String orgId) {
        if (!userService.canAccessOrganization(orgId)) {
            throw new AccessDeniedException("Not authorized to access this organization");
        }

        String csvTemplate = String.join("\n",
                "date,minyan_type,start_time,override_mode,location,notes,nusach,enabled",
                "2026-03-22,SHACHARIS,07:00,ADDITIVE,,Fast day extra minyan,ASHKENAZ,true",
                "2026-03-23,MINCHA,13:30,FULL_DAY_REPLACE,Main Sanctuary,Special schedule,UNSPECIFIED,true"
        ) + "\n";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"manual-overrides-template.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvTemplate.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Import manual overrides from CSV.
     */
    @PostMapping({"/admin/{orgId}/calendar-events/manual/import", "/admin/{orgId}/overrides/import"})
    public RedirectView importManualOverrides(
            @PathVariable String orgId,
            @RequestParam("file") MultipartFile file,
            RedirectAttributes redirectAttributes) {

        String redirectPath = "/admin/" + orgId + "/overrides";
        try {
            if (!userService.canAccessOrganization(orgId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Not authorized");
                return new RedirectView(redirectPath);
            }

            String username = userService.getCurrentUser() != null
                    ? userService.getCurrentUser().getUsername()
                    : "system";

            ManualOverrideCsvImportService.ImportResult result =
                    manualOverrideCsvImportService.importCsv(orgId, file, username);

            if (result.hasErrors()) {
                String topErrors = result.getErrors().stream().limit(3).collect(Collectors.joining(" | "));
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Imported with issues. Created: " + result.getCreatedCount()
                                + ", Updated: " + result.getUpdatedCount()
                                + ", Errors: " + result.getErrors().size()
                                + ". " + topErrors);
            } else {
                redirectAttributes.addFlashAttribute("successMessage",
                        "CSV import complete. Created: " + result.getCreatedCount()
                                + ", Updated: " + result.getUpdatedCount()
                                + ", Replaced manual rows: " + result.getDeletedManualCount() + ".");
            }
        } catch (Exception e) {
            log.error("Error importing manual overrides CSV", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error: " + e.getMessage());
        }

        return new RedirectView(redirectPath);
    }

    /**
     * Delete manual override event from dedicated overrides page.
     */
    @PostMapping("/admin/{orgId}/overrides/{eventId}/delete")
    public RedirectView deleteOverrideEvent(
            @PathVariable String orgId,
            @PathVariable Long eventId,
            RedirectAttributes redirectAttributes) {
        return deleteManualEventInternal(orgId, eventId, redirectAttributes, "/admin/" + orgId + "/overrides");
    }

    /**
     * Toggle enabled status of a calendar event
     */
    @PostMapping("/admin/{orgId}/calendar-events/{eventId}/toggle")
    public RedirectView toggleEvent(
            @PathVariable String orgId,
            @PathVariable Long eventId,
            RedirectAttributes redirectAttributes) {
        
        try {
            // Check authorization
            if (!userService.canAccessOrganization(orgId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Not authorized");
                return new RedirectView("/admin/" + orgId + "/calendar-events");
            }
            
            Optional<CalendarEvent> eventOpt = calendarEventRepository.findById(eventId);
            if (eventOpt.isEmpty() || !eventOpt.get().getOrganizationId().equals(orgId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Event not found");
                return new RedirectView("/admin/" + orgId + "/calendar-events");
            }
            
            CalendarEvent event = eventOpt.get();
            event.setEnabled(!event.isEnabled());
            calendarEventRepository.save(event);
            
            redirectAttributes.addFlashAttribute("successMessage", 
                    "Event " + (event.isEnabled() ? "enabled" : "disabled") + " successfully");
            
        } catch (Exception e) {
            log.error("Error toggling event", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error: " + e.getMessage());
        }
        
        return new RedirectView("/admin/" + orgId + "/calendar-events");
    }

    /**
     * Update event details
     */
    @PostMapping("/admin/{orgId}/calendar-events/{eventId}/update")
    public RedirectView updateEvent(
            @PathVariable String orgId,
            @PathVariable Long eventId,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) String locationId,
            @RequestParam(required = false) String locationName,
            RedirectAttributes redirectAttributes) {
        
        try {
            // Check authorization
            if (!userService.canAccessOrganization(orgId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Not authorized");
                return new RedirectView("/admin/" + orgId + "/calendar-events");
            }
            
            Optional<CalendarEvent> eventOpt = calendarEventRepository.findById(eventId);
            if (eventOpt.isEmpty() || !eventOpt.get().getOrganizationId().equals(orgId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Event not found");
                return new RedirectView("/admin/" + orgId + "/calendar-events");
            }
            
            CalendarEvent event = eventOpt.get();
            
            // Update fields
            if (notes != null) {
                event.setNotes(notes);
            }
            if (locationId != null && !locationId.isEmpty()) {
                event.setLocationId(locationId);
                // Also update location name if location exists
                Location location = locationService.findById(locationId);
                if (location != null) {
                    event.setLocationName(location.getName());
                }
            }
            if (locationName != null && !locationName.isEmpty()) {
                event.setLocationName(locationName);
            }
            
            // Mark as manually edited
            event.setManuallyEdited(true);
            event.setEditedBy(userService.getCurrentUser().getUsername());
            event.setEditedAt(java.time.LocalDateTime.now());
            
            calendarEventRepository.save(event);
            
            redirectAttributes.addFlashAttribute("successMessage", "Event updated successfully");
            
        } catch (Exception e) {
            log.error("Error updating event", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error: " + e.getMessage());
        }
        
        return new RedirectView("/admin/" + orgId + "/calendar-events");
    }

    /**
     * Trigger manual materialization for organization
     */
    @PostMapping("/admin/{orgId}/calendar-events/rematerialize")
    public RedirectView rematerialize(
            @PathVariable String orgId,
            RedirectAttributes redirectAttributes) {
        
        try {
            // Check authorization
            if (!userService.canAccessOrganization(orgId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Not authorized");
                return new RedirectView("/admin/" + orgId + "/calendar-events");
            }
            
            // Trigger materialization
            materializationScheduler.triggerMaterialization(orgId);
            
            redirectAttributes.addFlashAttribute("successMessage", 
                    "Calendar rematerialization triggered successfully");
            
        } catch (Exception e) {
            log.error("Error triggering materialization", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error: " + e.getMessage());
        }
        
        return new RedirectView("/admin/" + orgId + "/calendar-events");
    }

    /**
     * Delete event (only for MANUAL source events)
     */
    @PostMapping("/admin/{orgId}/calendar-events/{eventId}/delete")
    public RedirectView deleteEvent(
            @PathVariable String orgId,
            @PathVariable Long eventId,
            RedirectAttributes redirectAttributes) {
        return deleteManualEventInternal(orgId, eventId, redirectAttributes, "/admin/" + orgId + "/calendar-events");
    }

    private RedirectView deleteManualEventInternal(
            String orgId,
            Long eventId,
            RedirectAttributes redirectAttributes,
            String redirectPath) {
        try {
            // Check authorization
            if (!userService.canAccessOrganization(orgId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Not authorized");
                return new RedirectView(redirectPath);
            }
            
            Optional<CalendarEvent> eventOpt = calendarEventRepository.findById(eventId);
            if (eventOpt.isEmpty() || !eventOpt.get().getOrganizationId().equals(orgId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Event not found");
                return new RedirectView(redirectPath);
            }
            
            CalendarEvent event = eventOpt.get();
            
            // Only allow deleting MANUAL events
            if (event.getSource() != EventSource.MANUAL) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                        "Can only delete manual events. Use disable for other event types.");
                return new RedirectView(redirectPath);
            }
            
            calendarEventRepository.delete(event);
            
            redirectAttributes.addFlashAttribute("successMessage", "Event deleted successfully");
            
        } catch (Exception e) {
            log.error("Error deleting event", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error: " + e.getMessage());
        }
        
        return new RedirectView(redirectPath);
    }

    // Helper methods

    private List<CalendarEvent> queryEventsWithFilters(
            String orgId, LocalDate startDate, LocalDate endDate,
            String minyanType, String source, Sort sort) {

        // Use EffectiveScheduleService so only the winning events are shown
        // (IMPORTED overrides RULES on days where imports exist)
        List<CalendarEvent> events = effectiveScheduleService.getEffectiveEventsInRange(orgId, startDate, endDate);

        // Apply optional filters (treat empty string same as null — means "no filter")
        String effectiveType = (minyanType != null && !minyanType.isEmpty()) ? minyanType : null;
        String effectiveSource = (source != null && !source.isEmpty()) ? source : null;

        return events.stream()
                .filter(e -> effectiveType == null || e.getMinyanType().name().equals(effectiveType))
                .filter(e -> effectiveSource == null || e.getSource().name().equals(effectiveSource))
                .sorted(getComparator(sort))
                .collect(Collectors.toList());
    }

    private Sort buildSort(String sortBy, String sortDir) {
        String field = sortBy != null ? sortBy : "date";
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? 
                Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, field, "startTime");
    }

    private java.util.Comparator<CalendarEvent> getComparator(Sort sort) {
        // Default comparator by date and time
        return (e1, e2) -> {
            int dateCompare = e1.getDate().compareTo(e2.getDate());
            if (dateCompare != 0) return dateCompare;
            return e1.getStartTime().compareTo(e2.getStartTime());
        };
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
