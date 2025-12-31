# Changelog

All notable changes to the Teaneck Minyanim project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.7.0] - 2025-12-31

### Added

#### AG Grid Community Integration for Calendar Events
- **Replaced HTML table with AG Grid Community** for Calendar Events page (`/admin/{orgId}/calendar-events`)
  - Integrated AG Grid Community v31.3.2 (open source, MIT license)
  - Applied Alpine theme for clean, modern appearance
  - Optimized for handling 10,000+ rows with virtual scrolling and efficient rendering
  - Server-side pagination with configurable page sizes (25, 50, 100, 200 rows)
  - Advanced column filtering with date, text, and set filters
  - Sortable columns with multi-column sort support
  - Inline editing for notes and location fields with auto-submit
  - Custom cell renderers for badges, dates, times, and action buttons
  - Responsive column resizing and auto-sizing
  - Row animation for smooth user experience
  - Disabled row styling preserved from previous implementation

#### Modernized Template Structure
- **Migrated calendar-events.html to Thymeleaf layout decorator pattern**
  - Now uses `layout:decorate="~{admin/layout}"` for consistent admin UI
  - Inherited navbar, sidebar, and design system from shared admin layout
  - Preserved all existing features: statistics cards, collapsible filter panel, info box
  - Maintained color-coded badges for event types, sources, and status
  - Consistent with other admin pages (organizations.html, etc.)

#### Performance Improvements
- **Optimized for large datasets**
  - Virtual scrolling eliminates DOM bloat for 10,000+ row datasets
  - Client-side filtering and sorting with efficient indexing
  - Reduced initial page load time by lazy rendering visible rows only
  - Improved memory usage compared to full table rendering

### Changed

#### Admin UI Consistency
- **Calendar Events page now follows admin design system**
  - Uses CSS variables from design-system.css for theming
  - Modern card-based layout with proper spacing and shadows
  - Alert components replaced with alert-modern classes
  - Button styling updated to btn-modern classes
  - Page header structure matches other admin pages

#### JavaScript Architecture
- **Replaced Tabulator with AG Grid**
  - New file: `calendar-events-aggrid.js` (replacing `calendar-events-tabulator.js`)
  - Simplified data binding using Thymeleaf inline JavaScript
  - Form-based CRUD operations maintained for compatibility
  - Toggle, delete, and update actions preserved with same backend endpoints

### Removed

#### Tabulator Dependencies
- **Removed Tabulator library references**
  - Backed up `calendar-events-tabulator.js` to `.bak` file (excluded from git)
  - Backed up old template to `calendar-events-old.html` (excluded from git)
  - Removed Tabulator CSS/JS CDN references from templates
  - Updated .gitignore to exclude backup files (*.bak, *-old.html)

### Technical Details

#### AG Grid Configuration
- Column definitions for: Date, Time, Type, Source, Location, Notes, Status, Actions
- Default column settings: resizable, sortable, filterable
- Pagination: 50 rows per page default
- Grid height: 700px fixed height with internal scrolling
- Cell editing: Inline editing for notes (text input) and location (dropdown select)
- Custom renderers: HTML badges, formatted dates/times, action buttons
- Row styling: Disabled events shown with reduced opacity and gray background

#### Security & Authorization
- All CRUD operations maintain existing authorization checks
- Form submissions to existing controller endpoints
- No changes to backend security model
- Org admin and super admin permissions preserved

#### Browser Compatibility
- AG Grid Community supports modern browsers (Chrome, Firefox, Safari, Edge)
- CDN delivery with version pinning for stability
- Fallback graceful degradation not implemented (requires modern browser)

## [1.6.0] - 2025-12-31

### Added

#### Super Admin Top Navbar with Organization Dropdown
- **Redesigned Navigation for Super Admins**:
  - Moved super admin navigation from sidebar to top navbar for better accessibility
  - Added "Organizations" dropdown menu with searchable list of all organizations
  - Integrated "New Organization" action directly into dropdown menu (removed separate sidebar link)
  - Added direct access to Settings, Accounts, and Notifications from top navbar
  - Dropdown features real-time search filtering of organizations
  - Smooth animations and visual feedback for dropdown interactions
  - Mobile-responsive design with fixed positioning on small screens

