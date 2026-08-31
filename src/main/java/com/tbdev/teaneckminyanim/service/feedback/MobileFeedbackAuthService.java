package com.tbdev.teaneckminyanim.service.feedback;

import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
@RequiredArgsConstructor
public class MobileFeedbackAuthService {
    public static final String APP_TOKEN_HEADER = "X-Teaneck-Minyanim-App-Token";

    private final ApplicationSettingsService settingsService;

    public void verify(HttpServletRequest request)
            throws FeedbackConfigurationException, MobileFeedbackAuthenticationException {
        String expectedToken = normalize(settingsService.getFeedbackMobileAppToken());
        if (expectedToken.isBlank()) {
            throw new FeedbackConfigurationException("Mobile feedback app token is not configured.");
        }

        String providedToken = normalize(request == null ? null : request.getHeader(APP_TOKEN_HEADER));
        if (providedToken.isBlank() || !constantTimeEquals(expectedToken, providedToken)) {
            throw new MobileFeedbackAuthenticationException("Mobile feedback app token is missing or invalid.");
        }
    }

    private boolean constantTimeEquals(String expectedToken, String providedToken) {
        byte[] expected = expectedToken.getBytes(StandardCharsets.UTF_8);
        byte[] provided = providedToken.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, provided);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
