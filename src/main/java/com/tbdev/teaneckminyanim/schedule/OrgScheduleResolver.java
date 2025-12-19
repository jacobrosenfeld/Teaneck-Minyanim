package com.tbdev.teaneckminyanim.schedule;

import com.tbdev.teaneckminyanim.front.MinyanEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrgScheduleResolver {

    private final CalendarScrapeProvider calendarScrapeProvider;
    private final RuleBasedProvider ruleBasedProvider;

    /**
     * Get minyan events for an organization, choosing the appropriate provider
     */
    public List<MinyanEvent> getMinyanEvents(String organizationId, LocalDate date) {
        // Try calendar-based provider first
        if (calendarScrapeProvider.canHandle(organizationId)) {
            log.debug("Using calendar scrape provider for org {}", organizationId);
            List<MinyanEvent> events = calendarScrapeProvider.getMinyanEvents(organizationId, date);
            if (!events.isEmpty()) {
                return events;
            }
            // If no events found in scraped calendar, fall through to rule-based
            log.debug("No scraped events found for org {}, falling back to rule-based", organizationId);
        }

        // Fall back to rule-based provider
        log.debug("Using rule-based provider for org {}", organizationId);
        return ruleBasedProvider.getMinyanEvents(organizationId, date);
    }

    /**
     * Check if organization is using scraped calendar
     */
    public boolean isUsingScrapedCalendar(String organizationId) {
        return calendarScrapeProvider.canHandle(organizationId);
    }
}
