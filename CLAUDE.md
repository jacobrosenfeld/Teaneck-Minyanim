# Teaneck Minyanim - AI Coding Agent Instructions

## Architecture Overview
This is a Spring Boot application that displays Jewish prayer services (minyanim) and religious times (zmanim) for Teaneck, NJ. The system automatically calculates service times based on the Jewish calendar and location coordinates, and supports importing events from external calendars with intelligent classification.

**Key Data Flow (v1.4.0 - Materialized Calendar Architecture):**
1. `CalendarMaterializationService` generates calendar_events table from rule-based Minyan entities and imported OrganizationCalendarEntry records
2. **Materialized Calendar Events**: `CalendarEvent` entities are the single source of truth, pre-computed for rolling 11-week window (3 past, 8 future)
3. **Day-Level Precedence**: Imported events suppress all rules for that org+date (applied by `EffectiveScheduleService`)
4. **Frontend Queries**: `ZmanimService` queries materialized calendar_events via `EffectiveScheduleService`, converts to `MinyanEvent` via `CalendarEventAdapter`
5. **Weekly Materialization**: Scheduled job runs Sunday 2 AM, deletes+rebuilds RULES events (preserves IMPORTED/MANUAL)
6. **Admin UI**: Complete calendar management interface for org admins and super admins with Backpack for Laravel-inspired design

**Legacy Data Flow (pre-v1.4.0 - DEPRECATED):**
1. `ZmanimService` calculated times using Kosherjava library
2. Dual code paths: rule-based Minyan computation OR calendar import provider
3. `Schedule` class wrapped rule-based schedules into `MinyanTime` objects
4. `Organization` owns multiple `Minyan` instances and calendar entries; each `Minyan` links to a `Location`

## Critical Components & Patterns

### Materialized Calendar System (v1.4.0)
- **CalendarEvent** entity: Single source of truth for all minyanim, stored in `calendar_events` table with fields: id, organization_id, date, minyan_type, start_time, source (IMPORTED/RULES/MANUAL), source_ref, enabled, notes, location_id, nusach, manually_edited, edited_by, edited_at
- **EventSource** enum: IMPORTED (from calendar import), RULES (generated from Minyan entities), MANUAL (future feature for admin overrides)
- **CalendarEventRepository**: JPA repository with 20+ query methods including precedence logic, date range filtering, organization filtering
- **CalendarMaterializationService**: Generates calendar_events from Minyan entities and OrganizationCalendarEntry records
  - Delete + rebuild strategy for RULES events (preserves IMPORTED/MANUAL)
  - Rolling 11-week window (3 past, 8 future weeks)
  - Day-level precedence: if any imported events exist for org+date, suppress all rules for that day
- **CalendarMaterializationScheduler**: `@Scheduled` task runs weekly (Sunday 2 AM) and on application startup
- **EffectiveScheduleService**: Query layer that applies day-level precedence when fetching events
  - `getEffectiveEventsForDate(orgId, date)`: Returns only effective events (precedence applied)
  - `getAllEventsForDate(orgId, date)`: Returns all events for admin views
- **CalendarEventAdapter**: Converts `CalendarEvent` entities to `MinyanEvent` display objects for frontend compatibility
- **CalendarEventsAdminController**: Admin UI for managing calendar events with filters, statistics, enable/disable, inline editing

### Time Calculation Layer (`service/`)
- **ZmanimService** (~700 lines, refactored in v1.4.0): Primary view orchestrator. Queries materialized calendar_events via `EffectiveScheduleService`, converts to `MinyanEvent` via `CalendarEventAdapter`
  - `getZmanim(Date)`: Renders homepage with all services from materialized calendar, organized by type (Shacharis/Mincha/Maariv)
  - `org(orgId, Date)`: Renders org-specific page from materialized calendar with Hebrew dates and zmanim display
  - `shouldDisplayEvent()`: Helper method for time-based filtering (Shacharis/Mincha/Maariv windows, termination dates)
  - **Removed in v1.4.0**: Dual code paths, scheduleResolver branching, on-demand rule computation