#### Organization Context Sidebar
- **Context-Aware Sidebar Navigation**:
  - New org-specific sidebar appears when Super Admin or Org Manager is in organization context
  - Shows current organization name as sidebar section title
  - Consolidated org-level tools: Dashboard, Minyan Schedule, Locations, Calendar Entries, Profile & Accounts
  - Active link highlighting based on current page URL
  - Organization Actions section (Disable/Delete) for Super Admins only
  - Visual separation of org actions from regular navigation
  - Confirmation dialog for destructive actions (delete organization)

#### Standardized Organization Routes
- **Consistent URL Pattern (`/admin/org/{orgId}/...`)**:
  - `/admin/org/{orgId}/dashboard` - Organization dashboard (currently redirects to minyanim)
  - `/admin/org/{orgId}/minyanim` - Minyan schedule management
  - `/admin/org/{orgId}/locations` - Location management
  - `/admin/org/{orgId}/calendar-entries` - Calendar entries management
  - All routes properly verify user permissions (Super Admin or org ownership)
  - Routes delegate to existing controller methods for consistency

### Changed

#### Navigation Architecture
- **Role-Based Navigation Display**:
  - Super Admins now see top navbar with global controls instead of sidebar-first navigation
  - Org Managers/Admins see only org-specific sidebar (no access to global controls)
  - Navigation automatically adapts based on user role and current context
  - Sidebar visibility logic updated to show appropriate sections based on organization context
  - Non-super-admin users with no org context see their organization's tools directly

#### Controller Updates
- **AdminController Enhancements**:
  - `addStandardPageData()` now provides `allOrganizations` list for super admin dropdown
  - Added wrapper methods for org-context routes that delegate to existing implementations
  - Maintained backward compatibility with existing `/admin/{orgId}/...` routes

### Improved

#### User Experience
- **Streamlined Organization Management**:
  - Faster organization switching for Super Admins via top navbar dropdown
  - Clearer visual hierarchy: global controls (navbar) vs org-specific tools (sidebar)
  - Reduced navigation fragmentation with consolidated org-level menu
  - More intuitive workflow: select org → see org tools → manage org resources
  - Better mobile responsiveness with collapsible sidebar and fixed navbar

#### Design Consistency
- **Laravel Backpack-Inspired UX**:
  - Professional admin panel design patterns
  - Consistent spacing and visual design using existing design system tokens
  - Smooth transitions and hover effects throughout navigation
  - Clear visual distinction between global and org-specific contexts

## [1.5.0] - 2025-12-30

### Added

#### Modern Admin Panel Design System
- **Comprehensive CSS Design System** (`design-system.css`):
  - Complete design token system with CSS custom properties for colors, typography, spacing
  - 60+ design tokens for consistent theming across the admin panel
  - Modern component library: buttons (4 variants × 3 sizes), cards, forms, badges, alerts, tables
  - Utility classes for flexbox, spacing, typography, and display patterns
  - Professional blue-based color palette with primary, secondary, accent, and neutral colors
  - Responsive design with mobile-first approach and breakpoints at 768px and 480px

#### Modern Navigation Components
- **Redesigned Admin Navbar**:
  - Clean, fixed header with primary brand color and modern styling
  - Logo and site name branding with improved typography
  - Responsive hamburger menu for mobile devices
  - Current time display in monospace font
  - Organization badge with pill-style design
  - Smooth animations and hover effects

- **Redesigned Admin Sidebar**:
  - Fixed sidebar with organized sections (Organization, Administration, Account)
  - Icon-based navigation using SVG icons for all menu items
  - Active link highlighting with left border accent
  - Smooth hover transitions and visual feedback
  - Collapsible on mobile devices with toggle animation
  - Semantic organization of menu structure

#### Shared Layout System
- **Reusable Thymeleaf Layout Template** (`layout.html`):
  - Consistent header/sidebar/content structure across all admin pages
  - Page title and description blocks for consistent page headers
  - Global toast notification system for success/error messages
  - Responsive sidebar toggle functionality with smooth animations
  - Proper spacing and margins using design system tokens
  - Mobile-responsive design with automatic sidebar collapse

#### Enhanced Calendar Events Page
- **Modern Redesign with Design System Integration**:
  - Statistics grid with hover-animated stat cards
  - Collapsible filter panel with toggle functionality
  - Modern table design with cleaner borders, better spacing, sticky header
  - Enhanced badges using design system colors with proper contrast
  - Beautiful empty state with icon and helpful messaging
  - Info box redesigned with modern card styling

