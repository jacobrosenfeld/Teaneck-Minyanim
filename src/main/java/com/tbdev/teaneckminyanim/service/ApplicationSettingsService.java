package com.tbdev.teaneckminyanim.service;

import com.kosherjava.zmanim.util.GeoLocation;
import com.tbdev.teaneckminyanim.enums.SettingKey;
import com.tbdev.teaneckminyanim.enums.SettingType;
import com.tbdev.teaneckminyanim.model.ApplicationSettings;
import com.tbdev.teaneckminyanim.repo.ApplicationSettingsRepository;
import com.tbdev.teaneckminyanim.security.SensitiveSettingCrypto;
import com.tbdev.teaneckminyanim.security.SensitiveSettingCrypto.SensitiveSettingCryptoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Optional;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Centralized service for managing application-wide settings.
 * Provides type-safe getters, validation, caching, and default values.
 */
@Slf4j
@Service
public class ApplicationSettingsService {

    private final ApplicationSettingsRepository repository;
    private final SensitiveSettingCrypto sensitiveSettingCrypto;
    
    // Cache for performance
    private final Map<String, String> settingsCache = new ConcurrentHashMap<>();
    private static final Pattern IOS_APP_ID_PATH_PATTERN = Pattern.compile("/id(\\d+)(?:[/?#]|$)");
    private static final Pattern IOS_APP_ID_QUERY_PATTERN = Pattern.compile("[?&]id=(\\d+)(?:[&#]|$)");
    private static final Pattern NUMERIC_ID_PATTERN = Pattern.compile("^\\d+$");

    @Autowired
    public ApplicationSettingsService(
            ApplicationSettingsRepository repository,
            SensitiveSettingCrypto sensitiveSettingCrypto) {
        this.repository = repository;
        this.sensitiveSettingCrypto = sensitiveSettingCrypto == null
                ? SensitiveSettingCrypto.disabled()
                : sensitiveSettingCrypto;
    }

    public ApplicationSettingsService(ApplicationSettingsRepository repository) {
        this(repository, SensitiveSettingCrypto.disabled());
    }
    
    /**
     * Initialize default settings on application startup if they don't exist.
     */
    @PostConstruct
    public void initializeDefaults() {
        log.info("Initializing application settings with defaults");
        
        for (SettingKey key : SettingKey.values()) {
            Optional<ApplicationSettings> existing = repository.findBySettingKey(key.getKey());
            if (existing.isEmpty()) {
                ApplicationSettings setting = new ApplicationSettings(
                    key.getKey(),
                    key.getDefaultValue(),
                    key.getType().name()
                );
                setting.setDescription(key.getDescription());
                setting.setCategory(key.getCategory());
                repository.save(setting);
                settingsCache.put(key.getKey(), key.getDefaultValue());
                log.info("Created default setting: {} = {}", key.getKey(), key.maskValue(key.getDefaultValue()));
            } else {
                settingsCache.put(key.getKey(), readStoredSettingValue(key, existing.get()));
            }
        }
    }
    