- **ZmanimHandler**: Wraps Kosherjava library; returns `Dictionary<Zman, Date>` of 14+ prayer times; registered as `@Service` bean
- **MinyanService**: Simple CRUD layer. `setupMinyanObj()` populates `Schedule` from database strings (used for materialization, not frontend display)
- **CalendarImportService**: Handles import, classification, and persistence of calendar entries (now materializes to calendar_events)
- **MinyanClassifier**: Pattern-based classification with title qualifier extraction
- **Timezone**: Hardcoded to "America/New_York"; set at app startup in `TeaneckMinyanimApplication.main()`

### Minyan Scheduling Model
- **Minyan** entity stores 11 time strings: `startTime1` (Sunday) through `startTime7` (Shabbat), plus `startTimeRC`, `startTimeYT`, `startTimeCH`, `startTimeCHRC`
- **MinyanTime** parses each string; supports fixed time ("HH:MM") or Zman-based ("NETZ+5min", "PLAG-10min", etc.)
- **TimeRule**: Experimental "rounded" mode aligns times across week to nearest 5-minute interval
- **Schedule.getMappedSchedule()**: Returns `HashMap<MinyanDay, MinyanTime>` for display

### Enums & Type Safety
- **MinyanType**: SHACHARIS, MINCHA, MAARIV, MINCHA_MAARIV, SELICHOS, MEGILA_READING (see `displayName()` for UI strings)
- **MinyanClassification**: MINYAN, MINCHA_MAARIV, NON_MINYAN, OTHER (for imported calendar entries)
- **EventSource** (v1.4.0): IMPORTED, RULES, MANUAL (for calendar_events source tracking)
- **Nusach**: ASHKENAZ, SEFARD, EDOT_HAMIZRACH, ARIZAL, UNSPECIFIED (each has `is*()` helper methods)
- **Zman**: 14+ Jewish times (ALOS_HASHACHAR, NETZ, MISHEYAKIR, etc.)
- All enums have `fromString()` and `displayName()` methods

### Controllers
- **ZmanimController**: Routes homepage (`/`) and subscription page
- **AdminController**: 1499-line monolith handling org/minyan CRUD, security checks, and schedule editing
  - Uses `isSuperAdmin()` and `getCurrentUser()` for access control
  - Creates/updates minyanim via form params (`sunday-time-type`, `sunday-fixed-time`, etc.)
- **CalendarEventsAdminController** (v1.4.0): Manages materialized calendar events with full CRUD
  - Routes: `/admin/{orgId}/calendar-events` (org-specific), `/admin/calendar-events/all` (super admin master calendar)
  - Filters: date range, minyan type, source (IMPORTED/RULES/MANUAL), enabled status
  - Actions: enable/disable toggle, inline edit notes/location, delete (manual only), trigger rematerialization
  - Uses `@ModelAttribute` for siteName and supportEmail from ApplicationSettingsService
  - Authorization via `TNMUserService.canAccessOrganization()`

### Admin UI Patterns (v1.4.0 - Backpack for Laravel-Inspired)
- **Design System**: CSS variables in `/static/admin/design-system.css` (colors, spacing, typography, shadows, transitions)
- **Page Structure**: navbar (top), sidebar (left), main content area with header (H1 + lead paragraph) and content
- **Statistics Cards**: Grid of stat cards at top showing key metrics (total, enabled, by source)
- **Filter Panels**: Collapsible filter panel above tables for advanced querying
- **Data Tables**: Clean tables with sticky headers, hover effects, sortable columns, responsive wrapping
- **Color-Coded Badges**: Blue (rules), Green (imported), Orange (manual), semantic colors for status/type
- **Consistent Templates**: All admin pages follow same structure (see `templates/admin/calendar-events.html`)
- **Settings Integration**: Always inject siteName/supportEmail via `@ModelAttribute` for branding
- **Responsive Design**: Mobile-first, collapsible sidebar, horizontal-scrolling tables

