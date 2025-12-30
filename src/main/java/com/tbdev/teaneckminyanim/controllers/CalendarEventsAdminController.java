package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.enums.EventSource;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
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
            @RequestParam(required = false) String enabled,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        
        ModelAndView mv = new ModelAndView("admin/calendar-events");
        
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
        
        // Default date range to materialization window
        CalendarMaterializationService.WindowBounds bounds = materializationService.getWindowBounds();
        LocalDate effectiveStartDate = startDate != null ? startDate : bounds.getStartDate();
        LocalDate effectiveEndDate = endDate != null ? endDate : bounds.getEndDate();
        
        // Build sort
        Sort sort = buildSort(sortBy, sortDir);
        
        // Query events with filters
        List<CalendarEvent> events = queryEventsWithFilters(
                orgId, effectiveStartDate, effectiveEndDate, minyanType, source, enabled, sort);
        
        // Add to model
        mv.addObject("events", events);
        mv.addObject("startDate", effectiveStartDate);
        mv.addObject("endDate", effectiveEndDate);
        mv.addObject("minyanTypeFilter", minyanType);
        mv.addObject("sourceFilter", source);
        mv.addObject("enabledFilter", enabled);
        mv.addObject("sortBy", sortBy != null ? sortBy : "date");
        mv.addObject("sortDir", sortDir != null ? sortDir : "asc");
        
        // Add filter options
        mv.addObject("minyanTypes", MinyanType.values());
        mv.addObject("eventSources", EventSource.values());
        mv.addObject("windowBounds", bounds);
        
        // Add locations for editing
        List<Location> locations = locationService.findMatching(orgId);
        mv.addObject("locations", locations);
        
        // Statistics
        long totalEvents = calendarEventRepository.countEventsInRange(orgId, effectiveStartDate, effectiveEndDate);
        long enabledEvents = events.stream().filter(CalendarEvent::isEnabled).count();
        long rulesEvents = events.stream().filter(e -> e.getSource() == EventSource.RULES).count();
        long importedEvents = events.stream().filter(e -> e.getSource() == EventSource.IMPORTED).count();
        long manualEvents = events.stream().filter(e -> e.getSource() == EventSource.MANUAL).count();
        
        mv.addObject("totalEvents", totalEvents);
        mv.addObject("enabledEvents", enabledEvents);
        mv.addObject("rulesEvents", rulesEvents);
        mv.addObject("importedEvents", importedEvents);
        mv.addObject("manualEvents", manualEvents);
        
        return mv;
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
            
            // Only allow deleting MANUAL events
            if (event.getSource() != EventSource.MANUAL) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                        "Can only delete manual events. Use disable for other event types.");
                return new RedirectView("/admin/" + orgId + "/calendar-events");
            }
            
            calendarEventRepository.delete(event);
            
            redirectAttributes.addFlashAttribute("successMessage", "Event deleted successfully");
            
        } catch (Exception e) {
            log.error("Error deleting event", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error: " + e.getMessage());
        }
        
        return new RedirectView("/admin/" + orgId + "/calendar-events");
    }

    // Helper methods

    private List<CalendarEvent> queryEventsWithFilters(
            String orgId, LocalDate startDate, LocalDate endDate,
            String minyanType, String source, String enabled, Sort sort) {
        
        List<CalendarEvent> events = calendarEventRepository.findEventsInRange(orgId, startDate, endDate);
        
        // Apply filters
        return events.stream()
                .filter(e -> minyanType == null || e.getMinyanType().name().equals(minyanType))
                .filter(e -> source == null || e.getSource().name().equals(source))
                .filter(e -> enabled == null || 
                        (enabled.equals("true") && e.isEnabled()) || 
                        (enabled.equals("false") && !e.isEnabled()))
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
}
