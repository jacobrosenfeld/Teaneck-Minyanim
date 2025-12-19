# Teaneck Minyanim v1.2 - Files Changed Summary

## New Files Created

### Core Application Files

1. **src/main/java/com/tbdev/teaneckminyanim/model/OrganizationCalendarEntry.java**
   - JPA entity for storing scraped calendar entries
   - Contains all required fields with indexes for performance
   - Includes @PrePersist and @PreUpdate lifecycle hooks

2. **src/main/java/com/tbdev/teaneckminyanim/repo/OrganizationCalendarEntryRepository.java**
   - JPA repository for OrganizationCalendarEntry
   - Custom query methods for date-based and enabled filtering

### Calendar Scraping Package (calendar/)

3. **src/main/java/com/tbdev/teaneckminyanim/calendar/ScrapedCalendarEntry.java**
   - DTO for scraped calendar data before persistence

4. **src/main/java/com/tbdev/teaneckminyanim/calendar/CalendarSyncResult.java**
   - DTO for sync operation results and feedback

5. **src/main/java/com/tbdev/teaneckminyanim/calendar/CalendarNormalizer.java**
   - Service for normalizing and cleaning scraped data
   - Title normalization, time parsing, type inference
   - Fingerprint generation for deduplication

6. **src/main/java/com/tbdev/teaneckminyanim/calendar/CalendarScraper.java**
   - HTML parsing service using Jsoup
   - Supports multiple calendar formats
   - Extracts dates, times, and titles from structured HTML

7. **src/main/java/com/tbdev/teaneckminyanim/calendar/CalendarSyncService.java**
   - Orchestrates calendar scraping operations
   - Scheduled weekly sync (Sundays 2 AM)
   - Manual sync API
   - Deduplication and cleanup logic

### Schedule Provider Architecture (schedule/)

8. **src/main/java/com/tbdev/teaneckminyanim/schedule/OrgScheduleProvider.java**
   - Interface for schedule data providers

9. **src/main/java/com/tbdev/teaneckminyanim/schedule/CalendarScrapeProvider.java**
   - Implementation that returns date-based scraped entries

10. **src/main/java/com/tbdev/teaneckminyanim/schedule/RuleBasedProvider.java**
    - Implementation that wraps existing rule-based schedule logic

11. **src/main/java/com/tbdev/teaneckminyanim/schedule/OrgScheduleResolver.java**
    - Service that chooses appropriate provider for each org

### Admin UI Templates

12. **src/main/resources/templates/admin/calendar-entries.html**
    - Admin page for viewing and managing scraped entries
    - Includes filters, search, enable/disable toggles

### Documentation

13. **ARCHITECTURE_V1.2.md**
    - Comprehensive architecture documentation
    - Component descriptions, data flow, security considerations

14. **migration_v1.2.sql**
    - SQL migration script for database schema changes
    - Includes new table and column additions

### Tests

15. **src/test/java/com/tbdev/teaneckminyanim/calendar/CalendarNormalizerTest.java**
    - Unit tests for CalendarNormalizer
    - Tests for title normalization, time parsing, type inference, fingerprinting

## Modified Files

### Application Configuration

16. **pom.xml**
    - Version updated from 1.1.0-SNAPSHOT to 1.2.0-SNAPSHOT
    - Added Jsoup dependency (1.17.2) for HTML parsing

17. **src/main/java/com/tbdev/teaneckminyanim/TeaneckMinyanimApplication.java**
    - Added @EnableScheduling annotation for scheduled tasks

### Data Models

18. **src/main/java/com/tbdev/teaneckminyanim/model/Organization.java**
    - Added `calendar` field (String) for calendar URL
    - Added `useScrapedCalendar` field (Boolean) for feature toggle

### Controllers

19. **src/main/java/com/tbdev/teaneckminyanim/controllers/AdminController.java**
    - Added logger field
    - Added dependencies: CalendarSyncService, OrganizationCalendarEntryRepository
    - Updated `updateOrganization()` to handle new fields (calendar, useScrapedCalendar)
    - Added `syncCalendar()` endpoint - POST /admin/{orgId}/sync-calendar
    - Added `calendarEntries()` endpoint - GET /admin/{orgId}/calendar-entries
    - Added `toggleCalendarEntry()` endpoint - POST /admin/{orgId}/calendar-entries/{entryId}/toggle

### Admin Templates

20. **src/main/resources/templates/admin/organization.html**
    - Added calendar URL input field
    - Added "Use Scraped Calendar" checkbox
    - Added "Refresh Zmanim Sync" button
    - Added "Manage Calendar Entries" link

## Summary Statistics

- **New Java Files:** 11
- **New Template Files:** 1
- **New Documentation Files:** 2
- **New Test Files:** 1
- **Modified Java Files:** 4
- **Modified Template Files:** 1
- **Modified Configuration Files:** 1

**Total Files Changed:** 21

## Database Changes

### New Tables
- `organization_calendar_entry` (with 3 indexes)

### Modified Tables
- `organization` (added 2 columns)

## External Dependencies Added
- Jsoup 1.17.2 (HTML parsing library)

## Lines of Code Added (Approximate)
- Java Code: ~1,400 lines
- HTML/Template: ~150 lines
- SQL: ~50 lines
- Documentation: ~350 lines
- Tests: ~130 lines

**Total: ~2,080 lines**