### Security
- **WebSecurityConfiguration**: Spring Security setup
- **Encrypter**: Password hashing for `TNMUser`
- Admin actions check org ownership: `!getCurrentUser().getOrganizationId().equals(minyan.getOrganizationId())`

## Database (MariaDB)
- **URL**: `jdbc:mariadb://localhost:3306/minyanim` (hardcoded in application.properties)
- **Credentials**: root / passw0rd (hardcoded - see security section below)
- **Schema**: Hibernate auto-updates via `spring.jpa.hibernate.ddl-auto=update`
- **Tables**: Minyan, Organization, Location, Account, TNMUser, TNMSettings, OrganizationCalendarEntry, **CalendarEvent** (v1.4.0)

### Calendar Events Table (v1.4.0)
**Primary materialized calendar table - single source of truth:**
```
ID, ORGANIZATION_ID, DATE, MINYAN_TYPE (enum), START_TIME,
SOURCE (enum: IMPORTED/RULES/MANUAL), SOURCE_REF,
ENABLED (boolean, default true), MANUALLY_EDITED (boolean),
EDITED_BY, EDITED_AT, NOTES, LOCATION_ID, NUSACH,
CREATED_AT, UPDATED_AT, VERSION

Indexes:
- idx_org_date (organization_id, date)
- idx_org_date_type_time (organization_id, date, minyan_type, start_time)
```

**Materialization Strategy:**
- Weekly delete + rebuild for RULES events (preserves IMPORTED/MANUAL)
- Rolling 11-week window (3 past, 8 future)
- Day-level precedence: imported events suppress all rules for that org+date

## Frontend Patterns
- **Templating**: Thymeleaf (Spring Security integration via `thymeleaf-extras-springsecurity5`)
- **CSS**: Bootstrap 4.6 with custom admin styles (dashboard.css, minyanschedule.css)
- **JS**: jQuery 3.6 for form validation and DOM manipulation
  - `admin/minyanschedule.js`: Handles tab switching and time input forms
  - `frontindex.js`: Homepage minyan filtering and sorting
- **Maps**: Mapbox library available (mapbox.js) but not heavily integrated

## Building & Running

```bash
# Build
mvn clean install

# Run
mvn spring-boot:run

# Test (minimal - mostly disabled in pom.xml)
mvn test
```

**Note**: Development requires MariaDB running locally with database `minyanim` initialized.

## Important Context
- **Deprecated Code**: `TimeRule` contains TODOs about deprecated Java Date methods
- **Hebrew Calendar Logic**: Heavy reliance on Kosherjava library; Teaneck coordinates are hardcoded (not configurable)
- **No REST API**: All endpoints return HTML views via `ModelAndView`; frontend uses form submissions
- **Credentials in Code**: Database password and hardcoded coordinates in properties - address before production deployment

## Code Style
- Uses Lombok `@RequiredArgsConstructor` for dependency injection
- Service classes use `@Slf4j` for logging
- Repositories are Spring Data JPA
- Form validation is minimal; relies on browser validation
- Schedule times stored as strings ("HH:MM" or "NETZ+5") - parsed at runtime

## Version Management
- **ALWAYS increment the version number for changes that necessitate a PR**
  - Use semantic versioning: patch (`x.x.1`) for UI updates/fixes, minor (`x.1.0`) for new features, major (`1.x.0`) for breaking changes
  - Update the `pom.xml` version field (located at line 13: `<version>1.1.0-SNAPSHOT</version>`)
  - Add a corresponding entry to `CHANGELOG.md`:
    - **Patch versions (`x.x.1`)**: Short, 2-line summary of changes under appropriate subsections (Added/Changed/Fixed/Removed)
    - **Minor versions (`x.1.0`)**: Longer entries with detailed feature descriptions, organized by subsections (Added/Changed/Fixed/Deprecated/Removed/Security)
    - **Major versions (`1.x.0`)**: Comprehensive entries documenting breaking changes and migration paths, with detailed explanations of impacts
