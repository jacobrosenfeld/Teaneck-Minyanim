# Changelog

All notable changes to the Teaneck Minyanim project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- Allow negative elevation values (down to -500m) in application settings validation to support below-sea-level locations.
- Replaced hardcoded "Teaneck Minyanim" labels in public and admin templates with the configurable `site.name` setting and neutral fallbacks to keep branding consistent with application settings.

## [1.3.0] - 2024-12-29

### Added

#### Application Settings System
- **Strongly-Typed Settings Architecture**: New `ApplicationSettings` entity with type-safe configuration management
- **SettingKey Enum**: Type-safe enum for all application setting keys with defaults, types, and validation rules
- **ApplicationSettingsService**: Centralized service for managing application-wide settings with:
  - Type-safe getters (getString, getInteger, getDouble, getBoolean)
  - Convenience methods for common settings (getGeoLocation, getTimeZone, etc.)
  - Comprehensive validation framework (latitude/longitude ranges, timezone validity, cron expressions)
  - Settings caching for performance
  - Automatic default value initialization on startup
  - Category-based grouping for UI organization
- **Application Settings UI**: Modern admin interface for managing app-wide configuration
  - Settings grouped by category (Location & Coordinates, Timezone, Calendar Import)
  - Edit modal with validation hints per setting type
  - Success/error feedback with auto-dismissal
  - Cache refresh functionality for manual database changes
  - Super admin only access
- **ApplicationSettingsController**: RESTful controller for settings management with validation
- **Hardcoded Values Inventory**: Comprehensive documentation of all moved configuration values

#### Configurable Application Settings
The following settings are now configurable via the Application Settings page:
1. **Location Name** (String): Display name for Zmanim calculations (default: "Teaneck, NJ")
2. **Latitude** (Double): Geographic latitude with validation (-90 to 90, default: 40.906871)
3. **Longitude** (Double): Geographic longitude with validation (-180 to 180, default: -74.020924)
4. **Elevation** (Double): Elevation in meters with validation (0 to 9000, default: 24)
5. **Timezone** (String): Application timezone with validation (default: "America/New_York")
6. **Calendar Import Cron** (String): Schedule for automatic calendar imports with cron validation (default: "0 0 2 * * SUN")
7. **Calendar Cleanup Days** (Integer): Threshold for old entry cleanup with positive integer validation (default: 30)

### Changed

#### Settings Refactoring
- **ZmanimService**: Now injects and uses ApplicationSettingsService for timezone and coordinates
- **ZmanimHandler**: Refactored to use ApplicationSettingsService with lazy GeoLocation initialization
  - Added no-arg constructor for backward compatibility with TimeRule and legacy code
  - Maintains fallback to default values when settings service unavailable
- **ZmanimController**: Removed hardcoded coordinates, now uses injected ZmanimHandler
- **AdminController**: Uses ApplicationSettingsService for timezone in date formatting
- **TeaneckMinyanimApplication**: Sets system default timezone from settings after ApplicationContext initialization
- **MinyanEvent & KolhaMinyanim**: Use system default timezone (set globally from settings)
- **CalendarImportProvider**: Uses ApplicationSettingsService for ZoneId in datetime conversions
- **MinyanClassifier**: Uses ApplicationSettingsService for timezone in Netz and Shkiya calculations
- **Admin Sidebar**: Split settings into "Application Settings" and "Notification Settings" for clarity

#### Database Schema
- New `APPLICATION_SETTINGS` table with columns:
  - `SETTING_KEY` (VARCHAR 100, primary key): Unique setting identifier
  - `SETTING_VALUE` (TEXT): Setting value as string
  - `SETTING_TYPE` (VARCHAR 50): Data type for validation
  - `DESCRIPTION` (TEXT): Human-readable description
  - `CATEGORY` (VARCHAR 100): UI grouping category
  - `VERSION` (LONG): Optimistic locking version

### Deprecated
- Hardcoded timezone values in service classes (replaced with settings service)
- Hardcoded geographic coordinates in multiple classes (replaced with centralized settings)
- Direct instantiation of GeoLocation objects (replaced with settings service method)

### Removed
- Hardcoded "America/New_York" timezone strings (7 occurrences removed)
- Hardcoded Teaneck, NJ coordinates (latitude, longitude, elevation) from 6 classes
- Hardcoded location name strings

### Fixed
- Timezone now consistently applied across all services via centralized settings
- GeoLocation calculations now use single source of truth for coordinates
- Settings cache ensures performance while maintaining configurability
- Validation prevents invalid coordinate, timezone, and cron expression values

### Security
- Application Settings page restricted to super admin users only
- Validation prevents injection of invalid timezone or coordinate values
- Settings changes logged with username for audit trail

### Migration Notes

#### For Existing Deployments
1. On first startup, ApplicationSettings will auto-initialize with default values matching previous hardcoded values
2. No data migration required - new settings table created automatically via Hibernate
3. Existing TNMSettings table remains unchanged (used for notifications)
4. To customize settings, navigate to Admin > Application Settings after upgrade
5. Settings are cached in memory - use "Refresh Cache" button if manually updating database

