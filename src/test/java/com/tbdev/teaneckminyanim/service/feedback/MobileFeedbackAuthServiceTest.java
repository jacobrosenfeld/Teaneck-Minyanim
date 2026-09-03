package com.tbdev.teaneckminyanim.service.feedback;

import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MobileFeedbackAuthServiceTest {

    @Test
    void verifyAcceptsMatchingAppToken() {
        ApplicationSettingsService settingsService = settings("server-token");
        HttpServletRequest request = request("server-token");
        MobileFeedbackAuthService service = new MobileFeedbackAuthService(settingsService);

        assertDoesNotThrow(() -> service.verify(request));
    }

    @Test
    void verifyRejectsMissingAppTokenSetting() {
        ApplicationSettingsService settingsService = settings("");
        HttpServletRequest request = request("server-token");
        MobileFeedbackAuthService service = new MobileFeedbackAuthService(settingsService);

        assertThrows(FeedbackConfigurationException.class, () -> service.verify(request));
    }

    @Test
    void verifyRejectsInvalidAppTokenHeader() {
        ApplicationSettingsService settingsService = settings("server-token");
        HttpServletRequest request = request("wrong-token");
        MobileFeedbackAuthService service = new MobileFeedbackAuthService(settingsService);

        assertThrows(MobileFeedbackAuthenticationException.class, () -> service.verify(request));
    }

    private ApplicationSettingsService settings(String token) {
        ApplicationSettingsService settingsService = mock(ApplicationSettingsService.class);
        when(settingsService.getFeedbackMobileAppToken()).thenReturn(token);
        return settingsService;
    }

    private HttpServletRequest request(String token) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(MobileFeedbackAuthService.APP_TOKEN_HEADER)).thenReturn(token);
        return request;
    }
}