- **User Experience Improvements**:
  - Collapsible filters: Click header to toggle filter visibility with smooth animation
  - Toast notifications for success/error messages instead of page alerts
  - Modern form controls using design system styles with focus states
  - Improved button styling with hover effects and transitions
  - Better inline editing controls with focus indicators
  - Responsive grid layouts with proper breakpoints

### Changed

#### Navigation Structure
- Reorganized sidebar menu into logical sections with separators
- Updated menu item ordering for better workflow
- Added icons to all navigation items for better visual recognition
- Improved mobile navigation experience with collapsible sidebar

#### Visual Design
- Updated color scheme to use design system tokens throughout
- Improved typography with consistent font sizes, weights, and line heights
- Enhanced spacing using systematic 8px-based spacing scale
- Modernized button designs with multiple variants and sizes
- Updated form controls with better focus states and validation feedback

### Technical Improvements

#### CSS Architecture
- **Design Token System**: All colors, spacing, typography defined as CSS custom properties
- **Component-Based Styling**: Clear, BEM-inspired class names for components
- **Utility-First Approach**: Common patterns available as utility classes
- **Responsive Design**: Mobile-first with breakpoints at 768px and 480px
- **Performance**: No !important declarations, clean CSS cascade

#### JavaScript Features
- Sidebar toggle with smooth animations
- Filter panel collapse/expand functionality
- Toast notification system for better user feedback
- Form submission handling with loading states
- Event delegation for dynamic content

#### Browser Support
- Modern evergreen browsers (Chrome 90+, Firefox 88+, Safari 14+, Edge 90+)
- CSS custom properties (no IE11 support required)
- Flexbox and Grid layouts for modern layout capabilities
- ES6 JavaScript features for cleaner code

### Documentation
- Added comprehensive inline documentation in design-system.css
- Documented all design tokens with usage examples
- Created detailed PR descriptions with implementation notes
- Maintained backward compatibility with existing functionality

## [1.4.0] - 2025-12-30

### Added

#### Admin UI for Calendar Events Management
- **CalendarEventsAdminController**: New admin controller for managing materialized calendar_events
  - Route: `/admin/{orgId}/calendar-events`
  - View all calendar events for an organization with filters
  - Filter by date range (defaults to materialization window: 3 weeks past, 8 weeks future)
  - Filter by minyan type (Shacharis, Mincha, Maariv, Mincha/Maariv, Selichos, etc.)
  - Filter by event source (IMPORTED, RULES, MANUAL)
  - Filter by enabled/disabled status
  - Sort by date and time
  
- **Event Management Operations**:
  - **Toggle Enable/Disable**: Any event can be enabled or disabled via toggle button
  - **Inline Note Editing**: Edit notes field with auto-submit on change
  - **Inline Location Editing**: Select location from dropdown with auto-submit
  - **Delete Manual Events**: Only manual events can be deleted; imported/rules events can only be disabled
  - **Manual Edit Tracking**: Events show orange dot indicator when manually edited
  - **Rematerialization Trigger**: Button to manually trigger calendar rematerialization for organization

- **Statistics Dashboard**:
  - Total events count in current date range
  - Enabled vs disabled events
  - Rules-based events count
  - Imported events count
  - Manual events count (future feature)

- **Modern UI Features**:
  - Color-coded source badges: Blue (RULES), Green (IMPORTED), Orange (MANUAL)
  - Color-coded type badges: Blue (Shacharis), Amber (Mincha), Purple (Maariv), Pink (Mincha/Maariv)
  - Status badges: Green (Enabled), Gray (Disabled)
  - Responsive table with sticky header
  - Modern filter panel with date pickers and dropdowns
  - Success/error flash messages
  - Info box explaining event types and materialization window
  - Manual edit indicator (orange dot with tooltip)

- **Navigation**:
  - Added "Calendar Events" link to admin sidebar
  - Accessible to all admin users (organization-specific access)
  - Link appears between "Minyan Schedule" and other admin options

- **Security & Authorization**:
  - Enhanced TNMUserService with authorization helpers:
    - `getCurrentUser()`: Get currently authenticated user
    - `isSuperAdmin()`: Check if user is super admin
    - `canAccessOrganization(orgId)`: Check if user can access specific organization
  - Super admins can access all organizations
  - Regular admins can only access their own organization
  - All endpoints protected with proper access control
  - Graceful error handling for unauthorized access

