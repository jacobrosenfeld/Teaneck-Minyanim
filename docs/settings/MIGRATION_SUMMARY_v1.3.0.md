# Settings System Migration Summary - v1.3.0

## Overview
Version 1.3.0 introduces a comprehensive application settings system that replaces hardcoded configuration values with a database-backed, strongly-typed settings framework.

## What Changed

### Replaced Hardcoded Values
The following hardcoded values have been moved to the Application Settings system:

1. **Geographic Location** (previously hardcoded in 6 classes):
   - Location Name: "Teaneck, NJ"
   - Latitude: 40.906871
   - Longitude: -74.020924
   - Elevation: 24 meters

2. **Timezone** (previously hardcoded in 7 classes):
   - Application Timezone: "America/New_York"

3. **Scheduled Jobs** (previously hardcoded):
   - Calendar Import Cron: "0 0 2 * * SUN" (Sundays at 2am)
   - Calendar Cleanup Days: 30

### New Database Table
A new `APPLICATION_SETTINGS` table is automatically created on first startup:

```sql
CREATE TABLE APPLICATION_SETTINGS (
    SETTING_KEY VARCHAR(100) PRIMARY KEY,
    SETTING_VALUE TEXT NOT NULL,
    SETTING_TYPE VARCHAR(50) NOT NULL,
    DESCRIPTION TEXT,
    CATEGORY VARCHAR(100),
    VERSION BIGINT
);
```

### New Admin Interface
- **URL**: `/admin/application-settings`
- **Access**: Super Admin only
- **Features**:
  - Settings grouped by category
  - Edit modal with validation hints
  - Real-time validation (latitude, longitude, timezone, cron)
  - Cache refresh functionality
  - Success/error feedback

## Migration Steps

### For Production Deployments

#### 1. Backup Database
```bash
mysqldump -u root -p minyanim > backup_pre_v1.3.0.sql
```

#### 2. Deploy v1.3.0
```bash
# Pull latest code
git pull origin main

# Build
./mvnw clean install -DskipTests

# Run
./mvnw spring-boot:run
```

#### 3. Verify Settings Initialization
On first startup, check logs for:
```
INFO: Initializing application settings with defaults
INFO: Created default setting: location.name = Teaneck, NJ
INFO: Created default setting: location.latitude = 40.906871
...
```

#### 4. Verify Application Functionality
- Navigate to homepage - verify minyan times display correctly
- Check organization pages - verify zmanim calculations
- Test calendar import - verify timezone handling

#### 5. (Optional) Customize Settings
- Log in as super admin
- Navigate to Admin > Application Settings
- Edit any settings as needed
- Click "Save Changes" and verify success message

### For Development Environments

#### 1. Update Database
No manual migration needed - Hibernate will auto-create the new table.

#### 2. Restart Application
```bash
./mvnw spring-boot:run
```

#### 3. Access Settings Page
Navigate to: `http://localhost:8080/admin/application-settings`

## Validation Rules

### Location Settings
- **Latitude**: Must be between -90 and 90
- **Longitude**: Must be between -180 and 180
- **Elevation**: Must be between 0 and 9000 meters
- **Location Name**: Any non-empty string

### Timezone Settings
- **Timezone**: Must be a valid Java TimeZone ID (e.g., "America/New_York", "UTC", "Europe/London")

### Calendar Import Settings
- **Cron Expression**: Must be a valid Spring cron expression (e.g., "0 0 2 * * SUN")
- **Cleanup Days**: Must be a positive integer

## Backward Compatibility

### ✅ Fully Backward Compatible
- All changes maintain 100% backward compatibility
- Default settings match previous hardcoded values
- No behavior changes for end users
- Existing TNMSettings (notifications) table unchanged

### Fallback Behavior
If ApplicationSettings table is missing or empty:
- System uses hardcoded defaults
- Application continues to function normally
- Logs warning messages about missing settings

## Performance Considerations

### Caching Strategy
- Settings loaded once at application startup
- Cached in memory for zero overhead during operation
- Cache refresh available via admin UI if needed
- No additional database queries during normal operation

### System Timezone
- Set globally once at application startup
- Affects all SimpleDateFormat and TimeZone.getDefault() calls
- MinyanEvent and KolhaMinyanim use system default timezone

## Troubleshooting

### Settings Not Loading
**Symptom**: Settings page shows empty or default values don't initialize

