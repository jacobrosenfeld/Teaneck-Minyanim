package com.tbdev.teaneckminyanim.service.provider;

import com.tbdev.teaneckminyanim.front.MinyanEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Resolver that selects the appropriate schedule provider for an organization.
 * Checks providers in priority order and uses the first one that can handle the organization.
 */
@Slf4j
@Service
public class OrgScheduleResolver {

    private final List<OrgScheduleProvider> providers;

    @Autowired
    public OrgScheduleResolver(List<OrgScheduleProvider> providers) {
        // Sort providers by priority (highest first)
        this.providers = new ArrayList<>(providers);
        this.providers.sort(Comparator.comparingInt(OrgScheduleProvider::getPriority).reversed());
        
        log.info("Initialized OrgScheduleResolver with {} providers:", this.providers.size());
        for (OrgScheduleProvider provider : this.providers) {
            log.info("  - {} (priority: {})", provider.getProviderName(), provider.getPriority());
        }
    }

    /**
     * Get the appropriate provider for an organization.
     * Returns the highest-priority provider that can handle the organization.
     *
     * @param organizationId Organization ID
     * @return The provider to use, or null if none can handle
     */
    public OrgScheduleProvider getProviderForOrganization(String organizationId) {
        for (OrgScheduleProvider provider : providers) {
            if (provider.canHandle(organizationId)) {
                log.debug("Selected {} for organization {}", 
                        provider.getProviderName(), organizationId);
                return provider;
            }
        }
        
        log.warn("No provider found for organization: {}", organizationId);
        return null;
    }

    /**
     * Get events for an organization on a specific date using the appropriate provider.
     *
     * @param organizationId Organization ID
     * @param date Date to get events for
     * @return List of minyan events
     */
    public List<MinyanEvent> getEventsForDate(String organizationId, LocalDate date) {
        OrgScheduleProvider provider = getProviderForOrganization(organizationId);
        
        if (provider == null) {
            log.warn("No provider available for organization {} on {}", organizationId, date);
            return new ArrayList<>();
        }

        log.debug("Using {} to get events for {} on {}", 
                provider.getProviderName(), organizationId, date);
        
        return provider.getEventsForDate(organizationId, date);
    }

    /**
     * Check if calendar import is enabled for an organization.
     * This is used to determine if we should use calendar-based or rule-based scheduling.
     *
     * @param organizationId Organization ID
     * @return true if calendar import provider is selected
     */
    public boolean isCalendarImportEnabled(String organizationId) {
        OrgScheduleProvider provider = getProviderForOrganization(organizationId);
        return provider != null && "CalendarImportProvider".equals(provider.getProviderName());
    }
}
