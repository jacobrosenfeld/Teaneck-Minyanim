package com.tbdev.teaneckminyanim.service.subscription;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
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
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class SendySubscriptionService {
    private static final URI SENDY_SUBSCRIBE_URI = URI.create("https://sendy.josephjacobs.org/subscribe");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final String DEFAULT_LIST_ID = "2GTjv7L7dlk4MXC1SN9Lgg";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public void subscribe(SubscriptionRequest request) throws SubscriptionException {
        String honeypot = normalize(request.honeypot());
        if (!honeypot.isBlank()) {
            log.info("Skipping newsletter subscription with populated honeypot field.");
            return;
        }

        String email = normalize(request.email());
        if (email.isBlank()) {
            throw new SubscriptionException("Email address is required.");
        }
        validateEmail(email);

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(SENDY_SUBSCRIBE_URI)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody(request, email)))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 400) {
                throw new SubscriptionException("Subscription service returned HTTP " + response.statusCode() + ".");
            }
        } catch (IOException e) {
            throw new SubscriptionException("Subscription could not be sent: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SubscriptionException("Subscription request was interrupted.", e);
        }
    }

    private String formBody(SubscriptionRequest request, String normalizedEmail) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("name", normalize(request.name()));
        fields.put("email", normalizedEmail);
        fields.put("hp", "");
        fields.put("list", normalizeOrDefault(request.list(), DEFAULT_LIST_ID));
        fields.put("subform", normalizeOrDefault(request.subform(), "yes"));
        return encodeForm(fields);
    }

    private String encodeForm(Map<String, String> fields) {
        StringBuilder encoded = new StringBuilder();
        fields.forEach((key, value) -> {
            if (!encoded.isEmpty()) {
                encoded.append("&");
            }
            encoded.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
        return encoded.toString();
    }

    private void validateEmail(String email) throws SubscriptionException {
        try {
            InternetAddress address = new InternetAddress(email);
            address.validate();
        } catch (AddressException e) {
            throw new SubscriptionException("Email address must be valid.");
        }
    }

    private String normalizeOrDefault(String value, String fallback) {
        String normalized = normalize(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
