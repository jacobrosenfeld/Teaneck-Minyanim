package com.tbdev.teaneckminyanim.service.provider;

import com.tbdev.teaneckminyanim.front.MinyanEvent;

import java.time.LocalDate;
import java.util.List;

/**
 * Interface for providing organization schedule data.
 * Implementations can source data from different origins (calendar imports, rule-based generation, etc.)
 */
public interface OrgScheduleProvider {

    /**
     * Get minyan events for an organization on a specific date.
     *
     * @param organizationId Organization ID
     * @param date Date to get events for
     * @return List of minyan events for that date
     */
    List<MinyanEvent> getEventsForDate(String organizationId, LocalDate date);

    /**
     * Check if this provider can handle the given organization.
     * Used by OrgScheduleResolver to determine which provider to use.
     *
     * @param organizationId Organization ID
     * @return true if this provider can handle the organization
     */
    boolean canHandle(String organizationId);

    /**
     * Get the priority of this provider.
     * Higher priority providers are checked first.
     * Calendar import providers should have higher priority than rule-based.
     *
     * @return Priority value (higher = checked first)
     */
    int getPriority();

    /**
     * Get the name of this provider (for logging/debugging).
     *
     * @return Provider name
     */
    String getProviderName();
}