- **EXCEPTIONS — do NOT bump the version or add a CHANGELOG entry for:**
  - Changes to AI agent instruction files (`.github/copilot-instructions.md`, `CLAUDE.md`, or any other agent/AI context files)
  - Developer tooling configuration that does not affect runtime behavior
- Apply these instructions specifically to Teaneck-Minyanim:
  - Document all user-facing changes (UI updates, new features, behavior changes)
  - Document infrastructure updates (dependency upgrades, database schema changes, configuration changes)
  - Document fixes that impact functionality or dependencies
  - Follow the [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) format with sections: Added, Changed, Deprecated, Removed, Fixed, Security
  - Place new entries under `[Unreleased]` section at the top of CHANGELOG.md
  - When releasing, move unreleased changes to a new version section with date: `## [x.y.z] - YYYY-MM-DD`

## MinyanTime & Schedule Parsing

### Time Format Rules
The `MinyanTime` class parses time strings with specific prefixes:
- **Fixed Time** (e.g., "T06:00:00:0"): Format `T{hours}:{minutes}:{seconds}:{centiseconds}`
- **Dynamic Time** (e.g., "RNETZ:10"): Format `R{ZmanEnum}:{offsetMinutes}` - calculates from Jewish calendar
- **Rounded Time** (e.g., "QPLAG_HAMINCHA:-5"): Format `Q{ZmanEnum}:{offsetMinutes}` - dynamic time rounded to nearest 5-minute interval
- **No Time** ("NM"): Minyan not offered on that day

### Zman Enum Values
16 prayer times available in `Zman.java`:
- Morning: ALOS_HASHACHAR, ETT, MISHEYAKIR, NETZ, SZKS (Sof Zman Krias Shma), MASZKS, SZT, MASZT
- Afternoon: CHATZOS, MINCHA_GEDOLA, MINCHA_KETANA, PLAG_HAMINCHA
- Evening: SHEKIYA, EARLIEST_SHEMA, TZES
- Overnight: CHATZOS_LAILA

All have `fromString()` (case-insensitive) and `displayName()` methods for UI display.

### Schedule Structure
`Schedule` wraps 11 day types in separate `MinyanTime` fields:
```java
sunday, monday, tuesday, wednesday, thursday, friday, shabbos,
roshChodesh, yomTov, chanuka, roshChodeshChanuka
```
Call `getMappedSchedule()` to get `HashMap<MinyanDay, MinyanTime>` for template iteration.

## Database Schema (MariaDB)

**Minyan Table** (schedule storage):
```
ID, TYPE (enum), LOCATION_ID, ORGANIZATION_ID, ENABLED,
START_TIME_1 to START_TIME_7 (days Sun-Sat),
START_TIME_RC, START_TIME_YT, START_TIME_CH, START_TIME_CHRC,
NOTES, NUSACH (enum), WHATSAPP, VERSION
```

**Organization Table**: id, name, address, website_uri, nusach, org_color, version

**Location Table**: id, name, organization_id, version

**TNMUser Table**: id, username, email, encrypted_password, organization_id, role_id, version

**TNMSettings Table**: id, setting, enabled, text, type, version

## Admin Workflow: Creating/Editing Minyanim

### Minyan Creation Flow (`/admin/{orgId}/minyanim/create`)
1. Form submits with parameters for each day: `{day}-time-type`, `{day}-fixed-time`, `{day}-zman`, `{day}-zman-offset`
2. `AdminController.createMinyan()` validates and parses form inputs
3. Days: `sunday|monday|tuesday|wednesday|thursday|friday|shabbos` plus special overrides `rosh-chodesh|yom-tov|chanuka|rosh-chodesh-chanuka`
4. Time type selector determines parsing: "fixed" (field: `{day}-fixed-time` HH:MM), "dynamic" (fields: zman enum + offset), "rounded" (zman enum + offset, rounded), "none"
5. `Schedule` object constructed and stored as string in Minyan entity
6. On retrieval, `MinyanService.setupMinyanObj()` reconstructs `Schedule` and `MinyanTime` objects