#### Breaking Changes
- None - all changes are backward compatible with fallback defaults

#### Performance Impact
- Settings are cached on startup for optimal performance
- No additional database queries during normal operation
- Timezone set globally once at application startup

## [1.2.7] - 2024-12-29

### Added

#### Nusach Sefard (NS) Classification Enhancement
- Intelligent detection of "NS" abbreviation in event titles
- Automatic addition of "Nusach Sefard" note to all events containing NS
- Special handling: NS events before 12pm are forced to Shacharis classification with "Nusach Sefard" notes
- NS abbreviation is then processed through all remaining classification rules, allowing hybrid classifications (e.g., "NS Mincha" → Mincha with Nusach Sefard)
- Example: "NS" at 11:00 AM → Shacharis with notes "Nusach Sefard"
- Example: "NS Mincha" at 2:00 PM → Mincha with notes "Nusach Sefard"

#### Netz Hachama (Sunrise Minyan) Support
- New automatic detection of sunrise minyan terms: Hanetz, Netz, Neitz, Vasikin, Sunrise, Hanetz Hachama
- All Netz patterns automatically classified as Shacharis
- Automatic calculation and display of Netz Hachama time (sunrise time for that specific day)
- Format: "Netz Hachama: 6:45 AM" appended to notes
- Integrates with existing ZmanimHandler for accurate Jewish calendar-based calculations
- Combines with NS and title qualifiers (e.g., "Early Netz" → Shacharis with "Netz Hachama: 6:45 AM. Early")
- Example: "Netz Minyan" → Shacharis with notes "Netz Hachama: 6:45 AM"

### Changed
- MinyanClassifier now processes NS and Netz patterns early in classification pipeline for high priority
- NS detection continues through all remaining classification rules instead of early return for hybrid event support
- Enhanced note generation to combine Shkiya (Mincha/Maariv), Netz Hachama (sunrise), NS, and title qualifiers

## [1.2.6] - 2024-12-29

### Fixed
- Fixed website field not persisting in organization admin panel. The issue was caused by the builder setting the transient `websiteURI` field instead of the persistent `websiteURIStr` field in both create and update operations.
- Other minor cosmetic cleanups. 

## [1.2.5] - 2024-12-29

### Added

#### Homepage Notification System
- New popup notification feature for homepage announcements with smart display controls
- Cookie-based tracking system to limit notification displays per user
- Expiration date support for time-limited announcements
- Max displays configuration to control how many times a user sees a notification
- Modern Bootstrap modal UI for notifications with icon and styled buttons
- Automatic notification dismissal after expiration date
- Admin settings panel enhancements with new fields for notification management
  - Expiration Date field (optional, date picker)
  - Max Displays Per User field (optional, 1-100 range)
- JavaScript NotificationManager utility with cookie management
- Settings table now displays expiration date and max displays columns
- Persistent tracking using browser cookies (stored for 1 year)

### Changed
- Enhanced TNMSettings entity with `expirationDate` (String) and `maxDisplays` (Integer) fields
- Updated AdminController.updateSettings() to handle new notification configuration fields
- Improved settings modal form with better organization and helper text
- Updated homepage to load notification-popup.js for popup management

## [1.2.4] - 2024-12-29

### Changed
- Homepage now uses calendar/CSV scraped data when available for organizations
- Updated ZmanimService.getZmanim() to check each organization for calendar import status
- Homepage prioritizes calendar data over rule-based data, maintaining consistency with org pages

## [1.2.3] - 2024-12-29

### Added
- Homepage integration with calendar/CSV scraping functionality
- Automatic detection of calendar-enabled organizations on homepage
- Unified data sourcing across homepage, org pages, and admin panel

### Changed
- Homepage now processes all organizations individually to determine data source
- Calendar-imported events displayed alongside rule-based events on homepage
- KolhaMinyanim section updated to include calendar-imported events

## [1.2.2] - 2024-12-26

### Added

#### Calendar Import Classification System
- Intelligent classification of imported calendar entries into MINYAN, MINCHA_MAARIV, NON_MINYAN, and OTHER categories
- Pattern-based allowlist for minyan-related events (Shacharis, Mincha, Maariv variants, Selichos, Neitz)
- Pattern-based denylist for non-minyan events (Daf Yomi, Shiur, Lecture, Candle Lighting, Kiddush, etc.)
- Explainable classification with stored classification reasons
- Conservative default: unmatched events classified as NON_MINYAN and disabled

#### Title Qualifier Extraction
- Automatic extraction of meaningful qualifiers from event titles (Teen, Early, Late, Fast, Women's, Men's, etc.)
- Extracted qualifiers added to notes field for display
- Examples: "Teen Minyan" → notes: "Teen", "Early Shacharis" → notes: "Early"
- Supports 15+ common qualifier patterns

