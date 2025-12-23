# v1.2.1 Calendar Import Feature - Architecture Documentation

## Overview
Version 1.2.1 adds support for importing minyan times from external calendar CSV exports. This allows organizations to maintain their schedules in external calendar systems while automatically syncing to Teaneck Minyanim.

## Architecture

### High-Level Flow
```
External Calendar System
    ↓ (CSV Export URL)
CalendarImportService
    ↓ (fetch & parse)
OrganizationCalendarEntry (database)
    ↓ (when enabled)
CalendarImportProvider
    ↓ (via OrgScheduleResolver)
ZmanimService → Public UI
```

### Key Components

#### 1. Data Model (`model/`)
- **Organization**: Added `use_scraped_calendar` Boolean field (nullable, defaults to false)
- **OrganizationCalendarEntry**: New entity storing imported events
  - Fields: date, start_time, title, type, location, description, hebrew_date
  - Includes: fingerprint (for deduplication), enabled flag, timestamps

#### 2. Import Infrastructure (`service/calendar/`)
- **CalendarUrlBuilder**: Generates CSV export URLs with proper query parameters
  - Date range: today - 7 days to today + 56 days (8 weeks)
  - Parameters: advanced, date_start_date, date_end_date, view=other, other_view_type=csv
  - Uses Spring's UriComponentsBuilder for safe URL construction

- **CalendarCsvParser**: Robust CSV parsing with Apache Commons CSV
  - Tolerates: missing columns, reordered columns, various datetime formats
  - Normalization: titles and times for deduplication
  - Column name variations: "Start"/"start_date", "Name"/"title", etc.

- **CalendarImportService**: Main import orchestration
  - Fetches CSV via Java HttpClient (HTTP/1.1 with 30s timeout)
  - Parses entries via CalendarCsvParser
  - Deduplicates: same org + date + normalized title + normalized time
  - Auto-disables duplicates with reason annotation
  - Provides ImportResult with statistics (new, updated, duplicates skipped)

