# Changelog

All notable changes to the Teaneck Minyanim project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Manual override workflows (org + super admin)**: Added dedicated override pages for shul admins and super admins, including manual add/delete/toggle flows, start/end date range entry, and guidance that manual overrides do not require a location.
- **Bulk XLSX override import + downloadable templates**: Added XLSX import on override pages with templates tailored for org admins and super admins (including shul-name-based mapping and validated minyan types).
- **Mobile analytics compliance foundation (#146)**: Added first-launch in-app analytics disclosure with persisted consent state (`accepted|declined|unknown`) and platform tracking permission state, with runtime gating that keeps analytics disabled until policy requirements are met.
- **Free mobile analytics stack (#146)**: Integrated PostHog React Native for mobile event telemetry with kill-switch config, consent-gated advertising ID registration, and sanitized event payload handling.
- **Mobile store privacy checklist docs (#146)**: Added `docs/features/mobile-analytics-tracking-compliance.md` to align shipped behavior with App Store Connect and Play Console disclosures.

### Changed
- **Override page UI modernization**: Updated override tables and actions to match the admin design system styling used by calendar-entry management views.
- **Build tooling baseline moved to Java 21**: Added Java 21 enforcement/toolchain setup for local and CI consistency.
- **Calendar entries enable/disable is now live (window-scoped)**: Toggling imported calendar entries now syncs immediately to `calendar_events` (rolling window only), so the live schedule/API updates without rematerialization.
- **Calendar entries toggle UX now preserves filters**: Enable/Disable actions on `/admin/{orgId}/calendar-entries` now update in place via AJAX, keeping active search/date/type/sort state.
- **Calendar entries location updates now preserve filters**: Location changes on `/admin/{orgId}/calendar-entries` now apply in place via AJAX (no full-page reload), with the same toast feedback pattern as enable/disable.
- **Mobile privacy policy disclosure text updated**: The public privacy policy now documents consent-gated mobile analytics, ATT behavior on iOS, and Android disclosure expectations.

### Fixed
- **Dependabot alerts #100 and #103 (CVE-2026-22731, CVE-2026-22733)**: Upgraded Spring Boot parent to `3.5.12` to pick up patched Actuator behavior and close both authentication-bypass advisories.
- **Dependabot alert #98 (CVE-2026-24734)**: Spring Boot upgrade also lifts embedded Tomcat to a patched 10.1.x release outside the vulnerable range.
- **Dependabot alert #102 (CVE-2026-22732)**: Pinned Spring Security to `6.5.9` via Maven property override so transitive `spring-security-web` is upgraded out of the vulnerable range.
- **iOS PostHog release telemetry under ATT denial**: iOS analytics now enables after in-app consent even when ATT is denied/unavailable, advertising ID registration remains restricted to ATT-authorized sessions only, and SDK opt-in/opt-out transitions are now awaited to prevent startup race conditions that could leave capture disabled.
- **Super admin shul picker reliability**: Fixed dropdown/search initialization issues on override pages (including Turbolinks navigation handling) and improved search input behavior.
- **Super admin location scoping**: Location options now correctly scope to the selected shul instead of mixing locations across organizations.
- **Scheduled imports no longer undo manual disabled choices**: Added persistent manual-enable flag on imported entries so weekly sync honors admin enable/disable decisions until a full reimport.
- **Imported materialization drift correction**: Materialization now syncs existing imported `calendar_events` rows (including `enabled`) instead of skipping already-materialized entries.
- **Calendar Entries admin render regression**: Removed fragile per-row `#httpServletRequest` expression from toggle form and switched redirect-state fallback to `Referer`, preventing mixed partial admin render/error-template bleed-through.

## [1.9.2] - 2026-03-20

### Fixed
- **Org page 200 error for orgs with null nusach**: `org.getNusach()` returned null for organizations with no nusach set in the DB, causing a NullPointerException in the Thymeleaf template (`getNusach().displayName()`). Added a null-safe custom getter on `Organization` that falls back to `Nusach.UNSPECIFIED`.
- **Error page returning HTTP 200**: `CustomErrorController` rendered the error view without setting the HTTP status code, so all error pages (404, 500, etc.) were served as 200. Now injects `HttpServletResponse` and calls `response.setStatus(statusCode)`.
- **JS crash on pages without counterUp library**: `custom.js` accessed `window.counterUp.default` unconditionally; guarded with `if (window.counterUp)` to prevent crash on pages that don't load the library.
- **JS crash calling jQuery `.tooltip()` on Bootstrap 5 pages**: Guarded the `$('#back-top').tooltip('hide')` call with `typeof $.fn.tooltip === 'function'` check.

## [1.9.1] - 2026-03-20

### Fixed
- **Locations modal not opening (#214)**: Add/Edit/Delete modals were placed outside `</body>`, making Bootstrap unable to find them. Moved all modals inside the Thymeleaf content fragment.
- **Update organization silently wiping data (#215)**: `updateOrganization` built a fresh entity via `Organization.builder()`, losing `enabled`, `latitude`, `longitude`, and other DB fields not in the form. Now fetches the existing org and mutates only the editable fields. Also fixed a secondary bug in the USER-role branch of `organization()` that put an `Optional<Organization>` (instead of `.get()`) into the model, causing a Thymeleaf rendering failure when the error path was hit.
- **Minyan schedule page blank on empty org (#213)**: The empty-state element was always hidden (`display:none`) and only shown by JS filter/search actions, so a brand-new org with no minyanim showed a blank page. Now uses Thymeleaf to determine initial visibility. Also fixed `no-minyan` CSS class never being applied (was checking `displayTime()=='No Minyan'` but the method returns `""` for NONE type; fixed to use `t.isNone()`), and blank cells now show `—` instead of empty text.

### Changed
- **Add Minyan page modernized**: Replaced legacy Bootstrap layout with the current admin design system — `page-header`, `card-modern`, `btn-modern`, form sections with labels, and the help modal now accessible from a header button. All form field IDs/names and JavaScript hooks are unchanged.
- **Organization page modernized**: Switched outer and accounts cards from `card`/`card-body` to `card-modern`/`card-modern-body`; unified page header to the standard `page-header` pattern; success/error toasts now use `showToast()` instead of a raw inline Bootstrap toast element.

## [1.9.0] - 2026-03-19

### Added
- **Homepage minyan filter bar** (#144): Type pills (All / Shacharis / Mincha / Maariv) and a shul dropdown let users instantly narrow the minyanim list without a page reload. Mincha/Maariv combined services (`MINCHA_MAARIV`) group under the Mincha pill, matching the mobile app behaviour. Both desktop and mobile tables filter in sync.
- **Context-aware empty states**: When the active filter produces no results, a friendly message is shown — e.g. "No more Shacharis minyanim today." or "There are no more minyanim at [Shul] today." — with alternate phrasing for non-today dates.
- **Shul dropdown auto-populated**: Options are generated client-side from the rendered rows, so no server-side changes are needed as shuls are added or removed.

### Changed
- **`minyan-table.js` refactored**: Pagination state exposed via `resetMinyanTable()` so the filter can restore the 10-row load-more view when all filters are cleared.
- **Desktop table ID unchanged** (`minyan-table`); mobile table given distinct ID `minyan-table-mobile` (fixes duplicate-ID bug); both share class `minyan-data-table` for filter targeting.

## [1.8.11] - 2026-03-18

### Fixed
- **Calendar entries table defaults to today onwards** (#196): Page now starts with `startDate = today` rather than showing all historical entries; supports open-ended "from date" queries via new `findFromDateWithClassification` repository method.
- **Sortable column headers** (#196): All major columns (Date, Time, Title, Type, Status) are now clickable to sort client-side — no page reload. Active sort column highlighted with directional arrow icon.
- **Filter bar always visible and functional** (#198): Replaced collapsible filter panel with an always-open flat filter bar; filter/clear buttons are clearly styled and reliably submit.
- **Stat/count pill spacing** (#197): Stat cards and table count pill have consistent padding and `white-space: nowrap` to prevent overflow and layout bleed.
- **Comma-formatted numbers**: All stat card numbers and the table count pill are formatted with locale-aware commas via JS `toLocaleString()`.

### Changed
- **Calendar entries page fully redesigned** (closes #196, #197, #198): Clean flat layout matching the rest of the admin panel — import bar, stats row, filter bar, and modern sortable table with proper badge styling.
- **Master Calendar page redesigned** (#200): Applied same sortable table infrastructure with `data-value` attributes, collapsible filter panel with Clear button, stat cards with comma formatting, and consistent badge/pill styles.
- **Combined enabled + date filter**: `getFilteredEntries` now correctly applies the enabled status filter in-memory when combined with a date range or classification filter.

## [1.8.10] - 2026-03-18

### Changed
- **Calendar Events page redesigned as compact schedule preview**: The org-specific `/admin/{orgId}/calendar-events` page now shows a day-by-day schedule digest instead of a bloated data table.
  - Defaults to current date + 2 weeks (instead of full materialization window)
  - Events grouped by date; each day shows compact service pills (time + type, color-coded)
  - Amber "Calendar Import" badge and left border on days where imports override rules
  - "Today" label on the current date row
  - Week headers inserted at week boundaries
  - Read-only with action links: Manage Minyan Rules, Manage Calendar Entries, Rematerialize
  - Stats show: total days with services, days using calendar import, days using rules

## [1.8.9] - 2026-03-18

### Changed
- **Calendar Events pages now show effective schedule**: Both `/admin/{orgId}/calendar-events` and `/admin/calendar-events/all` now use `EffectiveScheduleService.getEffectiveEventsInRange()` instead of raw repository queries. The pages show only the events that the public actually sees — IMPORTED events take precedence over RULES on days where imports exist.
  - Removed "Enabled/Disabled" status filter and stat card (effective events are always enabled by definition)
  - Removed Status column and Enable/Disable toggle button from the table
- **Master Calendar added to Super Admin sidebar**: `/admin/calendar-events/all` is now accessible directly from the sidebar under the Super Admin section.

## [1.8.8] - 2026-03-18

### Fixed
- **Migrate calendar-events pages to standard admin layout (#110)**: Both `calendar-events.html` and `calendar-events-all.html` now use `layout:decorate="~{admin/layout}"`, ensuring consistent navbar, sidebar, design system CSS, and toast notifications across all admin pages.
  - Replaced standalone full-HTML structure with proper layout fragments (`layout:fragment="content"`, `layout:fragment="styles"`, `layout:fragment="scripts"`)
  - Upgraded `calendar-events-all.html` to use design system CSS variables (was using hardcoded pixel values)
  - Fixed missing `toggleFilters()` JS function — filter collapse toggle now works on both pages
  - Removed orphaned `</th:block>` tag in `calendar-events.html`

## [1.8.7] - 2026-03-18

### Changed
- **Minyan Schedule page redesigned (#94, #110)**: Complete visual overhaul of the admin minyan schedule page (`/admin/{orgId}/minyanim`) to match the project's modern design system.
  - Stats grid cards with per-type color coding (Shacharis blue, Mincha amber, Maariv purple, Selichos green, Megila indigo)
  - Per-minyan-type colored left-border cards and type badges
  - Fixed 7-column weekly schedule grid and 4-column special days grid (replaces broken `auto-fit` grid)
  - Pill-style filter tabs; Selichos/Megila tabs only shown when those types have entries
  - Cleaner card layout with header row, schedule section, and notes area

### Added
- **Calendar sync override notice**: Amber warning banner on the minyan schedule page when `useScrapedCalendar` is active, explaining that imported calendar entries take precedence over the rule-based schedule and linking to the Calendar Entries management page.

## [1.8.6] - 2026-03-18

### Fixed
- **Minyan Schedule page unstyled (#94, #110)**: The admin minyan schedule page (`/admin/{orgId}/minyanim`) was using ~10 wrong CSS custom property names that don't exist in `design-system.css`, causing all custom styles to silently fall back to defaults (no border colors, no backgrounds, no spacing, no radius). Corrected: `--primary-color` → `--color-primary`, `--spacing-N` → `--space-N` (×6), `--bg-secondary` → `--color-gray-100`, `--bg-tertiary` → `--color-gray-200`, `--border-radius` → `--radius-md`.

## [1.8.5] - 2026-03-18

### Fixed
- **Calendar Entries page broken (#67, #110)**: Replaced the broken Tabulator.js-based client-side table with a clean server-side-rendered HTML table. The previous implementation used `new Date("HH:mm:ss")` to format `LocalTime` values (always `Invalid Date` in browsers) and timezone-shifted `LocalDate` strings causing off-by-one date display. Now uses Thymeleaf `#temporals.format()` for all date/time rendering.
- **Filter panel was hidden**: The filter panel (`style="display:none"`) was dead code. It is now a fully functional collapsible panel wired to the existing server-side filter logic already present in the controller.
- **Removed broken Tabulator dependency**: Dropped CDN-loaded Tabulator 6.3.1 from the Calendar Entries page and deleted the associated `calendar-entries-tabulator.js`. The page now uses the project's own `design-system.css` table styles (via `layout:decorate="~{admin/layout}"`).

### Changed
- Calendar Entries page migrated to `layout:decorate="~{admin/layout}"`, giving it the modern navbar, sidebar, design tokens, and toast system consistent with other admin pages.
- Inline location editing now uses a server-rendered `<select>` form (matching the Calendar Events page pattern) instead of Tabulator's cell-click DOM manipulation.

## [1.8.4] - 2026-03-16

### Added
- **Privacy policy page** at `/privacy` — required for App Store submission. Covers location data, push notification tokens, analytics (Google Tag Manager), third-party services (Expo, Mapbox), and user choices. Rendered by the existing Thymeleaf layout with navbar and footer.

## [1.8.3] - 2026-03-16

### Added
- **Super Admin Maintenance panel** at `/admin/super/maintenance` (super admins only, sidebar link under "Super Admin" section).
  - **Reimport All Calendars** — deletes all `OrganizationCalendarEntry` records for every org with a calendar URL and re-fetches/re-classifies from scratch. Fixes stale notes (e.g. "Shkiya:" entries that should have been "Plag:") by running the current classifier pipeline over all data.
  - **Rematerialize All** — manually triggers the full `calendar_events` table rebuild (same job as the weekly Sunday 2 AM run). Run after reimporting calendars to push changes to the live schedule.
  - All confirmation dialogs use Bootstrap modals — no browser `confirm()` popups.

### Fixed
- **Plag annotation now shared between website and API** — extracted `ScheduleEnrichmentService` which both `ZmanimService` (web) and `ScheduleApiController` (API) call. The app previously showed "Shkiya: HH:MM" for maariv minyanim near plag while the website correctly showed "Plag: HH:MM". The shared service strips existing Shkiya/Plag fragments and injects "Plag: HH:MM" at response time for both paths.

## [1.8.2] - 2026-03-15

### Added
- **Organization geocoding (#143)**: Addresses are now automatically geocoded to lat/lng coordinates via the Mapbox Geocoding API when an organization is created or updated. Coordinates are proximity-biased toward Teaneck, NJ for accurate local resolution. The Mapbox token is read from the existing `mapbox.access.token` application setting — no new configuration required.
  - `latitude` and `longitude` columns added to the `organization` table (Hibernate auto-migration)
  - `GeocodingService` handles Mapbox API calls with 5s connect / 10s read timeouts and graceful fallback on failure
  - On update: coordinates are only re-fetched if the address changed or coordinates are missing; otherwise existing values are preserved
  - **"Geocode All" button** on the super-admin Organizations page to backfill coordinates for existing shuls
  - `latitude` and `longitude` exposed in the public API (`/api/v1/organizations`) for mobile map display

## [1.8.1] - 2026-03-15

### Fixed
- **Critical: dynamic zmanim showing wrong times (#141)** — `TimeRule.getTime()` was instantiating a bare `ZmanimHandler()` that fell back to Jerusalem, Israel coordinates. A "Shkiya" minyan therefore computed sunset for Jerusalem (~6 PM Israel time = ~11 AM New York time) instead of Teaneck. Fixed by adding `MinyanTime.resolveLocalTime()` which accepts a properly-configured zmanim supplier from `CalendarMaterializationService`; the supplier uses the injected `ZmanimHandler` bean (Teaneck coordinates). The legacy `TimeRule.getTime()` code path is preserved for non-materialization use cases.

## [1.8.0] - 2026-03-15

### Added
- **Public REST API v1 (#130)**: New versioned REST API at `/api/v1/` for the mobile app and third-party consumers. All endpoints are public (no auth required), return JSON with a consistent `{ data, meta }` wrapper, and support CORS from any origin.

  **Endpoints:**
  - `GET /api/v1/organizations` — list all enabled organizations (id, name, slug, color, nusach, address, website, whatsapp)
  - `GET /api/v1/organizations/{id}` — single organization by ID or slug
  - `GET /api/v1/organizations/{id}/schedule?date=YYYY-MM-DD` — org's effective schedule for a date (max 30-day range via `start`/`end` params)
  - `GET /api/v1/schedule?date=YYYY-MM-DD` — combined schedule across all orgs for a date (max 14-day range via `start`/`end` params); powers the app's "Today" view
  - `GET /api/v1/zmanim?date=YYYY-MM-DD` — all 14 Jewish prayer times for a date (defaults to today)

  **Design decisions:**
  - No `/next` or `/last` endpoints — callers use explicit ISO-8601 date params for predictable, cacheable responses
  - Flat event list sorted by date then `startTime`; org info is embedded in each event to avoid waterfall requests from mobile
  - `meta` on schedule responses includes `windowStart`/`windowEnd` so the app knows the queryable range
  - All times in `HH:mm` format in the application timezone
  - Day-level precedence (IMPORTED overrides RULES) is applied server-side via `EffectiveScheduleService` — same logic as the web frontend
  - MANUAL override (issue #8) is supported in the data model; API will surface it automatically once implemented

- **Notifications API**: `GET /api/v1/notifications` returns all currently active announcements. Supports optional `type=BANNER` or `type=POPUP` filter. Includes `maxDisplays` so the mobile app can mirror the website's "stop showing after N views" behavior.
- **Swagger UI / OpenAPI docs**: Interactive API documentation at `/api/docs` (Swagger UI) and `/api/docs.json` (OpenAPI JSON), powered by springdoc-openapi. All endpoints annotated with `@Operation`, `@Tag`, and `@Parameter`. Human-readable reference at `docs/api/README.md`.
- **API rate limiting**: Per-IP token-bucket rate limiter applies to all `/api/v1/**` requests. Default: 60 req/min/IP. Returns `429 Too Many Requests` with `Retry-After: 60`. Configurable via `api.ratelimit.requests-per-minute`.
- **CORS configuration**: `/api/v1/**` accepts GET and OPTIONS from any origin with a 1-hour preflight cache.

### Fixed
- **Organization slug in CalendarEventAdapter**: `MinyanEvent.organizationSlug` is now correctly populated by the adapter (previously always null, affecting org-page deep links and API responses).

## [1.7.6] - 2026-03-14

### Added
- **Auto super admin creation (#99)**: On first startup, if no super admin account exists, one is created automatically using credentials from `application.properties` (`superadmin.username` / `superadmin.password`). Existing super admins are never overwritten.
- **Schedule type badge on org list (#88)**: The super admin Organizations table now shows a "Schedule Type" badge for each org: **Rules-based** (no calendar URL), **Calendar Import** (has calendar URL), or **Calendar + Scrape** (useScrapedCalendar enabled).

### Fixed
- **Proper 404/error page (#136, #38, #39)**: Replaced the generic error page with a custom `ErrorController` that injects `siteName`, `supportEmail`, and `appVersion` into the error template. The page now shows a friendly, specific message for 404 (Not Found), 403 (Forbidden), and 500 (Server Error) responses with working navbar and footer.
- **Homepage stats (#137)**: The "About" stats section now shows total enabled shuls in the database and total enabled minyanim for the next 7 days (instead of that day's live counts). The "Minyanim" label updated to "Weekly Minyanim".

## [1.7.5] - 2026-03-13

### Added
- **WhatsApp on org page (#45)**: Added an org-level `whatsapp` field to the `Organization` model; a WhatsApp button now appears in the org header card (alongside "Get Directions" / "Visit the website") when the organization has a WhatsApp group link set. Admins can set the link on the organization settings page.
- **Clean slug URLs (#90)**: Organizations can now be accessed via short `/{slug}` URLs (e.g., `/keter-torah`) in addition to `/org/{slug}`. Added `/{slug}`, `/{slug}/next`, and `/{slug}/last` routes to `ZmanimController`; updated Spring Security to permit these public routes. The `/org/{slug}` routes remain for backward compatibility.

### Changed
- **Thymeleaf fragment notation (#115)**: Updated all active templates to use the current `~{...}` fragment expression syntax instead of the deprecated bare-string form (e.g., `th:include="~{frontnavbar}"` instead of `th:include="frontnavbar"`). Affects `error.html`, `subscription.html`, `homepage.html`, `org.html`, `admin/login.html`, `admin/layout.html`, `dashboard.html`, `admin/calendar-events-all.html`, `admin/calendar-events.html`, and `admin/calendar-entries.html`.

## [1.7.4] - 2026-03-13

### Fixed
- **Account modals (#117)**: Added `layout:fragment="modals"` slot to admin layout so the disable/enable/delete account modals on `/account` are rendered in the page instead of being silently dropped by Thymeleaf Layout Dialect
- **Mobile sidebar (#120)**: Removed conflicting `sb-sidenav-toggled` toggle logic from `sidebar.js`; toggle is handled by the inline script in `layout.html` using the current `.open`/`.collapsed` CSS approach
- **Timezone autocomplete (#118)**: Timezone autocomplete in the settings modal no longer activates for non-timezone fields; event listeners now check a `data-is-timezone` attribute set by `prepareEditModal` before showing the dropdown
- **Notification browser popups (#96)**: Replaced `confirm()` browser dialogs for toggle and delete actions on the Notifications page with in-page Bootstrap modals
- **Toast styling (#114)**: Notifications page now uses the global `window.showToast` from the admin layout instead of a local function with mismatched CSS class names

## [1.7.3] - 2026-03-13

### Security
- Upgraded `jackson-core` to 2.21.1 (was 2.19.4) — resolves high-severity DoS vulnerability (Dependabot alert)
- Upgraded `logback-core` to 1.5.25 (was 1.5.22) — resolves low-severity class instantiation vulnerability (Dependabot alert)

## [1.7.2] - 2026-03-13

### Fixed
- **Branding (#107)**: Fixed Thymeleaf admin page titles and descriptions displaying literal `?: 'Minyanim Platform'` text by switching from `@{|...|` (URL expression) to `|...|` (literal template) syntax so the Elvis operator evaluates correctly
- **Log cleanup (#89)**: Replaced all `System.out.println` debug statements in `AdminController` with `log.debug`, and downgraded verbose per-request `log.info` calls in `ZmanimService` to `log.debug` to reduce log noise
- **Banner markdown (#87)**: Homepage announcement banners now render markdown formatting (bold, italic, links, headers, lists) using the existing `NotificationPopup.parseMarkdown` parser
- **PRG pattern (#97)**: `updateMinyan` POST handler now redirects to the minyan view page after a successful save, preventing form resubmission on browser refresh
- **Plag maariv display (#41)**: Maariv minyanim with a start time at or after Plag Hamincha now display correctly on both the homepage and org pages; previously, calendar-imported early-Maariv events were filtered out because they lack a `dynamicTimeString` containing "plag". Shekiya-based events are now excluded from plag annotation to prevent showing both shekiya and plag times simultaneously
- **Login redirect (#111)**: After login, users are no longer redirected to `/admin/login.js?continue`; Spring Security's request cache is now configured to ignore static asset requests (`.js`, `.css`, `.ico`, `.png`, `.svg`, `.woff`), and permit patterns updated to use PathPatternParser-compatible `/**/*.js` / `/**/*.css` globs

### Changed
- **Sort organizations (#37)**: The super admin Organizations page now lists organizations in alphabetical order (case-insensitive)

## [1.7.1] - 2026-02-24

### Changed

#### Java Runtime Upgrade to LTS Version 21
- **Runtime Environment**:
  - Upgraded project target Java version from 17 to 21 LTS
  - Updated `pom.xml` to specify `java.version=21`
  - Applied OpenRewrite recipe `org.openrewrite.java.migrate.UpgradeToJava21` for code compatibility
  - All source code refactored for Java 21 compatibility
  - Spring Boot version remains at 3.5.9 (compatible with Java 21)

### Fixed
- Java 21 compatibility: all deprecated Java 17 patterns modernized

### Security
- Java 21 provides latest security patches and CVE fixes
- No known vulnerabilities detected in updated dependencies

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
