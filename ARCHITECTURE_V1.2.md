# Teaneck Minyanim v1.2 - Calendar Scraping Feature

## Architecture Overview

Version 1.2 introduces the ability to scrape minyan times from shul website calendars and use them as the source of truth instead of rule-based schedules.

### Key Components

#### 1. Data Model (`model/`)

**OrganizationCalendarEntry** - Stores scraped calendar data:
- `id`: Primary key (auto-increment)
- `organization_id`: FK to Organization
- `date`: LocalDate of the minyan
- `title`: Original title/label from calendar
- `type`: Inferred MinyanType (Shacharis/Mincha/Maariv/etc.)
- `time`: LocalTime of the minyan
- `rawText`: Original scraped text
- `sourceUrl`: URL where data was scraped from
- `fingerprint`: SHA-256 hash for deduplication (org + date + normalized title + time)
- `enabled`: Boolean flag (default true) - allows disabling duplicates or incorrect entries
- `scrapedAt`: Timestamp when first scraped
- `updatedAt`: Timestamp when last updated
- `dedupeReason`: Optional reason why entry was auto-disabled

**Indexes:**
- `(organization_id, date)` - Fast lookup by org and date
- `(organization_id, date, enabled)` - Fast enabled lookup
- `fingerprint` - Unique constraint for deduplication

**Organization additions:**
- `calendar`: URL to organization's online calendar
- `useScrapedCalendar`: Boolean toggle to activate scraped data (requires calendar URL)

#### 2. Scraping Layer (`calendar/`)

**CalendarScraper** - HTML parsing service:
- Uses Jsoup library to parse calendar pages
- Supports multiple calendar formats:
  - Google Calendar embeds
  - Table-based calendars
  - List-based calendars
- Extracts: date, time, title from structured and semi-structured HTML
- Configurable timeout (10s) and user-agent

**CalendarNormalizer** - Data cleaning and normalization:
- Title normalization: trim, collapse spaces, remove punctuation, lowercase
- Time parsing: handles multiple formats (12h/24h, with/without spaces, AM/PM variations)
- Type inference: matches title keywords to MinyanType enum
- Fingerprint generation: SHA-256 hash of normalized data for deduplication

**CalendarSyncService** - Orchestration:
- Scheduled sync: `@Scheduled` cron job runs Sundays at 2 AM
- Manual sync: Trigger via admin UI button
- Date range: Scrapes 8 weeks ahead, keeps 2 weeks past
- Rate limiting: 2-second delay between org scrapes
- Per-org error isolation: One org's failure doesn't break entire sync
- Deduplication:
  - Fingerprint-based exact match detection
  - Similarity check for near-duplicates (same date, similar title, time within 5 minutes)
  - Auto-disable superseded entries (keeps entry with more detail)
- Cleanup: Automatically deletes entries older than 3 weeks past

**CalendarSyncResult** - DTO for sync feedback:
- Success/failure status
- Counts: added, updated, disabled, skipped
- Error message if failed
- Sync timestamp

#### 3. Schedule Provider Architecture (`schedule/`)

**OrgScheduleProvider** (interface):
```java
List<MinyanEvent> getMinyanEvents(String organizationId, LocalDate date);
boolean canHandle(String organizationId);
```

**CalendarScrapeProvider** (implementation):
- Returns minyan events from `OrganizationCalendarEntry` table
- Only handles orgs where `calendar` URL is set AND `useScrapedCalendar` is true
- Converts entries to `MinyanEvent` objects for display

**RuleBasedProvider** (implementation):
- Wraps existing rule-based schedule logic
- Uses `Minyan` entities with 11 time columns (Sun-Sat + special days)
- Falls back provider - always returns true for `canHandle()`

**OrgScheduleResolver** (service):
- Central resolver that chooses provider for each org
- Priority: CalendarScrapeProvider → RuleBasedProvider
- Falls through to rule-based if scraped calendar has no entries

#### 4. Admin UI & API (`controllers/AdminController`)

**Endpoints:**
- `POST /admin/{orgId}/sync-calendar` - Manual sync trigger
- `GET /admin/{orgId}/calendar-entries` - View/manage scraped entries
- `POST /admin/{orgId}/calendar-entries/{entryId}/toggle` - Enable/disable entry

**Organization form additions:**
- Calendar URL text field
- "Use Scraped Calendar" checkbox
- "Refresh Zmanim Sync" button (triggers manual sync)
- "Manage Calendar Entries" link (navigates to entries page)

**Calendar Entries Management Page:**
- Table view with date, time, title, type, enabled status, scraped timestamp
- Filters: date range, search by title/text
- Actions: Enable/disable individual entries
- Summary stats: total entries, enabled count, disabled count

