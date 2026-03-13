# Claude Code Instructions — Teaneck Minyanim

> The primary architecture reference is `.github/copilot-instructions.md`. This file adds Claude-specific context, known pitfalls, local dev setup, and issue-tracker state. Changes to agent instruction files (CLAUDE.md, copilot-instructions.md) do **not** require a version bump, changelog entry, or PR.

## Local Dev Setup

```bash
# Default shell Java is 17 — project requires 21. Use sdkman:
JAVA_HOME=~/.sdkman/candidates/java/21.0.5-tem ./mvnw compile -q
JAVA_HOME=~/.sdkman/candidates/java/21.0.5-tem ./mvnw spring-boot:run
JAVA_HOME=~/.sdkman/candidates/java/21.0.5-tem ./mvnw test
```

Database: MariaDB at `localhost:3306/minyanim` (root / passw0rd) — must be running for the app to boot.

## Git Workflow

- Branch naming: `fix/<short-description>`, `feat/<short-description>`
- Batching small related fixes into one branch/PR is fine
- Always do a clean compile before committing
- PR body should reference which GitHub issues are resolved (`Closes #N`)
- Current version: **1.7.2** (pom.xml line 13)
- Version rules and CHANGELOG format are in copilot-instructions.md

## Known Pitfalls

### Thymeleaf expression syntax
`@{|...|` is URL expression syntax. The Elvis operator `?:` **does not work** inside it and renders literally in the browser.

- ❌ `th:text="@{|Title - ${siteName} ?: 'Fallback'|}"`  → outputs `Title - Teaneck Minyanim ?: 'Fallback'`
- ✅ `th:text="|Title - ${siteName ?: 'Fallback'}|"`

### System.out.println
Do not use `System.out.println` anywhere. All classes have `@Slf4j`. Use:
- `log.debug(...)` for per-request trace
- `log.info(...)` for startup/materialization milestones
- `log.warn(...)` / `log.error(...)` for problems

### sed on macOS
BSD `sed` backreferences (`\1`) behave differently from GNU sed. Avoid using `sed` for multi-line or complex Java substitutions — use the Edit tool instead.

### PRG pattern
POST handlers that save data should redirect on success (`"redirect:/..."`) rather than returning a view directly, to prevent browser resubmission on refresh.

### CalendarEvent vs Minyan
The pre-v1.4.0 rule-based `Minyan` path still exists but is only used during materialization. The frontend exclusively queries `CalendarEvent` via `EffectiveScheduleService`. Don't add display logic to the old path.

### shouldDisplayEvent() in ZmanimService
Filters events by time window. MAARIV check uses both shekiya and plag — if a MAARIV event has no `dynamicTimeString` (common for imported calendar events), the plag check must fall back to comparing `startTime` vs `Zman.PLAG_HAMINCHA`.

## Frontend Notes

`notification-popup.js` (loaded on homepage) exposes `NotificationPopup.parseMarkdown(text)` for markdown-to-HTML conversion. Use it rather than adding a new parser.

Toast/modal notifications in admin should use in-page UI — no `window.alert()` or `window.confirm()`.

## Issue Tracker State (as of 2026-03-13)

**Just completed (v1.7.2, PR #125):**
#41, #37, #87, #89, #97, #107

**Tier 2 — next up (bugs, medium lift):**
- #117 Deactivation/deletion modals on /account not triggering
- #120 Mobile sidebar broken in admin panel
- #114 Toast/modal styling inconsistent in admin UI
- #118 Timezone autocomplete field appearing on all settings (should be scoped)
- #96 Replace `window.alert`/`window.confirm` popups with in-page notifications

**Tier 3 — enhancements:**
- #99 Auto super admin creation on startup (security gap)
- #90 Clean `/[org-slug]` URLs (currently `/org/[org-slug]`)
- #115 Update legacy Thymeleaf fragment syntax to `~{...}`
- #88 Show rules-based vs calendar flag on org list
- #21 Treat legal holidays as Sundays
- #31 / #7 Selichos and Chol Hamoed handling

**Tier 4 — large projects:**
- #110 / #94 / #113 / #67 Admin UI redesign + org dashboard
- #116 Migrate to AG Grid
- #35 Email handler (SMTP + SES)
- #78 REST API for mobile
- #121 Multi-shul admin
- #43 Clerk auth (discuss scope — major lift, may conflict with #99)

**Needs clarification before starting:**
- #47 Board feature — what exactly is the "board" view?
- #8 Override for days — no description
- #34 Weekly zman review — no description
