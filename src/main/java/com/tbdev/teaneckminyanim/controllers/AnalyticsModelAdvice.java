package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes the Google Tag Manager container ID to every Thymeleaf view as {@code gtmId}.
 *
 * Applies to all controllers in the application (public and admin), so both
 * {@code frontnavbar.html} and {@code admin/navbar.html} can render the GTM snippets.
 * An empty value means Tag Manager is turned off and the templates render no snippet.
 */
@Slf4j
@ControllerAdvice(basePackages = "com.tbdev.teaneckminyanim")
@RequiredArgsConstructor
public class AnalyticsModelAdvice {

    private final ApplicationSettingsService settingsService;

    @ModelAttribute("gtmId")
    public String gtmId() {
        try {
            return settingsService.getGtmContainerId();
        } catch (RuntimeException e) {
            // Analytics must never break page rendering.
            log.warn("Could not read the GTM container ID setting: {}", e.getMessage());
            return "";
        }
    }
}
