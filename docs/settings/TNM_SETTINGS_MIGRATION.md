# TNMSettings to ApplicationSettings Migration Guide

## Overview
This guide explains how to migrate duplicate settings from the `SETTINGS` table (TNMSettings) to the new `APPLICATION_SETTINGS` table.

## Settings Migration Plan

### Settings to DELETE from TNMSettings (SETTINGS table)
These are now managed by ApplicationSettings:

| TNMSettings ID | Setting Name | New ApplicationSettings Key | Action |
|----------------|--------------|----------------------------|---------|
| (ID for "Site location") | Site location (City, State) | `location.name` | DELETE from SETTINGS |
| (ID for "Latitude") | Latitude | `location.latitude` | DELETE from SETTINGS |
| (ID for "Longitude") | Longitude | `location.longitude` | DELETE from SETTINGS |
| (ID for "Elevation") | Elevation | `location.elevation` | DELETE from SETTINGS |
| (ID for "Time Zone") | Time Zone | `timezone` | DELETE from SETTINGS |

### New Settings Added to ApplicationSettings
These settings were in TNMSettings but are now properly typed in ApplicationSettings:

| Setting | ApplicationSettings Key | Default Value | Notes |
|---------|------------------------|---------------|-------|
| Site Name | `site.name` | "Teaneck Minyanim" | Site display name |
| App Color | `site.app.color` | "#275ed8" | Hex color code |
| Mapbox Access Token | `mapbox.access.token` | "" | API token for maps |

### Settings to KEEP in TNMSettings (SETTINGS table)
These are notification-specific and should remain:

| Setting | Purpose | Keep/Delete |
|---------|---------|-------------|
| Home Page Announcement | User-facing notification with expiration/max displays | **KEEP** |
| Home Page Popup | User-facing popup with expiration/max displays | **KEEP** |

## Migration SQL Scripts

### Step 1: Migrate existing TNMSettings values to ApplicationSettings

```sql
-- Migrate Site Name (if it exists in SETTINGS table)
INSERT INTO APPLICATION_SETTINGS (SETTING_KEY, SETTING_VALUE, SETTING_TYPE, DESCRIPTION, CATEGORY, VERSION)
SELECT 
    'site.name',
    TEXT,
    'STRING',
    'Name of the website displayed in the header and browser title',
    'Site Branding & Appearance',
    0
FROM SETTINGS 
WHERE SETTING = 'Site Name'
ON DUPLICATE KEY UPDATE SETTING_VALUE = VALUES(SETTING_VALUE);

-- Migrate App Color
INSERT INTO APPLICATION_SETTINGS (SETTING_KEY, SETTING_VALUE, SETTING_TYPE, DESCRIPTION, CATEGORY, VERSION)
SELECT 
    'site.app.color',
    TEXT,
    'STRING',
    'Primary theme color for the application (hex color code, e.g., #275ed8)',
    'Site Branding & Appearance',
    0
FROM SETTINGS 
WHERE SETTING = 'App Color'
ON DUPLICATE KEY UPDATE SETTING_VALUE = VALUES(SETTING_VALUE);

-- Migrate Mapbox Access Token
INSERT INTO APPLICATION_SETTINGS (SETTING_KEY, SETTING_VALUE, SETTING_TYPE, DESCRIPTION, CATEGORY, VERSION)
SELECT 
    'mapbox.access.token',
    TEXT,
    'STRING',
    'Access token for Mapbox API (required for map features)',
    'External Services',
    0
FROM SETTINGS 
WHERE SETTING = 'Mapbox Access Token'
ON DUPLICATE KEY UPDATE SETTING_VALUE = VALUES(SETTING_VALUE);
```

### Step 2: Delete duplicate settings from TNMSettings

**IMPORTANT:** Only run this AFTER verifying that ApplicationSettings has been properly initialized with default values and the above migration has completed successfully.

```sql
-- Delete duplicate location/coordinate settings
DELETE FROM SETTINGS WHERE SETTING IN (
    'Site location (City, State)',
    'Latitude',
    'Longitude',
    'Elevation',
    'Time Zone'
);

-- Optionally delete the newly migrated settings if you want them ONLY in ApplicationSettings
DELETE FROM SETTINGS WHERE SETTING IN (
    'Site Name',
    'App Color',
    'Mapbox Access Token'
);
```

## Safe Migration Process

