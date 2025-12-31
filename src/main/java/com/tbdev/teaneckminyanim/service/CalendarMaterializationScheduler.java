package com.tbdev.teaneckminyanim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduled job for materializing calendar events.
 * 
 * Runs:
 * - On application startup (initial materialization)
 * - Every Sunday at 2 AM (weekly refresh)
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
     * Run materialization weekly on Sunday at 2 AM.
     * Cron: second, minute, hour, day of month, month, day of week
     * "0 0 2 * * SUN" = 2:00 AM every Sunday
     */
    @Scheduled(cron = "0 0 2 * * SUN")
    public void materializeWeekly() {
        log.info("Running scheduled weekly calendar materialization");
        try {
            materializationService.materializeAll();
            log.info("Scheduled weekly calendar materialization completed successfully");
        } catch (Exception e) {
            log.error("Scheduled weekly calendar materialization failed: {}", e.getMessage(), e);
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
