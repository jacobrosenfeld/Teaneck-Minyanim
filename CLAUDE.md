# Teaneck Minyanim - AI Coding Agent Instructions

## Architecture Overview
This is a Spring Boot application that displays Jewish prayer services (minyanim) and religious times (zmanim) for Teaneck, NJ. The system automatically calculates service times based on the Jewish calendar and location coordinates, and supports importing events from external calendars with intelligent classification.

**Key Data Flow (v1.4.0 - Materialized Calendar Architecture):**
1. `CalendarMaterializationService` generates calendar_events table from rule-based Minyan entities and imported OrganizationCalendarEntry records
2. **Materialized Calendar Events**: `CalendarEvent` entities are the single source of truth, pre-computed for rolling 11-week window (3 past, 8 future)
3. **Day-Level Precedence**: Imported events suppress all rules for that org+date (applied by `EffectiveScheduleService`)
4. **Frontend Queries**: `ZmanimService` queries materialized calendar_events via `EffectiveScheduleService`, converts to `MinyanEvent` via `CalendarEventAdapter`
5. **Scheduled Import + Materialization** (v1.21.0): The calendar import runs on the cron saved in Settings (`calendar.import.cron`, default Sunday 2 AM, site timezone), and materialization runs right after each scheduled import. Materialization deletes and rebuilds RULES events, keeping IMPORTED/MANUAL ones. It also runs on startup.
6. **Admin UI**: Complete calendar management interface for org admins and super admins with Backpack for Laravel-inspired design

**Legacy Data Flow (pre-v1.4.0 - DEPRECATED):**
1. `ZmanimService` calculated times using Kosherjava library
2. Dual code paths: rule-based Minyan computation OR calendar import provider
3. `Schedule` class wrapped rule-based schedules into `MinyanTime` objects
4. `Organization` owns multiple `Minyan` instances and calendar entries; each `Minyan` links to a `Location`

## REST API (public v1.8.0, Admin API v1.22.0)

### Overview
Versioned JSON API at `/api/v1/` powering the mobile app and third-party consumers.
- **Interactive docs (Scalar):** `/api/docs` — rendered by `ScalarDocsController` from CDN, spec at `/api/docs.json`
- **OpenAPI JSON:** `/api/docs.json`
- **Human-readable reference:** `docs/api/README.md`
- **Auth:** Public reads need none. Admin endpoints need `Authorization: Bearer <token>`; see "Admin API authentication" below.
- **Rate limits (per IP, `ApiRateLimitFilter`):**

  | Requests | Limit |
  |---|---|
  | Reads | 60/min |
  | `/api/v1/auth/**` | 10/min |
  | Other writes | 30/min |
  | Feedback | 5/min |

  The limiter runs inside the API security chain, before bearer auth.
- **CORS:** `GET`/`POST`/`OPTIONS` are allowed from any origin on `/api/v1/**`, without credentials, so PATCH and DELETE can't be called cross-origin from a browser.