#### Materialized Calendar Architecture (Major Refactor)
- **CalendarEvent Entity**: New unified database table (`calendar_events`) serving as single source of truth for all minyanim
  - Supports three event sources: IMPORTED (from calendars), RULES (from schedules), MANUAL (future overrides)
  - Includes comprehensive fields: organization_id, date, minyan_type, start_time, notes, location_id, location_name, enabled, source, source_ref
  - Automatic timestamps (created_at, updated_at) via JPA lifecycle hooks
  - Support for nusach, whatsapp links, dynamic_time_string for rule-based events
  - Manual edit tracking: manually_edited flag, edited_by, edited_at fields
  - Database indexes for performance: (organization_id, date), (organization_id, date, minyan_type, start_time), (source, date), (enabled)

- **EventSource Enum**: Type-safe source tracking with three values
  - `IMPORTED`: Events from external calendar imports
  - `RULES`: Events generated from rule-based minyan schedules
  - `MANUAL`: Manual overrides by admins (schema support added, UI now implemented)
  - Helper methods: `isImported()`, `isRules()`, `isManual()`, `displayName()`

- **CalendarEventRepository**: Comprehensive data access layer with 20+ query methods
  - Basic CRUD operations with Spring Data JPA
  - Date-based queries: `findByOrganizationIdAndDate()`, `findEventsInRange()`
  - Effective schedule queries with built-in precedence logic
  - Existence checks for precedence: `existsByOrganizationIdAndDateAndSourceAndEnabledTrue()`
  - Bulk operations: `deleteRulesEventsInRange()`, `deleteEventsBeforeDate()`
  - Filtering: by source, minyan_type, enabled status
  - Sorting support via Spring Data Sort parameter

#### Materialization Services
- **CalendarMaterializationService**: Core service for generating calendar_events
  - **Rule-Based Event Generation**:
    - Iterates through all enabled Minyan entities
    - For each day in rolling window, checks minyan's schedule
    - Handles Jewish calendar dates (Rosh Chodesh, Yom Tov, Chanuka, regular weekdays)
    - Converts MinyanTime to LocalTime, preserves dynamic time strings
    - Links to Location entities, captures nusach and WhatsApp info
  - **Imported Event Materialization**:
    - Reads OrganizationCalendarEntry records
    - Only materializes entries with classification != NON_MINYAN
    - Deduplicates using source_ref tracking
    - Respects enabled status from import
    - Defaults to organization nusach when not specified
  - **Delete + Rebuild Strategy**:
    - Deletes only RULES events in rolling window (preserves IMPORTED/MANUAL)
    - Transaction-safe operations
    - Prevents data loss for user-managed content
  - **Rolling Window Configuration**:
    - Past 3 weeks (configurable via PAST_WEEKS constant)
    - Next 8 weeks (configurable via FUTURE_WEEKS constant)
    - Total 11-week window centered on current date
  - **Cleanup Operations**: Automatic removal of events older than window start
  - **Manual Triggers**: Public methods for admin-initiated materialization
  - **Window Validation**: `isDateInWindow()` and `getWindowBounds()` helpers

- **CalendarMaterializationScheduler**: Scheduled job orchestration
  - **Application Startup**: Runs full materialization on ApplicationReadyEvent
  - **Weekly Schedule**: Cron job every Sunday at 2:00 AM (`0 0 2 * * SUN`)
  - **Manual Triggers**: Methods for admin controller integration
  - **Error Handling**: Comprehensive logging, exceptions don't crash scheduler
  - Uses `@EnableScheduling` already present in application

- **EffectiveScheduleService**: Query service with day-level precedence logic
  - **Day-Level Override**: If ANY imported events exist for org+date, returns ONLY imported events; else returns ONLY rules events
  - **Effective Events**: `getEffectiveEventsForDate()` applies precedence, returns enabled events only
  - **Range Queries**: `getEffectiveEventsInRange()` with per-day precedence application
  - **Admin Views**: `getAllEventsForDate()` and `getAllEventsInRange()` bypass precedence, show everything
  - **Window Validation**: Delegates to CalendarMaterializationService for date checks
  - **Stream-Based Processing**: Efficient grouping and filtering using Java Streams

