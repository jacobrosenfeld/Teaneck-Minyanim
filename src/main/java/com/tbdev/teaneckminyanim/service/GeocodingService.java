package com.tbdev.teaneckminyanim.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbdev.teaneckminyanim.enums.SettingKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Geocodes street addresses to lat/lng using the Mapbox Geocoding API.
 * The Mapbox access token is read from TNMSettings (SettingKey.MAPBOX_ACCESS_TOKEN).
 * Results are proximity-biased toward Teaneck, NJ so local addresses resolve correctly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeocodingService {

    private static final String MAPBOX_GEOCODE_URL =
            "https://api.mapbox.com/geocoding/v5/mapbox.places/%s.json";
    // Teaneck, NJ — lng,lat (Mapbox uses lng first)
    private static final String TEANECK_PROXIMITY = "-74.0140,40.8918";

    private final ApplicationSettingsService settingsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Geocodes the given address string.
     *
     * @param address Street address to geocode
     * @return double[]{latitude, longitude}, or null if geocoding fails or token is not set
     */
    public double[] geocodeAddress(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }

        String token = settingsService.getString(SettingKey.MAPBOX_ACCESS_TOKEN);
        if (token == null || token.isBlank()) {
            log.warn("Mapbox access token not configured — skipping geocoding for: {}", address);
            return null;
        }

        try {
            String encoded = URLEncoder.encode(address, StandardCharsets.UTF_8);
            String url = String.format(MAPBOX_GEOCODE_URL, encoded)
                    + "?access_token=" + token
                    + "&limit=1"
                    + "&country=us"
                    + "&proximity=" + TEANECK_PROXIMITY;

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Mapbox geocoding returned HTTP {} for address: {}", response.statusCode(), address);
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode features = root.path("features");

            if (features.isArray() && !features.isEmpty()) {
                // Mapbox center is [longitude, latitude]
                JsonNode center = features.get(0).path("center");
                double lng = center.get(0).asDouble();
                double lat = center.get(1).asDouble();
                log.info("Geocoded '{}' → lat={}, lng={}", address, lat, lng);
                return new double[]{lat, lng};
            }

            log.warn("No geocoding results for address: {}", address);
            return null;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Geocoding interrupted for address '{}': {}", address, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Geocoding failed for address '{}': {}", address, e.getMessage());
            return null;
        }
    }
}
