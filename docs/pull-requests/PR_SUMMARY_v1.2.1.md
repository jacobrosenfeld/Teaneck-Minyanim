# Pull Request: v1.2.1 - Calendar Import Feature

## Summary
This PR implements comprehensive calendar import functionality, allowing organizations to sync minyan times from external calendar systems via CSV exports. The feature includes automatic weekly imports, manual triggers, deduplication logic, and an admin UI for managing imported entries.

## Changes Overview

### Version
- **Updated from**: 1.1.0-SNAPSHOT
- **Updated to**: 1.2.1

### Files Changed
- **51 Java source files** (6 new services, 4 new providers, 1 new entity, 1 new repository, controller updates)
- **2 test files** with 23 passing unit tests
- **1 admin template** for calendar entry management
- **3 documentation files** (DB migration, architecture, this summary)

## Key Features

### 1. Calendar CSV Import System
- **URL Generation**: Deterministic CSV export URL builder with date range parameters
- **HTTP Fetching**: Java HttpClient with 30s timeout and user-agent identification
- **CSV Parsing**: Robust Apache Commons CSV parser tolerating missing/reordered columns
- **Deduplication**: SHA-256 fingerprinting with auto-disable for duplicates

### 2. Provider Architecture
- **OrgScheduleProvider Interface**: Abstract schedule data source
- **CalendarImportProvider**: Priority 100, uses imported calendar entries
- **RuleBasedProvider**: Priority 10, falls back to existing Minyan logic
- **OrgScheduleResolver**: Auto-selects provider based on configuration

### 3. Automated Scheduling
- **Weekly Cron Job**: Every Sunday at 2:00 AM
- **Rate Limiting**: 2-second delay between organizations
- **Error Isolation**: Per-org error handling

### 4. Admin UI
- **Import Management Page**: `/admin/{orgId}/calendar-entries`
  - View all imported entries (sortable by date)
  - Enable/disable individual entries
  - Manual "Refresh Zmanim Sync" button
  - Import statistics (total, enabled, disabled counts)
  - Duplicate reason annotations

### 5. Database Changes
- **New Table**: `organization_calendar_entry` (17 columns, 2 indexes, 1 unique constraint)
- **Modified Table**: `organization` (added `use_scraped_calendar` boolean)
- **Migration Script**: `DB_MIGRATION_v1.2.1.md` with SQL and rollback instructions

### 6. Service Integration
- **ZmanimService.org()**: Integrated with OrgScheduleResolver
  - Checks if calendar import enabled
  - Uses CalendarImportProvider when enabled
  - Falls back to rule-based when disabled
  - Maintains backward compatibility

## Technical Details

### Dependencies Added
- `commons-csv:1.10.0` - Robust CSV parsing
- `junit-jupiter-api:5.9.2` - Testing support

### Code Quality
- ✅ All existing functionality preserved
- ✅ 23 unit tests passing (URL builder, CSV parser)
- ✅ Lombok @RequiredArgsConstructor for DI
- ✅ SLF4J logging throughout
- ✅ Spring Security access control on admin endpoints

### Performance
- **HTTP Fetch**: 30s timeout, blocking (acceptable for manual/scheduled)
- **CSV Parsing**: In-memory (typical: <1MB, <1000 entries)
- **Database**: Indexed queries on (org_id, date)
- **Deduplication**: O(n) per entry (linear scan on import date)

## Testing

### Unit Tests (23 tests)
```
CalendarUrlBuilderTest ........ 12 tests ✓
CalendarCsvParserTest ......... 11 tests ✓
```

### Test Coverage
- ✅ URL generation (with/without query params, validation)
- ✅ CSV parsing (various formats, missing columns, normalization)
- ✅ Error handling (null/empty inputs, invalid formats)

### Manual Testing Checklist
- [ ] Configure organization with calendar URL
- [ ] Enable `useScrapedCalendar` flag
- [ ] Trigger manual import via admin UI
- [ ] Verify entries appear in management page
- [ ] Toggle entry enabled/disabled status
- [ ] Check organization page displays calendar events
- [ ] Verify rule-based still works when disabled
- [ ] Test scheduled import (or wait for Sunday 2 AM)

## Security Considerations

### Network Security
- ✅ HTTPS URL validation
- ✅ User-Agent header: "TeaneckMinyanim/1.2.1"
- ✅ 30-second timeout prevents hanging
- ✅ Rate limiting (2s between orgs)

