package com.tbdev.teaneckminyanim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Startup and manual triggers for materializing calendar events.
 *
 * Runs:
 * - On application startup (initial materialization)
 * - Right after each scheduled calendar import, on the schedule saved in the
 *   calendar import settings (see {@code CalendarImportScheduler}). That replaces the
 *   former fixed "Sunday 2 AM" materialization cron.
 *
 * Can also be triggered manually via admin endpoint.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarMaterializationScheduler {

    private final CalendarMaterializationService materializationService;

    /**
     * Run materialization on application startup.
     * This ensures calendar events are available immediately.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void materializeOnStartup() {
        log.info("Running initial calendar materialization on application startup");
        try {
            materializationService.materializeAll();
            log.info("Initial calendar materialization completed successfully");
        } catch (Exception e) {
            log.error("Initial calendar materialization failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Manual trigger for materialization (called from admin controller).
     */
    public void triggerMaterialization() {
        log.info("Manual trigger for calendar materialization");
        materializationService.materializeAll();
    }

    /**
     * Manual trigger for specific organization (called from admin controller).
     */
    public void triggerMaterialization(String organizationId) {
        log.info("Manual trigger for calendar materialization: organization {}", organizationId);
        materializationService.materializeOrganization(organizationId);
    }
}
