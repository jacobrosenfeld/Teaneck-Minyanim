# Teaneck Minyanim v1.2 - Known Limitations & Future Work

## Current Limitations

### 1. Public UI Integration Not Complete

**Status:** The schedule provider architecture is in place but not fully integrated.

**What's Missing:**
- Homepage (`ZmanimService.getZmanim()`) still uses old pattern - directly queries `minyanService.getEnabled()`
- Organization page (`ZmanimService.org()`) still uses old pattern - queries `minyanService.findEnabledMatching()`
- Should be using `OrgScheduleResolver.getMinyanEvents()` instead

**Impact:**
- Admin can set up calendar scraping and view entries
- But public-facing pages don't yet display scraped data
- Rule-based schedules continue to show for all organizations

**Next Steps:**
- Refactor `ZmanimService.getZmanim()` to use resolver
- Refactor `ZmanimService.org()` to use resolver
- Update homepage template if needed for date-specific queries
- Test that filtering/sorting works with scraped data

### 2. Limited Calendar Format Support

**Status:** Scraper supports common patterns but may fail on complex layouts.

**What's Supported:**
- Table-based calendars (TR/TD structure)
- List-based calendars (UL/LI or DIV structure)
- Google Calendar embeds (basic structure)
- Static HTML only

**What's Not Supported:**
- JavaScript-rendered calendars (React, Vue, Angular apps)
- iFrame-embedded calendars from external domains
- PDF/image-based calendars
- Unusual or proprietary calendar formats

**Workarounds:**
- Admin can manually enter entries (future feature)
- Use calendar export URLs that provide HTML

**Next Steps:**
- Add support for more calendar platforms
- Implement Selenium/headless browser for JS rendering
- Add manual entry UI

### 3. Conservative Deduplication

**Status:** Automatic deduplication avoids false positives but may miss true duplicates.

**Current Behavior:**
- Exact fingerprint matches are caught
- Entries with same date, similar title, and time within 5 minutes are flagged
- Auto-disables only when new entry has more detail (longer raw text)

**Limitations:**
- May keep entries humans would consider duplicates
- Doesn't handle typos or alternate spellings well
- No fuzzy matching for titles

**Workarounds:**
- Admin can manually disable duplicate entries
- Calendar entries page allows filtering and review

**Next Steps:**
- Add fuzzy string matching for titles
- Implement "Disable Duplicates" bulk action
- Add AI-powered duplicate detection

### 4. No Automatic Retry or Error Recovery

**Status:** Failed scrapes are logged but not automatically retried.

**Current Behavior:**
- One failure per org doesn't break entire sync
- Error messages logged and returned in sync result
- Manual re-sync required to retry

**Limitations:**
- Transient network errors require manual intervention
- No exponential backoff for rate limiting
- No circuit breaker for repeatedly failing URLs

**Next Steps:**
- Add retry logic with exponential backoff
- Implement circuit breaker pattern
- Send admin notifications for repeated failures

### 5. Testing Infrastructure Incomplete

**Status:** Test file created but can't run due to dependency issues.

**Current State:**
- `CalendarNormalizerTest.java` exists with comprehensive tests
- JUnit Jupiter dependency conflict prevents execution
- Project has minimal existing test infrastructure

**Limitations:**
- Unit tests can't verify normalizer behavior
- No integration tests for scraping/sync
- No test fixtures for HTML parsing

**Next Steps:**
- Fix JUnit dependency issues in pom.xml
- Add Spring Boot Test dependencies
- Create test fixtures (sample HTML files)
- Add integration tests for repository operations

### 6. No Bulk Operations

**Status:** Admin must enable/disable entries one at a time.

**What's Missing:**
- No "Select All" checkbox
- No "Disable Duplicates" button
- No "Re-run Dedupe" action
- No bulk delete

**Impact:**
- Time-consuming for orgs with many entries
- Manual review required for duplicate cleanup

**Next Steps:**
- Add checkboxes and bulk action toolbar
- Implement "Disable Duplicates" feature
- Add "Re-run Dedupe" that re-fingerprints all entries

### 7. No Location or Nusach Extraction

**Status:** Scraped entries don't capture location or nusach information.

**Current Behavior:**
- Only date, time, and title are extracted
- Type is inferred from title keywords
- No location association

