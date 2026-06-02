package com.tbdev.teaneckminyanim.enums;

/**
 * Enum defining all application setting keys.
 * Provides type safety and prevents typos in setting names.
 */
public enum SettingKey {
    // Site Branding & Appearance
    SITE_NAME("site.name", SettingType.STRING, "Minyanim"),
    SITE_APP_COLOR("site.app.color", SettingType.STRING, "#275ed8"),
    SITE_ROOT_URL("site.root.url", SettingType.STRING, "http://localhost:8080"),
    SITE_SUPPORT_EMAIL("site.support.email", SettingType.STRING, "info@teaneckminyanim.com"),
    MOBILE_IOS_APP_URL("mobile.ios.app.url", SettingType.STRING, ""),
    MOBILE_GOOGLE_PLAY_URL("mobile.google.play.url", SettingType.STRING, ""),
    
    // Location & Geographic Settings
    LOCATION_NAME("location.name", SettingType.STRING, "Jerusalem, Israel"),
    LOCATION_LATITUDE("location.latitude", SettingType.DOUBLE, "31.7683"),
    LOCATION_LONGITUDE("location.longitude", SettingType.DOUBLE, "35.2137"),
    LOCATION_ELEVATION("location.elevation", SettingType.DOUBLE, "754"),
    
    // Timezone Settings
    TIMEZONE("timezone", SettingType.STRING, "Asia/Jerusalem"),
    
    // External Services
    MAPBOX_ACCESS_TOKEN("mapbox.access.token", SettingType.STRING, ""),
    FEEDBACK_GITHUB_OWNER("feedback.github.owner", SettingType.STRING, "jacobrosenfeld"),
    FEEDBACK_GITHUB_REPO("feedback.github.repo", SettingType.STRING, "Teaneck-Minyanim"),
    FEEDBACK_GITHUB_TOKEN("feedback.github.token", SettingType.STRING, "", true),

    // Email Provider Settings
    EMAIL_PROVIDER("email.provider", SettingType.STRING, ""),
    EMAIL_SMTP_HOST("email.smtp.host", SettingType.STRING, ""),
    EMAIL_SMTP_PORT("email.smtp.port", SettingType.INTEGER, "587"),
    EMAIL_SMTP_USERNAME("email.smtp.username", SettingType.STRING, "", true),
    EMAIL_SMTP_PASSWORD("email.smtp.password", SettingType.STRING, "", true),
    EMAIL_SMTP_STARTTLS_ENABLED("email.smtp.starttls.enabled", SettingType.BOOLEAN, "true"),
    EMAIL_FROM_ADDRESS("email.from.address", SettingType.STRING, ""),
    EMAIL_FROM_NAME("email.from.name", SettingType.STRING, ""),
    EMAIL_REPLY_TO("email.reply-to", SettingType.STRING, ""),
    EMAIL_SES_REGION("email.ses.region", SettingType.STRING, ""),
    EMAIL_SES_ACCESS_KEY_ID("email.ses.access-key-id", SettingType.STRING, "", true),
    EMAIL_SES_SECRET_ACCESS_KEY("email.ses.secret-access-key", SettingType.STRING, "", true),
    EMAIL_SES_CONFIGURATION_SET("email.ses.configuration-set", SettingType.STRING, ""),
    
    // Calendar Import Settings
    CALENDAR_IMPORT_CRON("calendar.import.cron", SettingType.STRING, "0 0 2 * * SUN"),
    CALENDAR_CLEANUP_DAYS("calendar.cleanup.days", SettingType.INTEGER, "30");
    
    private final String key;
    private final SettingType type;
    private final String defaultValue;
    private final boolean sensitive;
    
    SettingKey(String key, SettingType type, String defaultValue) {
        this(key, type, defaultValue, false);
    }

    SettingKey(String key, SettingType type, String defaultValue, boolean sensitive) {
        this.key = key;
        this.type = type;
        this.defaultValue = defaultValue;
        this.sensitive = sensitive;
    }
    
    public String getKey() {
        return key;
    }
    
    public SettingType getType() {
        return type;
    }
    
    public String getDefaultValue() {
        return defaultValue;
    }

    public boolean isSensitive() {
        return sensitive;
    }

    public String maskValue(String value) {
        if (!sensitive || value == null || value.isBlank()) {
            return value;
        }
        return "********";
    }
    
    public static SettingKey fromKey(String key) {
        for (SettingKey settingKey : values()) {
            if (settingKey.getKey().equals(key)) {
                return settingKey;
            }
        }
        throw new IllegalArgumentException("Unknown setting key: " + key);
    }
    