### Endpoints
| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/organizations` | List all enabled orgs |
| GET | `/api/v1/organizations/{id}` | Org by ID or slug |
| GET | `/api/v1/organizations/{id}/schedule` | Per-org schedule (max 30-day range) |
| GET | `/api/v1/schedule` | Combined all-org schedule (max 14-day range) |
| GET | `/api/v1/zmanim` | 14 halachic times for a date |
| GET | `/api/v1/notifications` | Active announcements/banners (`?includeInactive=true` is the admin list: super admin + `notifications:write`) |

**Admin API (v1.22.0; Bearer token required unless marked public):**

| Method | Path | Scope | Description |
|---|---|---|---|
| POST | `/api/v1/auth/magic-link/request` | public | Email an app sign-in link (`/app/auth/magic-link?token=`) |
| POST | `/api/v1/auth/magic-link/exchange` | public | Swap a one-time app link token for access + refresh tokens |
| POST | `/api/v1/auth/passkey/options` | public | Native passkey challenge (`{challengeId, publicKey}`) |
| POST | `/api/v1/auth/passkey/verify` | public | Verify the native passkey assertion and issue tokens |
| POST | `/api/v1/auth/refresh` | public (refresh token) | Rotate the refresh token and issue a new pair |
| POST | `/api/v1/auth/logout` | public (refresh token) | Revoke the session family |
| GET | `/api/v1/me` | any token | Current user, shuls, token kind, effective scopes |
| GET | `/api/v1/organizations/{id}/overrides` | `schedule:read` | MANUAL overrides in a date range (max 62 days) |
| POST | `/api/v1/organizations/{id}/overrides` | `overrides:write` | Create overrides (date range) |
| PATCH / DELETE | `/api/v1/organizations/{id}/overrides/{eventId}` | `overrides:write` | Edit, toggle or delete one MANUAL override (409 for RULES/IMPORTED) |
| GET | `/api/v1/organizations/{id}/minyanim[/{minyanId}]` | `schedule:read` | Admin view of minyanim, including disabled ones and the 11-slot schedule |
| PATCH | `/api/v1/organizations/{id}/minyanim/{minyanId}` | `minyanim:write` | Enable or disable, partial schedule, notes, location, nusach |
| PATCH | `/api/v1/organizations/{id}` | `shul:write` | Edit the shul profile |
| POST / PATCH / DELETE | `/api/v1/notifications[/{id}]` | `notifications:write` + super admin | Manage site-wide notifications |

The universal-link association files, `/.well-known/apple-app-site-association` and `/.well-known/assetlinks.json`, are served by `WellKnownController` outside `/api`. They return 404 until `APP_APPLE_TEAM_ID`, `APP_IOS_BUNDLE_ID`, `APP_ANDROID_PACKAGE` and `APP_ANDROID_SHA256` are set.

### Admin API authentication (v1.22.0)
- **One auth layer.** A stateless second `SecurityFilterChain` for `/api/v1/**` (`WebSecurityConfiguration.apiFilterChain`) never reads the HTTP session, so browser cookies can't authorize API calls, which is why CSRF is off there.
- **Bearer filter.** `BearerTokenAuthenticationFilter` resolves `Authorization: Bearer`:
  - `tnm_…` is a personal API key (`ApiKey`, `ApiKeyService`).
  - `tnmapp_…` is a mobile access token (`AppSession`, `AppTokenService`; 15-minute TTL). Refresh tokens are `tnmrt_…` (30 days, rotate on every use; reuse revokes the session family).
  - All of them are stored only as SHA-256 hashes. Raw values are never logged or audited.
- **Principal.** Both kinds of token become an `ApiAuthentication` named by the username, so `TNMUserService.getCurrentUser()`, `isSuperAdmin()` and `canAccessOrganization()` work unchanged. `canAccessOrganization` is also narrowed by a key's shul restriction.
- **Authorizing in API controllers.** Call `ApiAuthorizationService.require(ApiScope, orgId)`, `requireSuperAdmin(scope)` or `requireAuthenticated()`. Effective permission = the key's scopes ∩ what the owner can do right now. App tokens and "same as owner" keys hold every scope.
- **Scopes (`ApiScope`):** `schedule:read`, `overrides:write`, `minyanim:write`, `notifications:write`, `shul:write`.
- **Errors.** Throw `ApiException`; `ApiExceptionHandler` renders the envelope.
  - Codes: `VALIDATION_ERROR`, `UNAUTHENTICATED`, `INVALID_TOKEN`, `FORBIDDEN`, `INSUFFICIENT_SCOPE`, `NOT_FOUND`, `NOT_MANUAL_OVERRIDE`, `RATE_LIMITED`, `INTERNAL_ERROR`.
  - The handler does not map `IllegalArgumentException`. Catch it and rethrow `ApiException.validation(...)`.
- **API keys.** Only super admins create and revoke them, on `/admin/api-keys` (`ApiKeyAdminController`). A key is shown once, rendered straight from the POST response, never via flash or session. API keys can't manage API keys.
- **Revocation.** Disabling a user revokes all of their tokens and keys (`TNMUserService.onAccountDisabled`), and deleting a user removes them (`onAccountDeleted`).
- **Audit.** Every token issue, use and rejection goes through `AuthAuditService`.
- **Mobile sign-in.**
  - App magic links use token purpose `APP_LOGIN`; web and app links can't be used in place of each other.
  - Native passkeys reuse the shared `WebAuthnRelyingPartyOperations` bean, with single-use DB challenges (`ApiWebAuthnChallenge`). Android passkey origins come from `APP_ANDROID_SHA256`.

### MANDATORY: Admin panel ↔ API parity
Every admin action must be reachable from both the admin panel and the Admin API through the same code:
1. **One service method per action.** It validates, authorizes (`canAccessOrganization` / `isSuperAdmin`), writes and syncs. The MVC controller and the API controller are thin wrappers that only parse input and map errors.
   - Examples: `ManualOverrideService`, `MinyanAdminService`, `ShulProfileService`, the admin methods of `NotificationService`.
2. **The same `@AdminAction("area.verb")` tag** (`security/AdminAction`) goes on the admin handler and on its `/api/v1` endpoint. Every `/admin/**` POST/PUT/PATCH/DELETE handler must carry one.
3. **The API endpoint** gets full Swagger docs, `@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)` and a section in `docs/api/README.md`.
4. **`AdminApiParityTest` fails CI** if an admin write handler has no tag, or a tag has no API endpoint and isn't in `src/test/resources/admin-api-parity-allowlist.txt`. Allowlist entries are `pending` (not built yet) or `by-design` (e.g. API-key management is browser-only), each with a reason.
   - When you add an admin action, add its API endpoint, or a `pending` entry with a reason.
   - When you cover a pending action, remove its allowlist line; the test fails on stale entries.

### Key files
- `src/main/java/com/tbdev/teaneckminyanim/api/` — all API controllers and `OpenApiConfig`
- `src/main/java/com/tbdev/teaneckminyanim/api/admin/` — Admin API controllers (DTOs in `api/dto/admin/`, auth DTOs in `api/dto/auth/`)
- `src/main/java/com/tbdev/teaneckminyanim/service/auth/` — `ApiKeyService`, `AppTokenService`, `ApiAuthorizationService`, `NativePasskeyService`, `MagicLinkService`
- `src/main/java/com/tbdev/teaneckminyanim/api/dto/` — public DTOs (never expose JPA entities)
- `src/main/java/com/tbdev/teaneckminyanim/api/ApiRateLimitFilter.java` — per-IP rate limiting
- `docs/api/README.md` — human-readable API reference

### MANDATORY: Updating the API
**Every time you add, remove, or change an API endpoint or response shape:**
1. Update the `@Operation`, `@Parameter`, and `@Tag` Swagger annotations in the relevant controller
2. Update `docs/api/README.md` with the change
3. If the change is breaking (field removed, renamed, semantics changed) → bump to `/api/v2/`; non-breaking additions can stay in `/v1/`
4. Update `CLAUDE.md` endpoint table above if adding/removing routes

**Never expose JPA entity classes directly** — always create or reuse a DTO in `api/dto/`.

### Response format
All responses use `ApiResponse<T>` with `{ data, meta, error }`. See `docs/api/README.md` for full reference.

## Critical Components & Patterns

### Materialized Calendar System (v1.4.0)
- **CalendarEvent** entity: Single source of truth for all minyanim, stored in `calendar_events` table with fields: id, organization_id, date, minyan_type, start_time, source (IMPORTED/RULES/MANUAL), source_ref, enabled, notes, location_id, nusach, manually_edited, edited_by, edited_at
- **EventSource** enum: IMPORTED (from calendar import), RULES (generated from Minyan entities), MANUAL (future feature for admin overrides)
- **CalendarEventRepository**: JPA repository with 20+ query methods including precedence logic, date range filtering, organization filtering
- **CalendarMaterializationService**: Generates calendar_events from Minyan entities and OrganizationCalendarEntry records
  - Delete + rebuild strategy for RULES events (preserves IMPORTED/MANUAL)
  - Rolling 11-week window (3 past, 8 future weeks)
  - Day-level precedence: if any imported events exist for org+date, suppress all rules for that day
- **CalendarMaterializationScheduler**: runs on application startup and from the admin "rematerialize" triggers. The weekly run comes from `CalendarImportScheduler`, which runs on the configurable import cron and materializes after each import (v1.21.0).
- **Configurable schedules** (v1.21.0): `service/schedule/CronSchedule` (cron ↔ day/time picker, validation, minimum interval of 1 hour) and `ScheduledCronJob` (reschedule on save, fallback). Both the calendar import and the weekly review email use them. The UI is the shared fragment `templates/admin/fragments/schedule-picker.html` with `static/admin/schedule-picker.{js,css}`.
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
- **AdminController**: large controller (~2700 lines) for org/minyan/location/account CRUD and schedule editing. Minyan enable/disable/update and shul profile edits now delegate to `MinyanAdminService` / `ShulProfileService`; keep new logic in services, not here
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
- **Confirmations and modals** (v1.21.0): Never use native `confirm()`, `alert()` or `prompt()`, or hand-built confirmation modals. Use the shared dialog that `admin/layout.html` includes once:
  - Buttons: `data-confirm`, `data-confirm-title`, `data-confirm-message`, `data-confirm-severity` (danger/warning/primary). They submit the closest form, or a `data-confirm-form` selector.
  - Script/AJAX actions: `AdminConfirm.ask({...}, onConfirm)`.
  - Form modals: use the shared `.admin-modal` style from `static/admin/admin-modal.css`.
  - `AdminPopupAuditTest` fails the build when this is broken.
- **Wording**: User-facing admin text says "Shul"/"Shuls", never "Organization". Code, routes, field names and the API keep `organization`.
- **Redirects**: Any user-supplied `rd`/return path must go through `AdminController.safeAdminRedirect` (same-site `/admin` paths only).
- **Responsive Design**: Mobile-first, collapsible sidebar, horizontal-scrolling tables

### Security
- **WebSecurityConfiguration**: Spring Security setup
- **Encrypter**: Password hashing for `TNMUser`
- **Sessions** (v1.21.0): HTTP sessions are stored in the database with Spring Session JDBC (`SPRING_SESSION` tables, created automatically), so redeploys don't sign admins out. Session attributes must be `Serializable`. The idle timeout is `SESSION_TIMEOUT` (default 7d), and the cookie's Secure flag is set by `SESSION_COOKIE_SECURE`.
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

## Branch & Deployment Strategy

| Branch | Auto-deploy | Purpose |
|--------|-------------|---------|
| `main` | Yes → production | Stable releases only. Changes reach `main` only through a release PR from `dev`. |
| `dev`  | Yes → dev site | Integration branch. Feature PRs target `dev` and are merged here once tested and approved. |
| `test/*` | No (deploy manually) | Throwaway combined branches for testing several open PRs together. Never merged. |

**Feature flow (test each PR on dev before merging):**
1. Branch from `dev` and open the feature PR against **`dev`**.
2. To test a PR live before merging, run the **Deploy to dev** workflow manually (Actions → Deploy to dev → Run workflow), pick the PR's branch in **Use workflow from**, and leave the text box blank. The dev site then runs that branch, and nothing is merged. For a branch created before this workflow had a manual trigger (it won't appear in the dropdown or will fail), keep **Use workflow from** on `main` and type the branch, tag, or SHA in the text box instead.
3. To test several open PRs together, create `test/<name>` from `dev`, merge the PR branches into it, and deploy that ref. Fix problems on the original feature branch, then rebuild the `test/*` branch. Delete it when done.
4. Review feedback goes on the open PR, and fixes are pushed to the same branch. Merge into `dev` once it passes on dev and is approved. Merging to `dev` auto-deploys it.

**Release flow (`dev` → `main`):**
1. When `dev` holds a tested set of changes, commit the version bump (`pom.xml`) and the CHANGELOG entry on `dev`. Move `[Unreleased]` into a dated version section.
2. Open a release PR from `dev` to `main`. GitHub only auto-closes issues when a PR merges into `main`, so list every `Fixes #N` from the included feature PRs in the release PR body.
3. Merging the release PR deploys to production.
4. Feature PRs merged to `dev` must not bump the version or edit the CHANGELOG independently. Put a suggested CHANGELOG entry in the PR description instead, and the release commit collects them. This avoids conflicts between parallel PRs.

**Caveats:** dev is a single server, so only one ref is live at a time, and the most recent deploy wins (including an automatic deploy from a push to `dev`). The dev database is shared and Hibernate `ddl-auto=update` keeps any columns or tables a branch added after you switch away. That's harmless for additive changes, but avoid deploying branches with destructive schema changes to dev without a plan.

## Important Context
- **Deprecated Code**: `TimeRule` contains TODOs about deprecated Java Date methods
- **Hebrew Calendar Logic**: Heavy reliance on Kosherjava library; Teaneck coordinates are hardcoded (not configurable)
- **REST API**: The website itself uses HTML views via `ModelAndView` and form submissions; the JSON API under `/api/v1/` serves the mobile app and integrations (see the REST API section)
- **Credentials in Code**: Database password and hardcoded coordinates in properties - address before production deployment

## Code Style
- Uses Lombok `@RequiredArgsConstructor` for dependency injection
- Service classes use `@Slf4j` for logging
- Repositories are Spring Data JPA
- Form validation is minimal; relies on browser validation
- Schedule times stored as strings ("HH:MM" or "NETZ+5") - parsed at runtime

## Privacy Policy

The privacy policy lives at `src/main/resources/templates/privacy.html` and is served at `https://teaneckminyanim.com/privacy`. It is linked from the iOS App Store listing and must stay accurate.

**Review and update the privacy policy whenever a change involves any of the following:**
- New data collected from users (location, notifications, analytics, personal info, device identifiers)
- New third-party services integrated (analytics, maps, crash reporting, payment, authentication, etc.)
- Changes to how existing data is used, stored, or shared
- New API endpoints that accept or return user-identifiable data
- Push notification changes (new triggers, new data passed in payloads)
- Any backend feature that logs, persists, or transmits user behaviour

**What to update:** Edit the relevant section(s) in `privacy.html` (plain language, matching the existing style) and update the "last updated" date at the bottom of the page. No version bump or CHANGELOG entry is needed for privacy policy text-only edits.

## Version Management
- **Increment the version number once per release-intent change set**
  - Use semantic versioning: patch (`x.x.1`) for UI updates/fixes, minor (`x.1.0`) for new features, major (`1.x.0`) for breaking changes
  - Update the `pom.xml` version field (located at line 13: `<version>1.1.0-SNAPSHOT</version>`)
  - Add a corresponding entry to `CHANGELOG.md`:
    - **Patch versions (`x.x.1`)**: Short, 2-line summary of changes under appropriate subsections (Added/Changed/Fixed/Removed)
    - **Minor versions (`x.1.0`)**: Longer entries with detailed feature descriptions, organized by subsections (Added/Changed/Fixed/Deprecated/Removed/Security)
    - **Major versions (`1.x.0`)**: Comprehensive entries documenting breaking changes and migration paths, with detailed explanations of impacts
- **Do not bump again for immediate regression fixes to unreleased work**
  - If a bug-fix commit only corrects behavior introduced by the current unreleased change set, keep the existing version and continue updating `[Unreleased]`.
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