**Limitations:**
- Can't filter by location on public pages
- Can't show nusach variations
- Less detailed than rule-based entries

**Next Steps:**
- Parse location from calendar entry text
- Detect nusach keywords in titles
- Associate with existing Location entities

### 8. No Conflict Detection

**Status:** System doesn't warn if scraped times conflict with rule-based schedules.

**Current Behavior:**
- When scraped calendar is enabled, rule-based ignored completely
- No comparison between two sources
- No alerting for discrepancies

**Limitations:**
- Admin doesn't know if scraped data is accurate
- No way to validate scraped entries
- Rule-based schedules become "dark" when scraping enabled

**Next Steps:**
- Add conflict detection page
- Show side-by-side comparison
- Warn admin of significant differences

### 9. No Public API or Webhooks

**Status:** No programmatic access to calendar data.

**What's Missing:**
- No RESTful API for calendar entries
- No JSON export
- No webhook support for real-time updates
- No iCal export

**Impact:**
- External apps can't consume data
- No integration with other systems
- Manual sync only

**Next Steps:**
- Create REST API endpoints
- Add iCal export feature
- Implement webhook triggers on calendar updates

### 10. Single Timezone Assumption

**Status:** Hardcoded to "America/New_York" timezone.

**Current Behavior:**
- All times assumed to be EST/EDT
- No timezone parsing from calendar
- No conversion for orgs in different zones

**Limitations:**
- Can't support organizations outside Teaneck area
- Daylight saving time handled by JVM only

**Next Steps:**
- Add timezone field to Organization
- Parse timezone from calendar entries
- Display times in appropriate timezone

## Security Considerations

### Current State
- Rate limiting: 2 seconds between orgs ✓
- Timeout: 10 seconds per request ✓
- User agent identified ✓
- Admin-only access to sync ✓
- Per-org access control ✓

### Potential Issues
- No CAPTCHA bypass detection
- No abuse prevention for manual sync button
- Fingerprint hash not salted (but SHA-256 is sufficient for this use case)

### Recommendations
- Add rate limiting on manual sync endpoint
- Log all sync attempts for audit
- Consider adding salt to fingerprint for enhanced security

## Performance Considerations

### Current State
- Indexed queries on org + date ✓
- Unique constraint on fingerprint ✓
- Pagination support in repository ✓

### Potential Issues
- Large number of entries could slow admin page
- No caching of scraped data
- Weekly sync blocks thread pool

### Recommendations
- Add pagination to calendar entries page
- Implement caching with TTL
- Use async processing for scheduled sync

## Migration Instructions

### For Existing Installations

1. **Backup Database:**
   ```bash
   mysqldump -u root -p minyanim > backup_pre_v1.2.sql
   ```

2. **Run Migration Script:**
   ```bash
   mysql -u root -p minyanim < migration_v1.2.sql
   ```

3. **Deploy New Code:**
   ```bash
   mvn clean package
   # Deploy JAR to production server
   ```

4. **Restart Application:**
   ```bash
   # Restart Spring Boot application
   # Verify @Scheduled tasks are registered
   ```

5. **Test Calendar Feature:**
   - Add calendar URL to an organization
   - Enable "Use Scraped Calendar"
   - Click "Refresh Zmanim Sync"
   - Verify entries appear in "Manage Calendar Entries"

### Rollback Procedure

If v1.2 causes issues:

1. **Restore Database:**
   ```bash
   mysql -u root -p minyanim < backup_pre_v1.2.sql
   ```

2. **Revert Code:**
   ```bash
   git checkout v1.1.0
   mvn clean package
   # Deploy previous version
   ```

3. **Notes:**
   - Rolling back will lose scraped calendar data
   - Organization `calendar` and `use_scraped_calendar` fields will be undefined
   - Public pages will continue to work with rule-based schedules

## Future Roadmap

### Short Term (v1.3)
- Complete public UI integration
- Add bulk operations
- Improve deduplication with fuzzy matching

### Medium Term (v1.4)
- Add location and nusach extraction
- Implement conflict detection
- Create REST API

### Long Term (v2.0)
- AI-powered calendar parsing
- Multi-timezone support
- Real-time calendar updates via webhooks
- Mobile app integration
