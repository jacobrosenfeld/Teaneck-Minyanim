package com.tbdev.teaneckminyanim.service.recaptcha;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecaptchaService {
    private static final URI VERIFY_URI = URI.create("https://www.google.com/recaptcha/api/siteverify");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private final ApplicationSettingsService settingsService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public boolean isEnabled() {
        return settingsService.isRecaptchaEnabled();
    }

    public String siteKey() {
        return settingsService.getRecaptchaSiteKey();
    }

    public void verify(String token, HttpServletRequest request) throws RecaptchaVerificationException {
        if (!isEnabled()) {
            return;
        }

        String normalizedToken = normalize(token);
        if (normalizedToken.isBlank()) {
            throw new RecaptchaVerificationException("reCAPTCHA verification is required.");
        }

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(VERIFY_URI)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(verificationForm(normalizedToken, clientIp(request))))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RecaptchaVerificationException("reCAPTCHA verification service returned HTTP "
                        + response.statusCode() + ".");
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (!root.path("success").asBoolean(false)) {
                log.warn("reCAPTCHA verification failed: {}", root.path("error-codes"));
                throw new RecaptchaVerificationException("reCAPTCHA verification failed.");
            }
        } catch (IOException e) {
            throw new RecaptchaVerificationException("reCAPTCHA verification failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RecaptchaVerificationException("reCAPTCHA verification was interrupted.", e);
        }
    }

    private String verificationForm(String token, String clientIp) {
        List<String> fields = new ArrayList<>();
        fields.add(formField("secret", settingsService.getRecaptchaSecretKey()));
        fields.add(formField("response", token));
        if (hasText(clientIp)) {
            fields.add(formField("remoteip", clientIp));
        }
        return String.join("&", fields);
    }

    private String formField(String name, String value) {
        return URLEncoder.encode(name, StandardCharsets.UTF_8)
                + "="
                + URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (hasText(forwardedFor)) {
            String firstForwardedIp = forwardedFor.split(",")[0].trim();
            if (hasText(firstForwardedIp)) {
                return firstForwardedIp;
            }
        }

        String realIp = request.getHeader("X-Real-IP");
        if (hasText(realIp)) {
            return realIp.trim();
        }

        return normalize(request.getRemoteAddr());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
