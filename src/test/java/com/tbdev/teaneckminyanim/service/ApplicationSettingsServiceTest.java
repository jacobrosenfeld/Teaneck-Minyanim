package com.tbdev.teaneckminyanim.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApplicationSettingsServiceTest {

    @Test
    void extractIosAppId_fromStandardAppStoreUrl() {
        String url = "https://apps.apple.com/us/app/teaneck-minyanim/id1234567890";
        assertEquals("1234567890", ApplicationSettingsService.extractIosAppId(url));
    }

    @Test
    void extractIosAppId_fromAppStoreUrlWithQueryParameters() {
        String url = "https://apps.apple.com/app/id987654321?mt=8";
        assertEquals("987654321", ApplicationSettingsService.extractIosAppId(url));
    }

    @Test
    void extractIosAppId_fromRawNumericId() {
        assertEquals("2468135790", ApplicationSettingsService.extractIosAppId("2468135790"));
    }

    @Test
    void extractIosAppId_returnsNullForInvalidInput() {
        assertNull(ApplicationSettingsService.extractIosAppId("https://apps.apple.com/us/app/teaneck-minyanim"));
        assertNull(ApplicationSettingsService.extractIosAppId("not-a-url"));
        assertNull(ApplicationSettingsService.extractIosAppId(""));
        assertNull(ApplicationSettingsService.extractIosAppId(null));
    }

    @Test
    void buildAppleSmartAppBannerContent_returnsExpectedValue() {
        String url = "https://apps.apple.com/us/app/teaneck-minyanim/id1234567890";
        assertEquals("app-id=1234567890", ApplicationSettingsService.buildAppleSmartAppBannerContent(url));
        assertNull(ApplicationSettingsService.buildAppleSmartAppBannerContent("https://apps.apple.com/us/app/no-id"));
    }
}