- **CalendarEventAdapter**: Conversion layer for frontend compatibility
  - Converts `CalendarEvent` entities to `MinyanEvent` display objects
  - Preserves all display properties: organization details, location, time formatting
  - Handles time zone conversion using ApplicationSettingsService
  - Supports dynamic time strings for rule-based events
  - Graceful handling of missing organizations or data
  - Batch conversion: `toMinyanEvents()` for list processing

#### Developer Experience
- **Comprehensive Documentation**: Added `docs/ZMANIM_SERVICE_REFACTORING.md` guide
  - Current state analysis of dual code paths
  - Target architecture with unified materialized calendar
  - Step-by-step refactoring instructions
  - Code snippets for all major changes
  - Testing checklist for validation
  - Rollback plan for safety

### Changed
- **Application Version**: Updated from 1.3.4 to 1.4.0 in pom.xml
- **Database Schema**: New `calendar_events` table created via JPA auto-DDL
- **Data Flow**: Backend now materializes events; frontend will read from materialized data (in progress)
- **Admin Sidebar**: Added "Calendar Events" navigation link for organization admins

### Technical Details

#### Precedence Implementation
The day-level precedence is implemented as follows:
1. Check if ANY enabled IMPORTED events exist for org+date
2. If yes, filter all events for that day to source=IMPORTED only
3. If no, filter all events for that day to source=RULES only
4. MANUAL source (future) will override both IMPORTED and RULES

This ensures a clean separation between different event sources and prevents confusion from mixed displays.

#### Performance Considerations
- Materialization runs weekly, not on-demand (reduces database load)
- Comprehensive indexes on calendar_events table
- Rolling window limits data volume
- Batch processing for rule generation
- Stream-based filtering in EffectiveScheduleService

#### Future Extensibility
Schema designed to support:
- Manual day overrides (MANUAL source)
- Per-event manual edits (manually_edited flag)
- Audit trails (edited_by, edited_at, updated_at)
- Easy addition of new event sources
- Time-based querying without full table scans

### Migration Notes
- **Database**: New `calendar_events` table created automatically on application startup
- **Initial Data**: Full materialization runs on first startup, populates 11-week window
- **Existing Features**: Rule-based and imported minyanim continue to work during transition
- **No Data Loss**: Import entries and minyan rules preserved, only materialized differently
- **Backward Compatibility**: MinyanEvent display objects unchanged, frontend impact minimized

### Known Limitations
- ZmanimService refactoring incomplete (documented in `docs/ZMANIM_SERVICE_REFACTORING.md`)
- Frontend still uses dual code paths (will be unified in follow-up)
- Admin UI for calendar management not yet built
- Manual override feature (MANUAL source) not implemented
- No UI for viewing materialization status/logs

### Dependencies
- No new external dependencies added
- Uses existing: Spring Data JPA, Lombok, Kosherjava Zmanim
- Compatible with Spring Boot 3.5.9 and Jakarta EE

## [1.3.3] - 2025-12-30

### Changed

#### Spring Boot 3.5.9 Migration
- **Major Framework Update**: Upgraded from Spring Boot 2.7.18 to 3.5.9
  - Updated parent POM dependency to spring-boot-starter-parent 3.5.9
  - Removed explicit version override for spring-boot-starter-web (now inherited from parent)
  - Updated thymeleaf-extras-springsecurity5 to thymeleaf-extras-springsecurity6 for Spring Security 6.x compatibility
  - Updated lombok to 1.18.30 for improved Java 17 support

#### Jakarta EE Namespace Migration
- **Complete javax → jakarta Migration**: Updated all Java EE imports from javax.* to jakarta.* namespace
  - Migrated javax.persistence.* to jakarta.persistence.* (16 files affected)
  - Migrated javax.servlet.* to jakarta.servlet.* (2 files affected)
  - Migrated javax.annotation.* to jakarta.annotation.* (1 file affected)
  - This includes all JPA entities, services, and servlet components

#### Spring Security 6.0 Modernization
- **Deprecated API Removal**: Replaced deprecated WebSecurityConfigurerAdapter pattern with SecurityFilterChain
  - Refactored WebSecurityConfiguration to use @Bean SecurityFilterChain approach
  - Updated security DSL from deprecated methods (antMatchers, authorizeRequests) to modern equivalents (requestMatchers, authorizeHttpRequests)
  - Migrated from method-chaining style to lambda DSL for improved readability
  - Added explicit BCryptPasswordEncoder and DaoAuthenticationProvider beans
  - Added AuthenticationManager bean configuration