- **CalendarImportScheduler**: Automated weekly imports
  - Cron: Every Sunday at 2:00 AM
  - Processes all organizations with calendar URL and useScrapedCalendar=true
  - Rate limiting: 2-second delay between organizations
  - Per-org error isolation (one failure doesn't stop others)

#### 3. Provider Architecture (`service/provider/`)
- **OrgScheduleProvider** (interface): Abstract schedule data source
  - Methods: getEventsForDate(), canHandle(), getPriority(), getProviderName()
  
- **CalendarImportProvider**: Uses imported calendar entries
  - Priority: 100 (higher than rule-based)
  - canHandle(): Returns true if calendar URL configured and useScrapedCalendar=true
  - Converts OrganizationCalendarEntry → MinyanEvent
  - Infers MinyanType from entry title (Shacharis/Mincha/Maariv keywords)

- **RuleBasedProvider**: Legacy rule-based generation (existing Minyan entities)
  - Priority: 10 (lower than calendar import)
  - canHandle(): Always returns true (fallback)
  - Marker provider - actual logic remains in ZmanimService

- **OrgScheduleResolver**: Provider selection orchestrator
  - Auto-injects all OrgScheduleProvider beans
  - Sorts by priority (highest first)
  - Returns first provider that canHandle() the organization
  - Used by ZmanimService to determine data source

#### 4. Service Integration (`service/`)
- **ZmanimService.org()**: Organization-specific page
  - Checks `scheduleResolver.isCalendarImportEnabled(orgId)`
  - If enabled: fetches events from CalendarImportProvider
  - If disabled: uses existing rule-based logic (Minyan entities)
  - Maintains backward compatibility

#### 5. Admin UI (`controllers/` + `templates/admin/`)
- **AdminController** endpoints:
  - `POST /admin/{orgId}/calendar/import`: Manual import trigger
  - `GET /admin/{orgId}/calendar-entries`: View/manage imported entries
  - `POST /admin/{orgId}/calendar-entries/{entryId}/toggle`: Enable/disable entry

- **calendar-entries.html**: Management interface
  - Shows: calendar URL, import status, entry counts (total/enabled/disabled)
  - "Refresh Zmanim Sync" button for manual import
  - Table: sortable by date, filterable, with enable/disable toggles
  - Displays duplicate reasons when entries are auto-disabled

#### 6. Repository Layer (`repo/`)
- **OrganizationCalendarEntryRepository**: Spring Data JPA
  - Queries: by org+date, by date range, by fingerprint
  - Indexes: (organization_id, date), (organization_id, enabled, date)
  - Unique constraint: fingerprint

### Data Flow: Import Process

1. **Trigger** (manual or scheduled)
   - Manual: Admin clicks "Refresh Zmanim Sync"
   - Scheduled: CalendarImportScheduler at Sunday 2 AM

2. **URL Generation**
   - CalendarUrlBuilder.buildCsvExportUrl(org.calendar)
   - Appends parameters: date_start_date, date_end_date, view=other, other_view_type=csv

3. **HTTP Fetch**
   - CalendarImportService.fetchCsvContent()
   - Uses Java HttpClient with User-Agent: "TeaneckMinyanim/1.2.1"
   - Timeout: 30 seconds

4. **Parse CSV**
   - CalendarCsvParser.parseCsv(csvContent)
   - Handles multiple datetime formats
   - Extracts: date, start_time, title, type, location, description, hebrew_date

5. **Deduplication**
   - Generate fingerprint: SHA-256(org_id|date|normalized_title|normalized_time)
   - Check existing: entryRepository.findByFingerprint()
   - If exists: update entry
   - If new: check isDuplicate() for similar entries

6. **Persist**
   - Save to OrganizationCalendarEntry table
   - Set enabled=false for detected duplicates with reason

7. **Return Result**
   - ImportResult: success, newEntries, updatedEntries, duplicatesSkipped, errorMessage

### Data Flow: Public Display

1. **User visits org page**: `/orgs/{slug}`
2. **ZmanimController** routes to ZmanimService.org()
3. **ZmanimService checks provider**:
   - `scheduleResolver.isCalendarImportEnabled(orgId)`
   - If true → CalendarImportProvider.getEventsForDate()
   - If false → RuleBasedProvider (existing Minyan logic)
4. **Build MinyanEvent list**
5. **Sort and filter** by type (Shacharis/Mincha/Maariv)
6. **Render template** with events

## Database Schema Changes

### New Table: `organization_calendar_entry`
```sql
CREATE TABLE organization_calendar_entry (
    ID BIGINT AUTO_INCREMENT PRIMARY KEY,
    organization_id VARCHAR(255) NOT NULL,
    date DATE NOT NULL,
    start_time TIME,
    start_datetime DATETIME,
    end_time TIME,
    end_datetime DATETIME,
    title VARCHAR(255) NOT NULL,
    type VARCHAR(255),
    name VARCHAR(255),
    location VARCHAR(255),
    description TEXT,
    hebrew_date VARCHAR(255),
    raw_text TEXT,
    source_url TEXT,
    fingerprint VARCHAR(255) NOT NULL UNIQUE,
    enabled BOOLEAN DEFAULT TRUE NOT NULL,
    duplicate_reason VARCHAR(255),
    imported_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    
    INDEX idx_org_date (organization_id, date),
    INDEX idx_org_enabled_date (organization_id, enabled, date),
    CONSTRAINT fk_org_calendar_entry FOREIGN KEY (organization_id) 
        REFERENCES organization(ID) ON DELETE CASCADE
);
```

### Modified Table: `organization`
```sql
ALTER TABLE organization 
ADD COLUMN USE_SCRAPED_CALENDAR BOOLEAN DEFAULT FALSE NOT NULL;
```

## Configuration

### Spring Boot Settings
- `@EnableScheduling` added to TeaneckMinyanimApplication
- Timezone: America/New_York (hardcoded in ZmanimService)

### Dependencies Added
- `commons-csv:1.10.0` - CSV parsing
- `junit-jupiter-api:5.9.2` - Testing (test scope)

## Security Considerations

### Network Security
- Uses HTTPS for calendar fetches (validation via CalendarUrlBuilder.isValidCalendarUrl())
- User-Agent header identifies the bot
- 30-second timeout prevents hanging requests
- Rate limiting: 2-second delay between org imports

### Data Validation
- CSV parsing is tolerant but validates:
  - Date formats (8 different formats supported)
  - Required fields (date, title)
  - Organization ownership (admin endpoints check getCurrentUser().getOrganizationId())

### Access Control
- Admin endpoints require isAdmin() check
- getOrganization() validates user access to org
- Toggle/delete operations verify entry.organizationId matches user's org

## Testing

### Unit Tests (23 tests, all passing)
- **CalendarUrlBuilderTest** (12 tests)
  - URL construction with/without query params
  - Date range validation
  - Null/empty input handling
  - Protocol validation

- **CalendarCsvParserTest** (11 tests)
  - Valid CSV parsing
  - Missing/reordered columns
  - Quoted fields with commas
  - Various datetime formats
  - Normalization (titles, times)
  - Empty/null content

### Test Fixtures
- `src/test/resources/fixtures/sample-calendar.csv`
  - 6 sample events (2 days × 3 services)
  - Demonstrates expected CSV format

### Integration Testing
- Manual testing recommended:
  1. Configure organization calendar URL
  2. Enable useScrapedCalendar
  3. Trigger manual import
  4. Verify entries in admin panel
  5. Check organization page displays calendar events

## Known Limitations

1. **No Automatic Conflict Resolution**
   - Duplicates are auto-disabled but require manual review
   - No merge strategy for conflicting entries

2. **No Bulk Operations**
   - Admin can only toggle entries one at a time
   - No mass enable/disable or delete

3. **Fixed Date Range**
   - Imports last 7 days + next 56 days
   - Not configurable per organization

4. **MinyanType Inference**
   - Based on keyword matching in title
   - May misclassify non-standard titles
   - Defaults to SHACHARIS if unable to determine

5. **No Historical Audit**
   - Import history not tracked (only last import timestamp on entry)
   - No record of past import successes/failures

6. **CSV Format Assumptions**
   - Expects specific column names (Type, Start, End, Name, etc.)
   - May fail with significantly different formats
   - No support for non-CSV calendar formats

7. **Single Calendar per Organization**
   - Each org can have only one calendar URL
   - No support for aggregating multiple calendars

8. **No Real-time Sync**
   - Weekly schedule only (Sunday 2 AM)
   - Manual trigger required for immediate updates

9. **NextMinyan Not Supported**
   - Calendar import doesn't compute "next upcoming minyan"
   - Only works for rule-based provider

10. **No Notification System**
    - Import failures are logged but not emailed
    - Admins must check admin panel for status

## Performance Considerations

- **HTTP Fetching**: 30-second timeout, synchronous (blocks thread)
- **CSV Parsing**: In-memory parsing (entire CSV loaded at once)
- **Database Queries**: Indexed by (org_id, date) for fast lookups
- **Deduplication**: O(n) per imported entry (checks existing entries on date)
- **Memory Usage**: Scales with CSV size (typical: <1MB, <1000 entries)

## Future Enhancements (Not in v1.2.1)

1. Async import processing (background jobs)
2. Configurable date ranges
3. Multi-calendar aggregation
4. Smart conflict resolution
5. Import history/audit trail
6. Email notifications for import failures
7. Bulk operations in admin UI
8. Custom MinyanType mapping rules
9. Support for more calendar formats (iCal, Google Calendar API, etc.)
10. Real-time sync via webhooks

## Rollback Procedure

If issues arise with v1.2.1:

1. Set `useScrapedCalendar=false` for all organizations
2. Disable CalendarImportScheduler (comment out @Scheduled)
3. (Optional) Drop organization_calendar_entry table
4. (Optional) Revert Organization.useScrapedCalendar column

The rule-based system will continue to function as before.
