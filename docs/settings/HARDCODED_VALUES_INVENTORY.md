# Hardcoded Values Inventory

This document catalogs all hardcoded values in the Teaneck Minyanim codebase that should be moved to Application Settings.

## A) Application-Wide Settings (Global)

These settings apply to the entire application and should be configurable via the Application Settings page.

### 1. Location & Geographic Coordinates

**Purpose**: Used for calculating Jewish prayer times (Zmanim) based on geographic location

| Setting | Current Value | Files |
|---------|--------------|-------|
| Location Name | "Teaneck, NJ" | `ZmanimService.java:32`, `ZmanimHandler.java:32` |
| Latitude | 40.906871 | `ZmanimService.java:33`, `ZmanimHandler.java:33`, `ZmanimController.java:31` |
| Longitude | -74.020924 | `ZmanimService.java:34`, `ZmanimHandler.java:34`, `ZmanimController.java:32` |
| Elevation (meters) | 24 | `ZmanimService.java:35`, `ZmanimHandler.java:35` |

**Recommendation**: Move to application settings with validation (lat: -90 to 90, lon: -180 to 180, elevation: 0 to 9000)

### 2. Timezone Configuration

**Purpose**: Ensures all date/time calculations use consistent timezone

| Setting | Current Value | Files |
|---------|--------------|-------|
| Application Timezone | "America/New_York" | 7 files (see below) |

**Files with timezone hardcoding**:
- `TeaneckMinyanimApplication.java:17` (application default)
- `ZmanimService.java:30`
- `ZmanimHandler.java:31`
- `ZmanimController.java:28`
- `AdminController.java:55`
- `MinyanEvent.java:12`
- `KolhaMinyanim.java:12`
- `CalendarImportProvider.java:32`
- `MinyanClassifier.java:368, 404`

**Recommendation**: Move to application settings with validation (must be valid Java TimeZone ID)

### 3. Scheduled Jobs Configuration

**Purpose**: Controls when automated tasks run

| Setting | Current Value | File | Description |
|---------|--------------|------|-------------|
| Calendar Import Cron | "0 0 2 * * SUN" | `CalendarImportScheduler.java:26` | Runs every Sunday at 2:00 AM |
| Cleanup Threshold Days | 30 | `CalendarImportScheduler.java:56` (comment) | Delete entries older than 30 days |

**Recommendation**: Move to application settings with validation (valid cron expression, days > 0)

### 4. Notification System (Already in TNMSettings)

**Purpose**: Controls homepage notifications and banners

| Setting | Current Implementation |
|---------|----------------------|
| Notification enabled/disabled | TNMSettings.enabled |
| Notification text | TNMSettings.text |
| Expiration date | TNMSettings.expirationDate |
| Max displays | TNMSettings.maxDisplays |

**Recommendation**: Keep in database but enhance validation and structure

## B) Organization-Level Settings (Per Org/Shul)

These settings vary by organization and should be managed at the org level.

### 1. Organization-Specific Settings (Already Implemented)

| Setting | Storage | Description |
|---------|---------|-------------|
| Organization Name | Organization.name | Display name |
| Organization Nusach | Organization.nusach | Prayer rite preference |
| Calendar Import URL | Organization.calendarUrl | External calendar source |
| Use Scraped Calendar | Organization.useScrapedCalendar | Enable calendar import |
| Organization Color | Organization.org_color | Theme color |

**Recommendation**: Already properly implemented, no changes needed

## C) Developer-Only Constants (Remain in Code)

These values are technical constants that should not be exposed to admins.

### 1. Date Format Patterns

**Files**: `ZmanimService.java`, `AdminController.java`
```java
SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM d, yyyy | h:mm aa");
SimpleDateFormat onlyDateFormat = new SimpleDateFormat("EEEE, MMMM d");
SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm aa");
```

**Recommendation**: Keep in code - these are technical formatting patterns

### 2. Schedule Time Prefixes

**Files**: `MinyanTime.java`
```java
- "T" prefix for fixed times
- "R" prefix for dynamic zmanim-based times  
- "Q" prefix for rounded times
- "NM" for no minyan
```

**Recommendation**: Keep in code - these are internal data format specifications

### 3. Database Configuration

**Files**: `application.properties`
```properties
spring.datasource.url=jdbc:mariadb://localhost:3306/minyanim
spring.datasource.username=root
spring.datasource.password=passw0rd
```

**Recommendation**: Keep in application.properties - Spring Boot standard configuration. Should use environment variables for production.

### 4. Web Security Configuration

**Files**: `WebSecurityConfiguration.java`
- Security roles, permissions, URL patterns

**Recommendation**: Keep in code - these are security-critical configurations

## Summary of Actions Required

### Move to Settings (8 settings):
1. ✅ Location Name (String)
2. ✅ Latitude (Double, -90 to 90)
3. ✅ Longitude (Double, -180 to 180)
4. ✅ Elevation (Double, 0 to 9000 meters)
5. ✅ Application Timezone (String, valid timezone ID)
6. ✅ Calendar Import Cron Expression (String, valid cron)
7. ✅ Cleanup Threshold Days (Integer, > 0)
8. ✅ Enhance existing notification settings with validation

### Keep in Code:
- Date format patterns
- Schedule time format specifications  
- Database connection settings (application.properties)
- Security configurations

### Already Properly Implemented:
- Organization-specific settings in Organization entity
- TNMSettings for notifications (needs enhancement only)

## Migration Strategy

1. **Phase 1**: Create new strongly-typed ApplicationSettings entity
2. **Phase 2**: Create SettingsService with type-safe getters and validation
3. **Phase 3**: Seed default settings on first run
4. **Phase 4**: Update all code to use SettingsService instead of hardcoded values
5. **Phase 5**: Create new admin settings page with validation
6. **Phase 6**: Test and verify all functionality

## Backwards Compatibility

- Existing TNMSettings table can coexist with new ApplicationSettings
- TNMSettings continues to handle notifications
- ApplicationSettings handles application-wide configuration
- Migration is additive, no data loss