### Access Control
- ✅ Admin endpoints require `isAdmin()` check
- ✅ Organization ownership validation
- ✅ Entry ownership verification on toggle/delete

### Data Validation
- ✅ CSV parsing with graceful error handling
- ✅ Date format validation (8 formats supported)
- ✅ Required field validation (date, title)

## Known Limitations

1. **No Automatic Conflict Resolution** - Duplicates auto-disabled but require manual review
2. **No Bulk Operations** - Entries toggled one at a time
3. **Fixed Date Range** - Imports -7 to +56 days (not configurable)
4. **MinyanType Inference** - Keyword-based, may misclassify
5. **No Historical Audit** - Import history not tracked
6. **CSV Format Assumptions** - Expects specific column names
7. **Single Calendar per Org** - No multi-calendar aggregation
8. **No Real-time Sync** - Weekly schedule or manual trigger only
9. **NextMinyan Not Supported** - Only for rule-based provider
10. **No Notification System** - Import failures logged but not emailed

See `ARCHITECTURE_v1.2.1.md` for detailed limitations and future enhancements.

## Database Migration

**IMPORTANT**: Manual database migration required before deploying.

### Steps:
1. Backup database: `mysqldump -u root -p minyanim > backup_$(date +%Y%m%d).sql`
2. Connect: `mysql -u root -p minyanim`
3. Run SQL from `DB_MIGRATION_v1.2.1.md`
4. Verify: `SHOW COLUMNS FROM organization LIKE 'USE_SCRAPED_CALENDAR';`

**Note**: Hibernate auto-update may create the table automatically, but manual migration is recommended for production.

### Rollback:
```sql
DROP TABLE IF EXISTS organization_calendar_entry;
ALTER TABLE organization DROP COLUMN USE_SCRAPED_CALENDAR;
```

## Deployment Checklist

- [ ] Review and approve PR
- [ ] Run database migration (see `DB_MIGRATION_v1.2.1.md`)
- [ ] Deploy application (restart required for @EnableScheduling)
- [ ] Configure organization calendar URLs
- [ ] Enable `useScrapedCalendar` for organizations
- [ ] Test manual import via admin UI
- [ ] Monitor logs for first scheduled import (Sunday 2 AM)
- [ ] Verify public UI displays calendar events

## Rollback Plan

If issues arise:
1. Set `useScrapedCalendar=false` for all orgs (via SQL or admin UI)
2. Comment out `@Scheduled` in CalendarImportScheduler (requires restart)
3. (Optional) Drop `organization_calendar_entry` table
4. Rule-based system continues to function normally

## Documentation

- **Architecture**: `ARCHITECTURE_v1.2.1.md` - Comprehensive technical documentation
- **DB Migration**: `DB_MIGRATION_v1.2.1.md` - SQL migration with rollback
- **Copilot Instructions**: `.github/copilot-instructions.md` - Updated with v1.2.1 info

## Future Enhancements (Not in v1.2.1)

1. Async import processing (background jobs)
2. Configurable date ranges per organization
3. Multi-calendar aggregation
4. Smart conflict resolution with merge strategies
5. Import history/audit trail with timestamps
6. Email notifications for import failures
7. Bulk operations in admin UI (enable/disable all)
8. Custom MinyanType mapping rules per organization
9. Support for more calendar formats (iCal, Google Calendar API)
10. Real-time sync via webhooks

## Breaking Changes

**None** - This is a fully backward-compatible addition. Existing functionality remains unchanged.

## Acknowledgments

Built following the coding patterns established in the Teaneck Minyanim codebase:
- Lombok for DI and boilerplate reduction
- Spring Data JPA for persistence
- Thymeleaf for templating
- Bootstrap 4.6 for UI
- Security model with role-based access control

---

## Summary Statistics

- **Commits**: 6 feature commits
- **Files Added**: 14
- **Files Modified**: 5
- **Lines Added**: ~2,500
- **Lines Removed**: ~20
- **Tests**: 23 (all passing)
- **Test Coverage**: URL generation, CSV parsing, normalization, error handling

## Reviewer Notes

Key areas to review:
1. **CalendarImportService** - Core import logic with deduplication
2. **OrgScheduleResolver** - Provider selection logic
3. **ZmanimService** - Integration with calendar provider
4. **AdminController** - New endpoints for import management
5. **DB Schema** - New table and column additions

Questions welcome! This is a substantial feature but maintains backward compatibility throughout.
