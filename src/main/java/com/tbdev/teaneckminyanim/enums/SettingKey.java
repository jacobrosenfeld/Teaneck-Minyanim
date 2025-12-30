package com.tbdev.teaneckminyanim.enums;

/**
 * Enum defining all application setting keys.
 * Provides type safety and prevents typos in setting names.
 */
public enum SettingKey {
    // Site Branding & Appearance
    SITE_NAME("site.name", SettingType.STRING, "Teaneck Minyanim"),
    SITE_APP_COLOR("site.app.color", SettingType.STRING, "#275ed8"),
    
    // Location & Geographic Settings
    LOCATION_NAME("location.name", SettingType.STRING, "Teaneck, NJ"),
    LOCATION_LATITUDE("location.latitude", SettingType.DOUBLE, "40.906871"),
    LOCATION_LONGITUDE("location.longitude", SettingType.DOUBLE, "-74.020924"),
    LOCATION_ELEVATION("location.elevation", SettingType.DOUBLE, "24"),
    
    // Timezone Settings
    TIMEZONE("timezone", SettingType.STRING, "America/New_York"),
    
    // External Services
    MAPBOX_ACCESS_TOKEN("mapbox.access.token", SettingType.STRING, ""),
    
    // Calendar Import Settings
    CALENDAR_IMPORT_CRON("calendar.import.cron", SettingType.STRING, "0 0 2 * * SUN"),
    CALENDAR_CLEANUP_DAYS("calendar.cleanup.days", SettingType.INTEGER, "30");
    
    private final String key;
    private final SettingType type;
    private final String defaultValue;
    
    SettingKey(String key, SettingType type, String defaultValue) {
        this.key = key;
        this.type = type;
        this.defaultValue = defaultValue;
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
            case LOCATION_NAME:
                return "Display name for the location used in Zmanim calculations";
            case LOCATION_LATITUDE:
                return "Latitude coordinate for Zmanim calculations (-90 to 90)";
            case LOCATION_LONGITUDE:
                return "Longitude coordinate for Zmanim calculations (-180 to 180)";
            case LOCATION_ELEVATION:
                return "Elevation in meters above sea level (0 to 9000)";
            case TIMEZONE:
                return "Timezone for all date/time calculations (e.g., America/New_York)";
            case MAPBOX_ACCESS_TOKEN:
                return "Access token for Mapbox API (required for map features)";
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
        } else if (key.startsWith("mapbox.")) {
            return "External Services";
        } else if (key.startsWith("calendar.")) {
            return "Calendar Import";
        }
        return "General";
    }
}
