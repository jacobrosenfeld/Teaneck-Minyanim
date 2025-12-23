package com.tbdev.teaneckminyanim.service.provider;

import com.tbdev.teaneckminyanim.front.MinyanEvent;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Provider that sources schedule data from rule-based generation (existing Minyan entities).
 * This is the default/fallback provider when calendar import is not enabled.
 * 
 * Note: The actual logic for generating MinyanEvents from Minyan entities remains in ZmanimService.
 * This provider acts as a marker/router that indicates rule-based generation should be used.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleBasedProvider implements OrgScheduleProvider {

    private final OrganizationService organizationService;

    @Override
    public List<MinyanEvent> getEventsForDate(String organizationId, LocalDate date) {
        // Note: This method returns empty list as the actual event generation
        // happens in ZmanimService when this provider is selected.
        // This is a transitional design - in a full refactor, the ZmanimService
        // logic would be moved here.
        log.debug("RuleBasedProvider: Indicating rule-based generation for {} on {}", 
                organizationId, date);
        return new ArrayList<>();
    }

    @Override
    public boolean canHandle(String organizationId) {
        // RuleBasedProvider can always handle any organization as the fallback
        Optional<Organization> orgOpt = organizationService.findById(organizationId);
        boolean canHandle = orgOpt.isPresent();
        
        log.debug("RuleBasedProvider.canHandle({}) = {}", organizationId, canHandle);
        return canHandle;
    }

    @Override
    public int getPriority() {
        return 10; // Lower priority than calendar import (100)
    }

    @Override
    public String getProviderName() {
        return "RuleBasedProvider";
    }
}