### Time Display Rules
- **MinyanEvent**: Wraps a Minyan with computed `startTime` (Date object) for a specific day
- **Dynamic times** show as "HH:MM (Zman+offset)" in templates
- **Fixed times** show as simple "HH:MM aa"
- Template uses `MinyanEvent.getFormattedStartTime()` to render

## Frontend State Management

### Homepage Flow (`getZmanim()`)
1. Fetch enabled minyanim via `minyanService.getEnabled()`
2. For each minyan, call `minyan.getStartDate(ref)` with reference LocalDate
3. Build `MinyanEvent` list with calculated times
4. Filter into three lists: shacharisMinyanim, minchaMinyanim, maarivMinyanim (by type)
5. Pass to homepage.html template
6. Frontend `frontindex.js`: filtering, sorting, search functionality

### Organization-Specific Page (`org()`)
1. Load all Jewish times (Zmanim) via `ZmanimHandler.getZmanim()`
2. Filter enabled minyanim to org using `minyanService.findEnabledMatching(orgId)`
3. Build MinyanEvent objects with location/nusach metadata
4. Group by prayer type; calculate "next minyan" based on current time
5. Add Hebrew date via `zmanimHandler.getHebrewDate()`

## Security Model

### Role Hierarchy (defined in `Role` enum)
- **SUPER_ADMIN**: Super user, unrestricted access, can manage all organizations
- **ADMIN**: Organization administrator, can manage users/locations/minyanim within their org
- **USER**: Regular user, can view organization data but limited edit permissions

### Permissions Checks
- `isSuperAdmin()`: Checks for SUPER_ADMIN role
- `isAdmin()`: Checks ADMIN or higher
- `isUser()`: Checks USER or higher
- `getCurrentUser()`: Retrieves authenticated user; check `getOrganizationId()` for org ownership
- Pattern: `if (!getCurrentUser().getOrganizationId().equals(minyan.getOrganizationId())) throw AccessDeniedException`

## Testing Notes
- Tests mostly disabled in `pom.xml` (commented out spring-boot-starter-test)
- Minimal test coverage in `TeaneckMinyanimApplicationTests.java`
- Use Mockito and JUnit 5 for future test writing

## Common Tasks
- **Add new minyan type**: Add enum value to `MinyanType`, update `displayName()`
- **Modify schedule UI**: Edit form in templates at `templates/admin/minyanim/{new,update}.html`
- **Change Teaneck coordinates**: Update hardcoded values in both `ZmanimService.java` (line 30-35) and `ZmanimController.java` (line 25-30)
- **Add organization-wide setting**: Add column to `TNMSettings` entity, expose via `TNMSettingsService`, add to settings template
- **Add new Zman type**: Add to `Zman.java` enum, implement `displayName()` switch case, reference in time calculations
- **Modify admin form**: Edit `templates/admin/minyanim/new.html` and `update.html`; form fields post to `AdminController` with standard naming convention
- **Add new classification pattern**: Update `MinyanClassifier` allowlist/denylist patterns
- **Add new title qualifier**: Add to `extractTitleQualifier()` method in `MinyanClassifier`

## Calendar Import System (Added v1.2.2)

### Overview
The calendar import system allows organizations to import minyan schedules from external calendars (CSV, ICS, etc.). Events are automatically classified, enriched with Jewish calendar data, and displayed alongside rule-based minyanim.

### Key Components

#### MinyanClassifier (`service/calendar/MinyanClassifier.java`)
Intelligent pattern-based classification service with title processing:

**Classification Types:**
- `MINYAN` - Prayer services (Shacharis, Mincha, Maariv)
- `MINCHA_MAARIV` - Combined afternoon/evening services (includes Shkiya calculation)
- `NON_MINYAN` - Learning/social events (Daf Yomi, Shiur, Kiddush, Candle Lighting)
- `OTHER` - General minyan-related events (Selichos, Sunrise Minyan)