#### Shkiya (Sunset) Integration
- Automatic Shkiya time calculation for Mincha/Maariv combined entries
- Uses existing ZmanimHandler for accurate sunset computation
- Format: "Shkiya: 4:38 PM" appended to notes
- Graceful failure handling if computation fails

#### Inline Location Editing
- Click-to-edit functionality for location field in admin calendar entries table
- Dropdown populated from organization's existing locations
- Manual edit tracking with visual indicators (blue dot)
- Tracks who edited and when (manually_edited_by, manually_edited_at fields)
- Regular imports preserve manual edits; explicit "Reimport All" button to override

#### Modern Admin UI Enhancements
- Sortable columns (date, time, title, type, enabled, importedAt)
- Comprehensive filtering (date range, text search, event type, enabled status)
- "Show Non-Minyan" toggle to view excluded events
- Statistics dashboard showing entry counts by classification
- Color-coded badges for classifications (Green: MINYAN, Cyan: MINCHA_MAARIV, Gray: NON_MINYAN)
- Color-coded prayer type pills based on title text (Blue: Shacharis, Amber: Mincha, Purple: Maariv, Cyan: Mincha/Maariv)
- Sticky table headers for better scrolling
- Modern card-based layout
- Empty states with helpful messages
- Filter persistence in URL parameters

#### Database Enhancements
- New fields: classification, classification_reason, notes
- Manual edit tracking: location_manually_edited, manually_edited_by, manually_edited_at
- Performance indexes: idx_org_classification, idx_org_enabled_classification
- New repository query methods for filtering and sorting

### Changed

#### Classification Behavior
- NON_MINYAN events now automatically disabled by default during import
- Unmatched events default to NON_MINYAN (was OTHER) - conservative approach
- Denylist patterns take precedence over allowlist when both match
- Classification enforced during both create and update operations

#### Frontend Display
- "Next Minyan" button now always uses today's date (LocalDate.now()) regardless of viewed date
- Calendar import events properly shown with MINCHA_MAARIV type on org pages
- Notes field prioritized over description field for display
- Description field no longer automatically appended to notes

#### Admin Features
- Default sort changed to date ascending (earliest to latest) for upcoming events first
- Location field now editable inline with save/cancel buttons
- Visual indicator for manually edited fields

### Fixed
- ZmanimHandler registered as Spring @Service bean for proper dependency injection
- Upcoming minyan button now includes calendar-imported events (was skipped before)
- Mincha/Maariv combined events display correctly as "Mincha/Maariv" (was showing as "Mincha")
- "Show Non-Minyan" checkbox layout no longer overlaps text
- Location edit buttons properly sized with better padding

### Technical

#### New Files
- `MinyanClassification.java` - Enum for event classifications
- `MinyanClassifier.java` - Classification service with pattern matching and title qualifier extraction
- `MinyanClassifierTest.java` - Comprehensive test suite (43 tests)
- `MIGRATION_v1.2.2.sql` - Database migration script
- `FEATURE_SUMMARY_v1.2.2.md` - Detailed feature documentation
- `CHANGELOG.md` - This file

#### Modified Files
- `OrganizationCalendarEntry.java` - Added classification, notes, and manual edit tracking fields
- `OrganizationCalendarEntryRepository.java` - New query methods for filtering/sorting
- `CalendarImportService.java` - Auto-disable NON_MINYAN, respect manual edits, title qualifier extraction
- `CalendarImportProvider.java` - Fixed notes display priority
- `ZmanimService.java` - Today-based next minyan logic
- `ZmanimHandler.java` - Added @Service annotation
- `AdminController.java` - Filtering, sorting, inline editing endpoints, default sort
- `calendar-entries.html` - Modern UI with pills, inline editing, statistics

#### Testing
- 43 MinyanClassifier tests added covering:
  - All pattern matching scenarios
  - Shkiya note generation
  - Title qualifier extraction
  - Priority ordering (denylist before allowlist)
  - Case-insensitive matching
  - Edge cases (null, empty, combined fields)
- 65+ total tests passing

### Deployment Notes
- Database migration required (run MIGRATION_v1.2.2.sql or use Hibernate auto-update)
- Existing entries will be reclassified on next import
- No breaking changes to public APIs
- Fully backward compatible

## [1.2.1] - Previous Release

See [PR_SUMMARY_v1.2.1.md](PR_SUMMARY_v1.2.1.md) and [ARCHITECTURE_v1.2.1.md](ARCHITECTURE_v1.2.1.md) for details.

---

## Release Guidelines

### Version Numbering
- **Major (X.0.0)**: Breaking changes, major features
- **Minor (1.X.0)**: New features, backward compatible
- **Patch (1.2.X)**: Bug fixes, minor enhancements

### Categories
- **Added**: New features
- **Changed**: Changes in existing functionality
- **Deprecated**: Soon-to-be removed features
- **Removed**: Removed features
- **Fixed**: Bug fixes
- **Security**: Security improvements