### Data Flow

1. **Initial Setup:**
   - Admin enters calendar URL in organization form
   - Admin enables "Use Scraped Calendar" checkbox
   - Admin clicks "Refresh Zmanim Sync" to trigger first scrape

2. **Scheduled Scraping (Weekly):**
   - Sunday 2 AM: `CalendarSyncService.scheduledSync()` runs
   - Fetches all orgs with calendar URLs
   - For each org:
     - Scrapes calendar HTML
     - Parses and normalizes entries
     - Checks fingerprints for duplicates
     - Saves/updates entries in database
     - Cleans up old entries
   - Rate limits between orgs (2s delay)
   - Isolates errors per org

3. **Public Display:**
   - User visits homepage or org page
   - `OrgScheduleResolver` checks if org uses scraped calendar
   - If yes: `CalendarScrapeProvider` returns entries from DB
   - If no: `RuleBasedProvider` returns calculated times
   - Display layer renders `MinyanEvent` objects

4. **Admin Management:**
   - Admin views calendar entries page
   - Reviews scraped data, checks for duplicates or errors
   - Disables incorrect/duplicate entries
   - Entries remain in DB but filtered out from public display

### Security & Safety

**Rate Limiting:**
- 2-second delay between organization scrapes
- 10-second timeout per HTTP request

**User Agent:**
- Identifies as "Teaneck-Minyanim/1.2 (Calendar Sync Bot)"

**Error Isolation:**
- Per-org try-catch blocks
- One org's failure doesn't break sync for others
- Error messages logged and returned in sync result

**Access Control:**
- Only admins can trigger sync
- Only admins of owning org can view/manage entries
- Security checks via `getOrganization()` method

### Database Schema

**New Table: `organization_calendar_entry`**
```sql
CREATE TABLE organization_calendar_entry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organization_id VARCHAR(255) NOT NULL,
    date DATE NOT NULL,
    title VARCHAR(500) NOT NULL,
    type VARCHAR(50),
    time TIME NOT NULL,
    raw_text TEXT,
    source_url VARCHAR(2000),
    fingerprint VARCHAR(64) NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    scraped_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    dedupe_reason VARCHAR(500),
    INDEX idx_org_date (organization_id, date),
    INDEX idx_org_date_enabled (organization_id, date, enabled),
    INDEX idx_fingerprint (fingerprint)
);
```

**Updated Table: `organization`**
```sql
ALTER TABLE organization 
    ADD COLUMN calendar VARCHAR(2000),
    ADD COLUMN use_scraped_calendar BOOLEAN DEFAULT FALSE;
```

### Precedence Rules

1. **Calendar URL present AND use_scraped_calendar = true:**
   - Use `CalendarScrapeProvider`
   - Return date-based entries from database
   - Filter to enabled entries only

2. **Calendar URL absent OR use_scraped_calendar = false:**
   - Use `RuleBasedProvider`
   - Generate times using existing rule-based logic
   - Calculate from `Minyan` entity schedules

3. **Fallback:**
   - If scraped provider returns no entries, fall back to rule-based
   - Ensures org always shows some data

### Known Limitations

1. **Calendar Format Support:**
   - Current implementation supports common patterns (tables, lists, Google embeds)
   - May require customization for unusual calendar layouts
   - No support for JavaScript-rendered calendars (requires static HTML)

2. **Date/Time Parsing:**
   - Assumes month/day/year format (e.g., 12/25/2024)
   - Assumes 12-hour or 24-hour time formats
   - May fail on unusual date/time representations

3. **Type Inference:**
   - Relies on keyword matching in titles
   - May misclassify entries with non-standard naming

4. **Deduplication:**
   - Automatic deduplication is conservative
   - May keep entries that humans would consider duplicates
   - Requires manual admin review

5. **Existing Code Not Fully Refactored:**
   - `ZmanimService` still uses old patterns
   - Homepage and org page not yet integrated with resolver
   - Phase 6 (Public UI Integration) remains incomplete

### Future Enhancements

1. **AI-Powered Parsing:**
   - Use LLM to extract structured data from unstructured calendar text
   - Handle more calendar formats automatically

2. **Location Extraction:**
   - Parse location information from calendar entries
   - Associate with existing `Location` entities

3. **Nusach Detection:**
   - Infer nusach from calendar entry text
   - Override organization default when detected

4. **Conflict Detection:**
   - Warn admin if scraped times conflict with rule-based schedules
   - Highlight potential data quality issues

5. **Bulk Operations:**
   - "Disable all duplicates" button
   - "Re-run dedupe" action for entire org

6. **Public API:**
   - RESTful endpoints for calendar data
   - Webhook support for real-time calendar updates