    /**
     * Returns a human-readable display name for the setting.
     */
    public String getDisplayName() {
        switch (this) {
            case SITE_NAME:
                return "Site Name";
            case SITE_APP_COLOR:
                return "App Theme Color";
            case SITE_ROOT_URL:
                return "Site Root URL";
            case SITE_SUPPORT_EMAIL:
                return "Support Email Address";
            case MOBILE_IOS_APP_URL:
                return "iOS App Store URL";
            case MOBILE_GOOGLE_PLAY_URL:
                return "Google Play Store URL";
            case LOCATION_NAME:
                return "Location Name";
            case LOCATION_LATITUDE:
                return "Latitude";
            case LOCATION_LONGITUDE:
                return "Longitude";
            case LOCATION_ELEVATION:
                return "Elevation (meters)";
            case TIMEZONE:
                return "Timezone";
            case MAPBOX_ACCESS_TOKEN:
                return "Mapbox Access Token";
            case FEEDBACK_GITHUB_OWNER:
                return "Feedback GitHub Owner";
            case FEEDBACK_GITHUB_REPO:
                return "Feedback GitHub Repository";
            case FEEDBACK_GITHUB_TOKEN:
                return "Feedback GitHub Token";
            case EMAIL_PROVIDER:
                return "Email Provider";
            case EMAIL_SMTP_HOST:
                return "SMTP Host";
            case EMAIL_SMTP_PORT:
                return "SMTP Port";
            case EMAIL_SMTP_USERNAME:
                return "SMTP Username";
            case EMAIL_SMTP_PASSWORD:
                return "SMTP Password";
            case EMAIL_SMTP_STARTTLS_ENABLED:
                return "SMTP STARTTLS Enabled";
            case EMAIL_FROM_ADDRESS:
                return "Email From Address";
            case EMAIL_FROM_NAME:
                return "Email From Name";
            case EMAIL_REPLY_TO:
                return "Email Reply-To Address";
            case EMAIL_SES_REGION:
                return "AWS SES Region";
            case EMAIL_SES_ACCESS_KEY_ID:
                return "AWS SES Access Key ID";
            case EMAIL_SES_SECRET_ACCESS_KEY:
                return "AWS SES Secret Access Key";
            case EMAIL_SES_CONFIGURATION_SET:
                return "AWS SES Configuration Set";
            case CALENDAR_IMPORT_CRON:
                return "Calendar Import Schedule (Cron)";
            case CALENDAR_CLEANUP_DAYS:
                return "Calendar Cleanup Threshold (Days)";
            default:
                return key;
        }
    }
    
    /**
     * Returns a description of what this setting controls.
     */
    public String getDescription() {
        switch (this) {
            case SITE_NAME:
                return "Name of the website displayed in the header and browser title";
            case SITE_APP_COLOR:
                return "Primary theme color for the application (hex color code, e.g., #275ed8)";
            case SITE_ROOT_URL:
                return "Root URL of the website (e.g., https://www.teaneckminyanim.com)";
            case SITE_SUPPORT_EMAIL:
                return "Support email address for contact and help inquiries";
            case MOBILE_IOS_APP_URL:
                return "Full iOS App Store URL used to configure Safari Smart App Banner (e.g., https://apps.apple.com/us/app/your-app/id1234567890)";
            case MOBILE_GOOGLE_PLAY_URL:
                return "Full Google Play Store URL for the Android app (e.g., https://play.google.com/store/apps/details?id=com.example.app)";
            case LOCATION_NAME:
                return "Display name for the location used in Zmanim calculations";
            case LOCATION_LATITUDE:
                return "Latitude coordinate for Zmanim calculations (-90 to 90)";
            case LOCATION_LONGITUDE:
                return "Longitude coordinate for Zmanim calculations (-180 to 180)";
            case LOCATION_ELEVATION:
                return "Elevation in meters above sea level (-500 to 9000)";
            case TIMEZONE:
                return "Timezone for all date/time calculations (e.g., America/New_York)";
            case MAPBOX_ACCESS_TOKEN:
                return "Access token for Mapbox API (required for map features)";
            case FEEDBACK_GITHUB_OWNER:
                return "GitHub user or organization that owns the repository where feedback issues are created";
            case FEEDBACK_GITHUB_REPO:
                return "GitHub repository where feedback issues are created";
            case FEEDBACK_GITHUB_TOKEN:
                return "Server-side GitHub token used to create feedback issues. Use a fine-grained token with Issues read/write access for the configured repository.";
            case EMAIL_PROVIDER:
                return "Email delivery provider. Supported values: SMTP or SES. Leave blank to disable email sending.";
            case EMAIL_SMTP_HOST:
                return "SMTP server host name, such as smtp.example.com";
            case EMAIL_SMTP_PORT:
                return "SMTP server port, typically 587 for STARTTLS or 465 for TLS";
            case EMAIL_SMTP_USERNAME:
                return "SMTP username used for authenticated SMTP delivery";
            case EMAIL_SMTP_PASSWORD:
                return "SMTP password used for authenticated SMTP delivery";
            case EMAIL_SMTP_STARTTLS_ENABLED:
                return "Whether SMTP STARTTLS should be enabled";
            case EMAIL_FROM_ADDRESS:
                return "Default From email address for outgoing application emails";
            case EMAIL_FROM_NAME:
                return "Optional display name for outgoing application emails";
            case EMAIL_REPLY_TO:
                return "Optional Reply-To address for outgoing application emails";
            case EMAIL_SES_REGION:
                return "AWS region for SES API delivery, such as us-east-1";
            case EMAIL_SES_ACCESS_KEY_ID:
                return "AWS access key ID used for SES API delivery";
            case EMAIL_SES_SECRET_ACCESS_KEY:
                return "AWS secret access key used for SES API delivery";
            case EMAIL_SES_CONFIGURATION_SET:
                return "Optional SES configuration set name for outgoing emails";
            case CALENDAR_IMPORT_CRON:
                return "Cron expression for automatic calendar import schedule (e.g., '0 0 2 * * SUN' for Sundays at 2am)";
            case CALENDAR_CLEANUP_DAYS:
                return "Number of days to keep old calendar entries before cleanup (must be > 0)";
            default:
                return "";
        }
    }
    
    /**
     * Returns the category/group this setting belongs to for UI organization.
     */
    public String getCategory() {
        if (key.startsWith("site.")) {
            return "Site Branding & Appearance";
        } else if (key.startsWith("location.")) {
            return "Location & Coordinates";
        } else if (key.equals("timezone")) {
            return "Timezone";
        } else if (key.startsWith("mobile.")) {
            return "Mobile Apps";
        } else if (key.startsWith("mapbox.") || key.startsWith("feedback.github.")) {
            return "External Services";
        } else if (key.startsWith("email.")) {
            return "Email";
        } else if (key.startsWith("calendar.")) {
            return "Calendar Import";
        }
        return "General";
    }
}