### Fixed
- **Hibernate 6.x Compatibility**: Fixed JPQL queries in MinyanRepository to use boolean literals instead of integer values
  - Changed `WHERE m.enabled = 1` to `WHERE m.enabled = true` for Hibernate 6.x compatibility
  - Fixes query validation errors when starting the application
  - Affects `findByEnabled()` and `findByOrganizationIdAndEnabled()` methods
- **Spring Security 6.0 PathPattern Compatibility**: Fixed URL patterns to comply with PathPattern syntax
  - Changed `/**/*.css` and `/**/*.js` to `**.css` and `**.js` (double asterisk patterns must be at start/end)
  - Changed `/admin/**/locations` to `/admin/*/locations` (single asterisk for single path segment)
  - Changed `/admin/**/minyanim/**` to `/admin/*/minyanim/**`
  - Added explicit patterns for `/org/**`, `/assets/**`, and `/favicon.ico`
  - Added patterns for calendar entries: `/admin/*/calendar-entries/**`
  - Fixes PatternParseException preventing access to org pages, admin pages, and static resources
- **CSRF Protection Configuration**: Restored explicit CSRF disable to match original behavior
  - Changed `.csrf(csrf -> { })` to `.csrf(csrf -> csrf.disable())` to properly disable CSRF protection
  - Maintains backward compatibility with original Spring Security 5.x configuration
  - Prevents breaking changes for existing forms and API endpoints that don't use CSRF tokens

### Technical Details

#### Breaking Changes
- Spring Boot 3.x requires Java 17 or later (already compatible)
- Jakarta EE namespace is mandatory for all javax.* imports
- Spring Security 6.x no longer supports WebSecurityConfigurerAdapter
- Spring Security 6.x uses PathPattern instead of AntPathMatcher by default, with stricter pattern syntax rules
- Hibernate 6.x is now the underlying JPA provider
- Hibernate 6.x requires boolean literals (true/false) instead of numeric values (0/1) in JPQL queries

#### Compatibility
- Fully backward compatible at the application level
- No database schema changes required
- No API changes for end users
- All existing features remain functional

#### Build Status
- Successful compilation with javac for Java 17
- All 63 source files compiled successfully
- All 66 tests passing (100% success rate)
- Minor warnings about @Builder defaults (pre-existing, non-blocking)

### Migration Notes

#### For Developers
1. All javax.* imports have been replaced with jakarta.*
2. Custom security configurations must use SecurityFilterChain pattern
3. Update IDE auto-imports to prefer jakarta.* over javax.*
4. Spring Security DSL now uses lambda expressions consistently
5. JPQL queries must use boolean literals (true/false) instead of numeric values (0/1) for boolean fields
6. URL patterns must comply with PathPattern syntax:
   - Double asterisk `**` can only be at the start or end of a pattern
   - Use single asterisk `*` to match a single path segment
   - Patterns like `/**/*.css` should be `**.css` or `/static/**/*.css`

#### For Deployment
1. No data migration required - schema remains unchanged
2. Application binary size may increase slightly due to newer dependencies
3. Runtime performance improvements expected from Spring Boot 3.x optimizations
4. Container/JVM must support Java 17+

### Security
- Updated to latest Spring Security 6.x with improved security defaults
- Removed deprecated security configuration methods
- Modern security DSL provides better type safety

### Removed
- Dependency on javax.* namespace (replaced with jakarta.*)
- Use of deprecated WebSecurityConfigurerAdapter
- Explicit version override for spring-boot-starter-web

---

## [1.3.2] - Previous Unreleased Changes

## [Unreleased]

### Changed
- Allow negative elevation values (down to -500m) in application settings validation to support below-sea-level locations.
- Timezone selector now uses regex-friendly fuzzy search (supports mid-string queries and common aliases like “Jerusalem” → “Israel”).
- Replaced hardcoded "Teaneck Minyanim" labels in public and admin templates with the configurable `site.name` setting and neutral fallbacks to keep branding consistent with application settings.
- Application Settings modal now includes a hex color picker for `site.app.color` to reduce invalid theme color entries.

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
