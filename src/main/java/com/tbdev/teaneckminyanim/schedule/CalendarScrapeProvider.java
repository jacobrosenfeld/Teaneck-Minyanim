package com.tbdev.teaneckminyanim.schedule;

import com.tbdev.teaneckminyanim.enums.Nusach;
import com.tbdev.teaneckminyanim.front.MinyanEvent;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.OrganizationCalendarEntry;
import com.tbdev.teaneckminyanim.repo.OrganizationCalendarEntryRepository;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CalendarScrapeProvider implements OrgScheduleProvider {

    private final OrganizationCalendarEntryRepository calendarEntryRepository;
    private final OrganizationService organizationService;

    @Override
    public List<MinyanEvent> getMinyanEvents(String organizationId, LocalDate date) {
        List<OrganizationCalendarEntry> entries = calendarEntryRepository
                .findByOrganizationIdAndDateAndEnabled(organizationId, date, true);

        Optional<Organization> orgOpt = organizationService.findById(organizationId);
        if (orgOpt.isEmpty()) {
            return List.of();
        }

        Organization org = orgOpt.get();

        return entries.stream()
                .map(entry -> convertToMinyanEvent(entry, org))
                .collect(Collectors.toList());
    }

    @Override
    public boolean canHandle(String organizationId) {
        Optional<Organization> orgOpt = organizationService.findById(organizationId);
        if (orgOpt.isEmpty()) {
            return false;
        }

        Organization org = orgOpt.get();
        
        // Can handle if organization has calendar URL AND use_scraped_calendar is enabled
        return org.getCalendar() != null && 
               !org.getCalendar().trim().isEmpty() &&
               org.getUseScrapedCalendar() != null &&
               org.getUseScrapedCalendar();
    }

    private MinyanEvent convertToMinyanEvent(OrganizationCalendarEntry entry, Organization org) {
        // Convert LocalDate and LocalTime to Date
        Date startTime = Date.from(
                entry.getDate()
                        .atTime(entry.getTime())
                        .atZone(ZoneId.of("America/New_York"))
                        .toInstant()
        );

        return new MinyanEvent(
                "scraped-" + entry.getId(),  // parent minyan ID
                entry.getType(),              // type
                org.getName(),                // organization name
                org.getNusach() != null ? org.getNusach() : Nusach.UNSPECIFIED,  // org nusach
                org.getId(),                  // organization ID
                null,                         // location name (not available in scraped data)
                startTime,                    // start time
                null,                         // dynamic time string
                Nusach.UNSPECIFIED,          // minyan nusach
                entry.getTitle(),            // notes (using title as notes)
                org.getOrgColor(),           // org color
                null                          // whatsapp
        );
    }
}