    /**
     * Get a setting value as a string.
     */
    public String getString(SettingKey key) {
        String value = settingsCache.get(key.getKey());
        if (value == null) {
            Optional<ApplicationSettings> setting = repository.findBySettingKey(key.getKey());
            value = setting
                .map(applicationSettings -> readStoredSettingValue(key, applicationSettings))
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
     * Get site root URL.
     */
    public String getSiteRootUrl() {
        return getString(SettingKey.SITE_ROOT_URL);
    }
    
    /**
     * Get support email address.
     */
    public String getSupportEmail() {
        return getString(SettingKey.SITE_SUPPORT_EMAIL);
    }
    
    /**
     * Get Mapbox access token.
     */
    public String getMapboxAccessToken() {
        return getString(SettingKey.MAPBOX_ACCESS_TOKEN);
    }

    public String getFeedbackGithubOwner() {
        return getString(SettingKey.FEEDBACK_GITHUB_OWNER);
    }

    public String getFeedbackGithubRepo() {
        return getString(SettingKey.FEEDBACK_GITHUB_REPO);
    }

    public String getFeedbackGithubToken() {
        return getString(SettingKey.FEEDBACK_GITHUB_TOKEN);
    }

    public String getFeedbackMobileAppToken() {
        return getString(SettingKey.FEEDBACK_MOBILE_APP_TOKEN);
    }

    public boolean isFeedbackEnabled() {
        return hasText(getFeedbackGithubOwner())
                && hasText(getFeedbackGithubRepo())
                && hasText(getFeedbackGithubToken());
    }

    public boolean isMobileFeedbackAuthEnabled() {
        return hasText(getFeedbackMobileAppToken());
    }

    public String getRecaptchaSiteKey() {
        return getString(SettingKey.RECAPTCHA_SITE_KEY);
    }

    public String getRecaptchaSecretKey() {
        return getString(SettingKey.RECAPTCHA_SECRET_KEY);
    }

    public boolean isRecaptchaEnabled() {
        return hasText(getRecaptchaSiteKey()) && hasText(getRecaptchaSecretKey());
    }

    public String getEmailProvider() {
        return getString(SettingKey.EMAIL_PROVIDER);
    }

    public String getEmailSmtpHost() {
        return getString(SettingKey.EMAIL_SMTP_HOST);
    }

    public Integer getEmailSmtpPort() {
        return getInteger(SettingKey.EMAIL_SMTP_PORT);
    }

    public String getEmailSmtpUsername() {
        return getString(SettingKey.EMAIL_SMTP_USERNAME);
    }

    public String getEmailSmtpPassword() {
        return getString(SettingKey.EMAIL_SMTP_PASSWORD);
    }

    public Boolean isEmailSmtpStartTlsEnabled() {
        return getBoolean(SettingKey.EMAIL_SMTP_STARTTLS_ENABLED);
    }

    public String getEmailFromAddress() {
        return getString(SettingKey.EMAIL_FROM_ADDRESS);
    }

    public String getEmailFromName() {
        return getString(SettingKey.EMAIL_FROM_NAME);
    }

    public String getEmailReplyTo() {
        return getString(SettingKey.EMAIL_REPLY_TO);
    }

    public String getEmailSesRegion() {
        return getString(SettingKey.EMAIL_SES_REGION);
    }

    public String getEmailSesAccessKeyId() {
        return getString(SettingKey.EMAIL_SES_ACCESS_KEY_ID);
    }

    public String getEmailSesSecretAccessKey() {
        return getString(SettingKey.EMAIL_SES_SECRET_ACCESS_KEY);
    }

    public String getEmailSesConfigurationSet() {
        return getString(SettingKey.EMAIL_SES_CONFIGURATION_SET);
    }

    /**
     * Get iOS App Store URL.
     */
    public String getIosAppUrl() {
        return getString(SettingKey.MOBILE_IOS_APP_URL);
    }

    /**
     * Get Google Play Store URL.
     */
    public String getGooglePlayUrl() {
        return getString(SettingKey.MOBILE_GOOGLE_PLAY_URL);
    }

    /**
     * Build Smart App Banner meta content from the configured iOS App Store URL.
     * Returns null when no valid app id can be extracted.
     */
    public String getAppleSmartAppBannerContent() {
        return buildAppleSmartAppBannerContent(getIosAppUrl());
    }

    /**
     * Extract numeric app id from an iOS App Store URL.
     * Supported formats include:
     * - https://apps.apple.com/us/app/app-name/id1234567890
     * - https://apps.apple.com/app/id1234567890
     * - 1234567890
     */
    public static String extractIosAppId(String iosAppUrl) {
        if (iosAppUrl == null) {
            return null;
        }

        String normalized = iosAppUrl.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        if (NUMERIC_ID_PATTERN.matcher(normalized).matches()) {
            return normalized;
        }

        Matcher pathMatcher = IOS_APP_ID_PATH_PATTERN.matcher(normalized);
        if (pathMatcher.find()) {
            return pathMatcher.group(1);
        }

        Matcher queryMatcher = IOS_APP_ID_QUERY_PATTERN.matcher(normalized);
        if (queryMatcher.find()) {
            return queryMatcher.group(1);
        }

        return null;
    }

    /**
     * Builds the `apple-itunes-app` content attribute value from an App Store URL.
     */
    public static String buildAppleSmartAppBannerContent(String iosAppUrl) {
        String appId = extractIosAppId(iosAppUrl);
        if (appId == null) {
            return null;
        }
        return "app-id=" + appId;
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
        String normalizedValue = value == null ? null : value.trim();

        // Validate the value
        validateSettingValue(key, normalizedValue);
        
        ApplicationSettings setting = repository.findBySettingKey(key.getKey())
            .orElseThrow(() -> new IllegalStateException("Setting not found: " + key.getKey()));
        
        setting.setSettingValue(valueForStorage(key, normalizedValue));
        repository.save(setting);
        
        // Update cache
        settingsCache.put(key.getKey(), normalizedValue);
        
        log.info("Updated setting: {} = {}", key.getKey(), key.maskValue(normalizedValue));
    }
    
    /**
     * Update a setting by key string (used by admin controller).
     */
    public void updateSettingByKey(String keyStr, String value) throws ValidationException {
        SettingKey key = SettingKey.fromKey(keyStr);
        updateSetting(key, value);
    }

    public boolean isSensitiveSetting(String keyStr) {
        try {
            return SettingKey.fromKey(keyStr).isSensitive();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public String safeValueForLog(String keyStr, String value) {
        try {
            return SettingKey.fromKey(keyStr).maskValue(value);
        } catch (IllegalArgumentException e) {
            return value;
        }
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
     * Get settings grouped by category while omitting categories that have a dedicated editor.
     */
    public Map<String, List<ApplicationSettings>> getSettingsByCategoryExcluding(Set<String> excludedCategories) {
        return getAllSettings().stream()
            .filter(setting -> !excludedCategories.contains(
                setting.getCategory() != null ? setting.getCategory() : "General"))
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
            try {
                SettingKey key = SettingKey.fromKey(setting.getSettingKey());
                settingsCache.put(setting.getSettingKey(), readStoredSettingValue(key, setting));
            } catch (IllegalArgumentException e) {
                settingsCache.put(setting.getSettingKey(), setting.getSettingValue());
            }
        }
        log.info("Settings cache refreshed");
    }

    private String valueForStorage(SettingKey key, String value) throws ValidationException {
        if (!key.isSensitive() || value == null || value.isBlank()) {
            return value;
        }

        if (!sensitiveSettingCrypto.isConfigured()) {
            throw new ValidationException(
                    "Sensitive settings encryption key is not configured. Set APP_SETTINGS_ENCRYPTION_KEY before saving this field.");
        }

        try {
            return sensitiveSettingCrypto.encrypt(value);
        } catch (SensitiveSettingCryptoException e) {
            throw new ValidationException(
                    "Sensitive setting could not be encrypted. Check APP_SETTINGS_ENCRYPTION_KEY.", e);
        }
    }

    private String readStoredSettingValue(SettingKey key, ApplicationSettings setting) {
        String storedValue = setting.getSettingValue();
        if (!key.isSensitive() || storedValue == null || storedValue.isBlank()) {
            return storedValue;
        }

        if (sensitiveSettingCrypto.isEncrypted(storedValue)) {
            return sensitiveSettingCrypto.decrypt(storedValue);
        }

        if (!sensitiveSettingCrypto.isConfigured()) {
            throw new IllegalStateException(
                    "Sensitive setting " + key.getKey()
                            + " is stored as plaintext. Set APP_SETTINGS_ENCRYPTION_KEY so it can be encrypted before startup.");
        }

        setting.setSettingValue(sensitiveSettingCrypto.encrypt(storedValue));
        repository.save(setting);
        log.info("Migrated sensitive setting to encrypted storage: {}", key.getKey());
        return storedValue;
    }
    
    /**
     * Validate a setting value before saving.
     */
    private void validateSettingValue(SettingKey key, String value) throws ValidationException {
        if (value == null) {
            throw new ValidationException("Setting value cannot be empty");
        }

        if (value.isEmpty()) {
            if (isOptionalSetting(key)) {
                return;
            }
            throw new ValidationException("Setting value cannot be empty");
        }

        switch (key) {
            case SITE_APP_COLOR:
                validateHexColor(value);
                break;
            case SITE_SUPPORT_EMAIL:
                validateEmail(value, "Support email");
                break;
            case EMAIL_PROVIDER:
                validateEmailProvider(value);
                break;
            case EMAIL_SMTP_PORT:
                validateTcpPort(value);
                break;
            case EMAIL_SMTP_STARTTLS_ENABLED:
                validateBoolean(value, "SMTP STARTTLS enabled");
                break;
            case EMAIL_FROM_ADDRESS:
                validateEmail(value, "Email from address");
                break;
            case EMAIL_REPLY_TO:
                validateEmail(value, "Email reply-to address");
                break;
            case EMAIL_SES_REGION:
                validateAwsRegion(value);
                break;
            case FEEDBACK_GITHUB_OWNER:
                validateGithubOwner(value);
                break;
            case FEEDBACK_GITHUB_REPO:
                validateGithubRepo(value);
                break;
            case SITE_ROOT_URL:
                validateUrl(value, "Root URL");
                break;
            case MOBILE_IOS_APP_URL:
                validateUrl(value, "iOS App Store URL");
                if (extractIosAppId(value) == null) {
                    throw new ValidationException("iOS App Store URL must include a valid App Store app id (e.g., .../id1234567890)");
                }
                break;
            case MOBILE_GOOGLE_PLAY_URL:
                validateUrl(value, "Google Play URL");
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

    private boolean isOptionalSetting(SettingKey key) {
        switch (key) {
            case MAPBOX_ACCESS_TOKEN:
            case FEEDBACK_GITHUB_TOKEN:
            case FEEDBACK_MOBILE_APP_TOKEN:
            case RECAPTCHA_SITE_KEY:
            case RECAPTCHA_SECRET_KEY:
            case MOBILE_IOS_APP_URL:
            case MOBILE_GOOGLE_PLAY_URL:
            case EMAIL_PROVIDER:
            case EMAIL_SMTP_HOST:
            case EMAIL_SMTP_USERNAME:
            case EMAIL_SMTP_PASSWORD:
            case EMAIL_FROM_ADDRESS:
            case EMAIL_FROM_NAME:
            case EMAIL_REPLY_TO:
            case EMAIL_SES_REGION:
            case EMAIL_SES_ACCESS_KEY_ID:
            case EMAIL_SES_SECRET_ACCESS_KEY:
            case EMAIL_SES_CONFIGURATION_SET:
                return true;
            default:
                return false;
        }
    }
    
    private void validateHexColor(String value) throws ValidationException {
        if (!value.matches("^#[0-9A-Fa-f]{6}$")) {
            throw new ValidationException("App color must be a valid hex color code (e.g., #275ed8)");
        }
    }
    
    private void validateEmail(String value, String fieldName) throws ValidationException {
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        if (!value.matches(emailRegex)) {
            throw new ValidationException(fieldName + " must be a valid email address");
        }
    }

    private void validateEmailProvider(String value) throws ValidationException {
        String normalized = value.toUpperCase(Locale.ROOT);
        if (!normalized.equals("SMTP") && !normalized.equals("SES")) {
            throw new ValidationException("Email provider must be SMTP or SES");
        }
    }

    private void validateTcpPort(String value) throws ValidationException {
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) {
                throw new ValidationException("SMTP port must be between 1 and 65535");
            }
        } catch (NumberFormatException e) {
            throw new ValidationException("SMTP port must be a valid integer");
        }
    }

    private void validateBoolean(String value, String fieldName) throws ValidationException {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!normalized.equals("true") && !normalized.equals("false")) {
            throw new ValidationException(fieldName + " must be true or false");
        }
    }

    private void validateAwsRegion(String value) throws ValidationException {
        if (!value.matches("^[a-z]{2}-[a-z]+-\\d$")) {
            throw new ValidationException("AWS region must look like us-east-1");
        }
    }

    private void validateGithubOwner(String value) throws ValidationException {
        if (!value.matches("^[A-Za-z0-9][A-Za-z0-9-]{0,38}$")) {
            throw new ValidationException("GitHub owner must be a valid user or organization name");
        }
    }

    private void validateGithubRepo(String value) throws ValidationException {
        if (!value.matches("^[A-Za-z0-9._-]+$")) {
            throw new ValidationException("GitHub repository must be a valid repository name");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
    
    private void validateUrl(String value, String fieldName) throws ValidationException {
        if (!value.matches("^https?://[^\\s/$.?#].[^\\s]*$")) {
            throw new ValidationException(fieldName + " must be a valid URL (e.g., https://www.example.com)");
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
            if (elev < -1500 || elev > 9000) {
                throw new ValidationException("Elevation must be between -1500 and 9000 meters");
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

        public ValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