**Solution**:
```bash
# Check database connection
mysql -u root -p minyanim -e "SELECT * FROM APPLICATION_SETTINGS;"

# Force cache refresh via UI
Navigate to Admin > Application Settings > Refresh Settings Cache

# Or restart application
./mvnw spring-boot:run
```

### Validation Errors
**Symptom**: Cannot save setting due to validation error

**Solution**:
- Check validation hint in edit modal
- Verify value format matches expected type
- For timezone: Use valid Java timezone ID from [IANA Time Zone Database](https://www.iana.org/time-zones)
- For cron: Use Spring cron format (6 fields: second minute hour day month weekday)

### Timezone Not Applied
**Symptom**: Times display in wrong timezone after changing setting

**Solution**:
1. Verify setting saved successfully
2. Restart application (timezone set once at startup)
3. Check logs for timezone initialization

### Calendar Import Schedule Not Working
**Symptom**: Automatic imports not running at new cron time

**Solution**:
- Cron changes require application restart
- CalendarImportScheduler reads cron at class loading time
- Future enhancement: dynamic cron scheduling

## Files Changed

### New Files
- `src/main/java/com/tbdev/teaneckminyanim/enums/SettingKey.java`
- `src/main/java/com/tbdev/teaneckminyanim/enums/SettingType.java`
- `src/main/java/com/tbdev/teaneckminyanim/model/ApplicationSettings.java`
- `src/main/java/com/tbdev/teaneckminyanim/repo/ApplicationSettingsRepository.java`
- `src/main/java/com/tbdev/teaneckminyanim/service/ApplicationSettingsService.java`
- `src/main/java/com/tbdev/teaneckminyanim/controllers/ApplicationSettingsController.java`
- `src/main/resources/templates/admin/application-settings.html`
- `docs/settings/HARDCODED_VALUES_INVENTORY.md`

### Modified Files
- `src/main/java/com/tbdev/teaneckminyanim/TeaneckMinyanimApplication.java`
- `src/main/java/com/tbdev/teaneckminyanim/service/ZmanimService.java`
- `src/main/java/com/tbdev/teaneckminyanim/service/ZmanimHandler.java`
- `src/main/java/com/tbdev/teaneckminyanim/controllers/ZmanimController.java`
- `src/main/java/com/tbdev/teaneckminyanim/controllers/AdminController.java`
- `src/main/java/com/tbdev/teaneckminyanim/front/MinyanEvent.java`
- `src/main/java/com/tbdev/teaneckminyanim/front/KolhaMinyanim.java`
- `src/main/java/com/tbdev/teaneckminyanim/service/provider/CalendarImportProvider.java`
- `src/main/java/com/tbdev/teaneckminyanim/service/calendar/MinyanClassifier.java`
- `src/main/resources/templates/admin/sidebar.html`
- `pom.xml` (version updated to 1.3.0-SNAPSHOT)
- `CHANGELOG.md` (comprehensive v1.3.0 entry)

## Testing Checklist

### Pre-Deployment Testing
- [ ] Clean install builds successfully
- [ ] Application starts without errors
- [ ] Settings table created automatically
- [ ] Default settings initialized correctly
- [ ] Homepage displays minyan times
- [ ] Organization pages display zmanim
- [ ] Calendar import functions normally

### Post-Deployment Testing
- [ ] Access application settings page
- [ ] Edit location coordinates
- [ ] Edit timezone
- [ ] Verify validation works (try invalid values)
- [ ] Save settings successfully
- [ ] Restart application
- [ ] Verify changed settings applied
- [ ] Test calendar import with new cron
- [ ] Verify zmanim calculations with new coordinates

## Support & Documentation

### Additional Resources
- **Hardcoded Values Inventory**: `docs/settings/HARDCODED_VALUES_INVENTORY.md`
- **CHANGELOG**: See v1.3.0 entry for detailed change list
- **Code Documentation**: JavaDoc in ApplicationSettingsService
- **Admin Guide**: Settings page includes inline descriptions

### Known Limitations
1. **Cron Changes**: Require application restart (CalendarImportScheduler initialized at startup)
2. **Multi-Instance**: Settings cache not synchronized across instances (use load balancer sticky sessions)
3. **Scheduled Job Config**: Only cron expression configurable, not enabled/disabled toggle (future enhancement)

## Future Enhancements
- Dynamic cron scheduling (no restart required)
- Settings change event system for real-time updates
- Settings audit log (track all changes with timestamps)
- Settings export/import functionality
- Additional scheduled job configurations
- Per-organization timezone overrides
