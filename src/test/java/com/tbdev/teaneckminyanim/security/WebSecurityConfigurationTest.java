package com.tbdev.teaneckminyanim.security;

import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSecurityConfigurationTest {

    @Test
    void webAuthnUsesSharedRpIdAndOriginsForProd() {
        WebSecurityConfiguration configuration = new WebSecurityConfiguration();
        ApplicationSettingsService settingsService = settings("https://teaneckminyanim.com");

        assertEquals("teaneckminyanim.com", configuration.webAuthnRpId(settingsService));
        assertEquals(Set.of(
                        WebSecurityConfiguration.PASSKEY_PROD_ORIGIN,
                        WebSecurityConfiguration.PASSKEY_DEV_ORIGIN),
                configuration.webAuthnAllowedOrigins(settingsService));
    }

    @Test
    void webAuthnUsesSharedRpIdAndOriginsForDevSubdomain() {
        WebSecurityConfiguration configuration = new WebSecurityConfiguration();
        ApplicationSettingsService settingsService = settings("https://dev.teaneckminyanim.com");

        assertEquals("teaneckminyanim.com", configuration.webAuthnRpId(settingsService));
        assertEquals(Set.of(
                        WebSecurityConfiguration.PASSKEY_PROD_ORIGIN,
                        WebSecurityConfiguration.PASSKEY_DEV_ORIGIN),
                configuration.webAuthnAllowedOrigins(settingsService));
    }

    @Test
    void webAuthnKeepsLocalhostConfigLocal() {
        WebSecurityConfiguration configuration = new WebSecurityConfiguration();
        ApplicationSettingsService settingsService = settings("http://localhost:8080");

        assertEquals("localhost", configuration.webAuthnRpId(settingsService));
        assertEquals(Set.of("http://localhost:8080"), configuration.webAuthnAllowedOrigins(settingsService));
    }

    @Test
    void webAuthnIncludesConfiguredTeaneckSubdomainOrigin() {
        WebSecurityConfiguration configuration = new WebSecurityConfiguration();
        ApplicationSettingsService settingsService = settings("https://staging.teaneckminyanim.com:8443");

        Set<String> origins = configuration.webAuthnAllowedOrigins(settingsService);

        assertEquals("teaneckminyanim.com", configuration.webAuthnRpId(settingsService));
        assertTrue(origins.contains(WebSecurityConfiguration.PASSKEY_PROD_ORIGIN));
        assertTrue(origins.contains(WebSecurityConfiguration.PASSKEY_DEV_ORIGIN));
        assertTrue(origins.contains("https://staging.teaneckminyanim.com:8443"));
    }

    private ApplicationSettingsService settings(String siteRootUrl) {
        ApplicationSettingsService settingsService = mock(ApplicationSettingsService.class);
        when(settingsService.getSiteRootUrl()).thenReturn(siteRootUrl);
        return settingsService;
    }
}
