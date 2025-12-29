# Changelog

All notable changes to the Teaneck Minyanim project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