### Phase 1: Preparation
1. **Backup your database** before making any changes
   ```bash
   mysqldump -u root -p minyanim > backup_before_migration.sql
   ```

2. Verify current values in TNMSettings:
   ```sql
   SELECT ID, SETTING, TEXT FROM SETTINGS 
   WHERE SETTING IN (
       'Site Name', 'App Color', 'Site location (City, State)',
       'Latitude', 'Longitude', 'Elevation', 'Time Zone', 
       'Mapbox Access Token'
   );
   ```

### Phase 2: Deploy New Code
1. Deploy the updated application with new ApplicationSettings code
2. On first startup, ApplicationSettingsService will auto-initialize with defaults
3. Verify ApplicationSettings table was created:
   ```sql
   SELECT * FROM APPLICATION_SETTINGS;
   ```

### Phase 3: Migrate Data
1. Run the migration SQL from Step 1 above to copy values from TNMSettings to ApplicationSettings
2. Verify values were copied correctly:
   ```sql
   SELECT SETTING_KEY, SETTING_VALUE FROM APPLICATION_SETTINGS
   WHERE SETTING_KEY IN (
       'site.name', 'site.app.color', 'mapbox.access.token'
   );
   ```

### Phase 4: Clean Up
1. **ONLY after verifying the application works correctly**, run the delete SQL from Step 2
2. Verify TNMSettings only contains notification-related settings:
   ```sql
   SELECT ID, SETTING FROM SETTINGS;
   ```
   Should only show: Home Page Announcement, Home Page Popup

## Post-Migration Verification

1. **Check Settings Page**: Navigate to `/admin/settings` as super admin
   - Verify "System Configuration" section shows all new settings
   - Verify "Notification Settings" section shows only Home Page Announcement and Home Page Popup

2. **Test Settings Updates**: Try editing each setting through the admin UI
   - Verify validation works (e.g., hex color must be #XXXXXX format)
   - Verify changes persist after page refresh

3. **Test Application Functionality**:
   - Homepage loads correctly with proper site name
   - Zmanim calculations work (uses location/timezone from ApplicationSettings)
   - Map features work if Mapbox token is configured

## Rollback Plan

If issues occur, you can roll back:

```sql
-- Restore from backup
mysql -u root -p minyanim < backup_before_migration.sql
```

## Settings That Should Be Deleted from TNMSettings

Based on your current TNMSettings table, you should **DELETE** these entries (IDs will vary in your database):

1. **Site location (City, State)** - Now `location.name` in ApplicationSettings
2. **Latitude** - Now `location.latitude` in ApplicationSettings
3. **Longitude** - Now `location.longitude` in ApplicationSettings
4. **Elevation** - Now `location.elevation` in ApplicationSettings
5. **Time Zone** - Now `timezone` in ApplicationSettings

You can optionally also delete these (as they're now in ApplicationSettings):
6. **Site Name** - Now `site.name` in ApplicationSettings
7. **App Color** - Now `site.app.color` in ApplicationSettings
8. **Mapbox Access Token** - Now `mapbox.access.token` in ApplicationSettings

## Settings to Keep in TNMSettings

**KEEP** these in the SETTINGS table (they use notification-specific features like expiration dates and max displays):

1. **Home Page Announcement** - Notification with enabled/disabled, expiration, max displays
2. **Home Page Popup** - Notification with enabled/disabled, expiration, max displays

These should remain in TNMSettings because they use fields that ApplicationSettings doesn't have:
- `ENABLED` (Enabled/Disabled/N/A)
- `EXPIRATION_DATE`
- `MAX_DISPLAYS`
- `VERSION` (for tracking views)

## Summary

**To Answer Your Question:**

You should **DELETE from TNMSettings** (total of 5-8 entries):
- ✅ Site location (City, State)
- ✅ Latitude
- ✅ Longitude
- ✅ Elevation
- ✅ Time Zone
- ✅ Site Name (migrated to ApplicationSettings)
- ✅ App Color (migrated to ApplicationSettings)
- ✅ Mapbox Access Token (migrated to ApplicationSettings)

You should **KEEP in TNMSettings** (2 entries):
- ✅ Home Page Announcement
- ✅ Home Page Popup

The database tables will coexist:
- `APPLICATION_SETTINGS` - For typed, validated configuration settings
- `SETTINGS` (TNMSettings) - For user-facing notifications with expiration/display tracking
