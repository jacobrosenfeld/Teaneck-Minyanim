package com.tbdev.teaneckminyanim.schedule;

import com.tbdev.teaneckminyanim.front.MinyanEvent;

import java.time.LocalDate;
import java.util.List;

/**
 * Abstraction for providing minyan schedule data for an organization
 */
public interface OrgScheduleProvider {

    /**
     * Get minyan events for a specific organization and date
     */
    List<MinyanEvent> getMinyanEvents(String organizationId, LocalDate date);

    /**
     * Check if this provider can handle the given organization
     */
    boolean canHandle(String organizationId);
}