**Pattern Priority:**
1. Combined Mincha/Maariv patterns (most specific)
2. Denylist patterns (NON_MINYAN) - wins over allowlist
3. Allowlist patterns (MINYAN)
4. Default: NON_MINYAN (conservative approach)

**Title Qualifier Extraction:**
Automatically extracts qualifiers from titles and adds to notes:
- Teen, Youth, Young Adult
- Early, Late
- Fast, Quick, Express
- Main, Second
- Women's, Men's
- Kollel, Vasikin, Hanetz

Example: "Teen Minyan" → title: "Minyan", notes: "Teen"

**Shkiya Integration:**
For MINCHA_MAARIV entries, automatically calculates and appends sunset time to notes:
- Uses ZmanimHandler for accurate calculation
- Format: "Shkiya: 4:38 PM"
- Combined with extracted qualifiers if present

#### CalendarImportService (`service/calendar/CalendarImportService.java`)
Handles import, deduplication, and persistence:

**Core Behaviors:**
- NON_MINYAN events automatically set to `enabled = false`
- Enforced during both create and update operations
- Respects manual location edits (location_manually_edited flag)
- Classification re-applied on reimport
- Manually edited entries preserved unless "Reimport All" used

#### CalendarImportProvider (`service/provider/CalendarImportProvider.java`)
Fetches calendar entries for display:

**Display Logic:**
- Only fetches enabled entries (`findByOrganizationIdAndDateAndEnabledTrue`)
- Notes field shown directly (contains Shkiya + qualifiers)
- Description field NOT automatically appended
- Integrates with ZmanimService for unified minyan display

#### OrganizationCalendarEntry (Model)
Database entity for imported events:

**Key Fields:**
- `classification` (enum): Event classification
- `classificationReason` (text): Explanation of classification decision
- `notes` (text): Shkiya time and extracted qualifiers
- `location_manually_edited` (boolean): Manual edit tracking
- `manually_edited_by` (varchar): Username of editor
- `manually_edited_at` (timestamp): Edit timestamp
- `enabled` (boolean): Display toggle (auto-disabled for NON_MINYAN)

### Admin UI Features

#### Calendar Entries Management (`/admin/{orgId}/calendar-entries`)
Modern table with:
- **Sortable columns**: date, time, title, type, enabled, importedAt (default: date ASC)
- **Filters**: date range, text search, classification, enabled status
- **Statistics dashboard**: Entry counts by classification
- **Color-coded badges**: Classification (Green: MINYAN, Cyan: MINCHA_MAARIV, Gray: NON_MINYAN)
- **Prayer type pills**: Based on title text (Blue: Shacharis, Amber: Mincha, Purple: Maariv)
- **Inline location editing**: Click-to-edit with dropdown, manual change tracking
- **"Show Non-Minyan" toggle**: View excluded events for debugging
- **"Reimport All" button**: Override all entries including manual edits (with confirmation)

### Best Practices

**Classification Patterns:**
- Use word boundaries (`\b`) to prevent false positives
- Denylist should be explicit and comprehensive
- Test with real calendar data before deploying
- Check `classificationReason` for debugging misclassifications

**Notes Field Management:**
- Only classifier-generated content goes in notes
- Don't append description automatically
- Use title qualifiers for admin-controlled notes
- Shkiya always appended for MINCHA_MAARIV

**Manual Edits:**
- Always check `location_manually_edited` flag before overwriting
- Track editor username and timestamp
- Provide explicit "Reimport All" for full override
- Show visual indicator (blue dot) for manually edited fields

### Testing
See `MinyanClassifierTest.java` for comprehensive test examples:
- 43 tests covering all classification scenarios
- Shkiya note generation and formatting
- Title qualifier extraction
- Priority ordering
- Edge cases (null, empty, combined fields)
