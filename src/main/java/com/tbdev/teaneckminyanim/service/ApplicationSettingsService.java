package com.tbdev.teaneckminyanim.service;

import com.kosherjava.zmanim.util.GeoLocation;
import com.tbdev.teaneckminyanim.enums.SettingKey;
import com.tbdev.teaneckminyanim.enums.SettingType;
import com.tbdev.teaneckminyanim.model.ApplicationSettings;
import com.tbdev.teaneckminyanim.repo.ApplicationSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Centralized service for managing application-wide settings.
 * Provides type-safe getters, validation, caching, and default values.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationSettingsService {

    private final ApplicationSettingsRepository repository;
    
    // Cache for performance
    private final Map<String, String> settingsCache = new ConcurrentHashMap<>();
    
    /**
     * Initialize default settings on application startup if they don't exist.
     */
    @PostConstruct
    public void initializeDefaults() {
        log.info("Initializing application settings with defaults");
        
        for (SettingKey key : SettingKey.values()) {
            if (!repository.findBySettingKey(key.getKey()).isPresent()) {
                ApplicationSettings setting = new ApplicationSettings(
                    key.getKey(),
                    key.getDefaultValue(),
                    key.getType().name()
                );
                setting.setDescription(key.getDescription());
                setting.setCategory(key.getCategory());
                repository.save(setting);
                settingsCache.put(key.getKey(), key.getDefaultValue());
                log.info("Created default setting: {} = {}", key.getKey(), key.getDefaultValue());
            } else {
                // Load into cache
                ApplicationSettings existing = repository.findBySettingKey(key.getKey()).get();
                settingsCache.put(key.getKey(), existing.getSettingValue());
            }
        }
    }
    
    /**
     * Get a setting value as a string.
     */
    public String getString(SettingKey key) {
        String value = settingsCache.get(key.getKey());
        if (value == null) {
            value = repository.findBySettingKey(key.getKey())
                .map(ApplicationSettings::getSettingValue)
                .orElse(key.getDefaultValue());
            settingsCache.put(key.getKey(), value);
        }
        return value;
    }
    
    /**
     * Get a setting value as an integer.
     */
    public Integer getInteger(SettingKey key) {
        String value = getString(key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid integer value for {}: {}, using default", key.getKey(), value);
            return Integer.parseInt(key.getDefaultValue());
        }
    }
    
    /**
     * Get a setting value as a double.
     */
    public Double getDouble(SettingKey key) {
        String value = getString(key);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid double value for {}: {}, using default", key.getKey(), value);
            return Double.parseDouble(key.getDefaultValue());
        }
    }
    
    /**
     * Get a setting value as a boolean.
     */
    public Boolean getBoolean(SettingKey key) {
        String value = getString(key);
        return Boolean.parseBoolean(value);
    }
    
    // Convenience methods for common settings
    
    public String getLocationName() {
        return getString(SettingKey.LOCATION_NAME);
    }
    
    public Double getLatitude() {
        return getDouble(SettingKey.LOCATION_LATITUDE);
    }
    
    public Double getLongitude() {
        return getDouble(SettingKey.LOCATION_LONGITUDE);
    }
    
    public Double getElevation() {
        return getDouble(SettingKey.LOCATION_ELEVATION);
    }
    
    public TimeZone getTimeZone() {
        String tzId = getString(SettingKey.TIMEZONE);
        return TimeZone.getTimeZone(tzId);
    }
    
    public ZoneId getZoneId() {
        String tzId = getString(SettingKey.TIMEZONE);
        return ZoneId.of(tzId);
    }
    
    public String getCalendarImportCron() {
        return getString(SettingKey.CALENDAR_IMPORT_CRON);
    }
    
    public Integer getCalendarCleanupDays() {
        return getInteger(SettingKey.CALENDAR_CLEANUP_DAYS);
    }
    
    /**
     * Get site name.
     */
    public String getSiteName() {
        return getString(SettingKey.SITE_NAME);
    }
    
    /**
     * Get app theme color.
     */
    public String getAppColor() {
        return getString(SettingKey.SITE_APP_COLOR);
    }
    
    /**
     * Get Mapbox access token.
     */
    public String getMapboxAccessToken() {
        return getString(SettingKey.MAPBOX_ACCESS_TOKEN);
    }
    
    /**
     * Get GeoLocation object for Zmanim calculations.
     */
    public GeoLocation getGeoLocation() {
        return new GeoLocation(
            getLocationName(),
            getLatitude(),
            getLongitude(),
            getElevation(),
            getTimeZone()
        );
    }
    
    /**
     * Update a setting value with validation.
     */
    public void updateSetting(SettingKey key, String value) throws ValidationException {
        // Validate the value
        validateSettingValue(key, value);
        
        ApplicationSettings setting = repository.findBySettingKey(key.getKey())
            .orElseThrow(() -> new IllegalStateException("Setting not found: " + key.getKey()));
        
        setting.setSettingValue(value);
        repository.save(setting);
        
        // Update cache
        settingsCache.put(key.getKey(), value);
        
        log.info("Updated setting: {} = {}", key.getKey(), value);
    }
    
    /**
     * Update a setting by key string (used by admin controller).
     */
    public void updateSettingByKey(String keyStr, String value) throws ValidationException {
        SettingKey key = SettingKey.fromKey(keyStr);
        updateSetting(key, value);
    }
    
    /**
     * Get all settings for display in admin panel.
     */
    public List<ApplicationSettings> getAllSettings() {
        return repository.findAll();
    }
    
    /**
     * Get settings grouped by category.
     */
    public Map<String, List<ApplicationSettings>> getSettingsByCategory() {
        return getAllSettings().stream()
            .collect(Collectors.groupingBy(
                setting -> setting.getCategory() != null ? setting.getCategory() : "General"
            ));
    }
    
    /**
     * Refresh the cache from database.
     */
    public void refreshCache() {
        settingsCache.clear();
        for (ApplicationSettings setting : getAllSettings()) {
            settingsCache.put(setting.getSettingKey(), setting.getSettingValue());
        }
        log.info("Settings cache refreshed");
    }
    
    /**
     * Validate a setting value before saving.
     */
    private void validateSettingValue(SettingKey key, String value) throws ValidationException {
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationException("Setting value cannot be empty");
        }
        
        switch (key) {
            case SITE_APP_COLOR:
                validateHexColor(value);
                break;
            case LOCATION_LATITUDE:
                validateLatitude(value);
                break;
            case LOCATION_LONGITUDE:
                validateLongitude(value);
                break;
            case LOCATION_ELEVATION:
                validateElevation(value);
                break;
            case TIMEZONE:
                validateTimezone(value);
                break;
            case CALENDAR_IMPORT_CRON:
                validateCronExpression(value);
                break;
            case CALENDAR_CLEANUP_DAYS:
                validatePositiveInteger(value);
                break;
            // SITE_NAME and MAPBOX_ACCESS_TOKEN - no special validation needed
        }
    }
    
    private void validateHexColor(String value) throws ValidationException {
        if (!value.matches("^#[0-9A-Fa-f]{6}$")) {
            throw new ValidationException("App color must be a valid hex color code (e.g., #275ed8)");
        }
    }
    
    private void validateLatitude(String value) throws ValidationException {
        try {
            double lat = Double.parseDouble(value);
            if (lat < -90 || lat > 90) {
                throw new ValidationException("Latitude must be between -90 and 90");
            }
        } catch (NumberFormatException e) {
            throw new ValidationException("Latitude must be a valid number");
        }
    }
    
    private void validateLongitude(String value) throws ValidationException {
        try {
            double lon = Double.parseDouble(value);
            if (lon < -180 || lon > 180) {
                throw new ValidationException("Longitude must be between -180 and 180");
            }
        } catch (NumberFormatException e) {
            throw new ValidationException("Longitude must be a valid number");
        }
    }
    
    private void validateElevation(String value) throws ValidationException {
        try {
            double elev = Double.parseDouble(value);
            if (elev < 0 || elev > 9000) {
                throw new ValidationException("Elevation must be between 0 and 9000 meters");
            }
        } catch (NumberFormatException e) {
            throw new ValidationException("Elevation must be a valid number");
        }
    }
    
    private void validateTimezone(String value) throws ValidationException {
        try {
            TimeZone tz = TimeZone.getTimeZone(value);
            // Java returns GMT for invalid timezones, so check if it's what we asked for
            if (!tz.getID().equals(value) && !tz.getID().equals("GMT")) {
                throw new ValidationException("Invalid timezone ID: " + value);
            }
        } catch (Exception e) {
            throw new ValidationException("Invalid timezone: " + value);
        }
    }
    
    private void validateCronExpression(String value) throws ValidationException {
        try {
            CronExpression.parse(value);
        } catch (Exception e) {
            throw new ValidationException("Invalid cron expression: " + e.getMessage());
        }
    }
    
    private void validatePositiveInteger(String value) throws ValidationException {
        try {
            int num = Integer.parseInt(value);
            if (num <= 0) {
                throw new ValidationException("Value must be a positive integer");
            }
        } catch (NumberFormatException e) {
            throw new ValidationException("Value must be a valid integer");
        }
    }
    
    /**
     * Custom exception for validation errors.
     */
    public static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
    }
}
