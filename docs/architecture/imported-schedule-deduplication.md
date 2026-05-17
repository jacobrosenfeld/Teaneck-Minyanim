# Imported Schedule Deduplication

## Problem

Imported calendar rows are stored first in `organization_calendar_entry`, then mirrored into the materialized `calendar_events` table for API and schedule display.

The duplicate rows reported in issue 236 were not duplicate admin/import rows. They were duplicate materialized `calendar_events` rows with the same imported source reference, for example multiple rows with:

- `organization_id`: same org
- `source`: `IMPORTED`
- `source_ref`: same `import-{organization_calendar_entry.id}`
- same display date/time/type/notes

This is why the admin panel and source import table could look correct while the public API still showed duplicates.

## Root Cause

The materialization/live-sync path indexed existing imported `calendar_events` rows in memory by `sourceRef`:

```java
bySourceRef.put(existing.getSourceRef(), existing);
```

If the database already contained more than one materialized row for the same `sourceRef`, each later row overwrote the earlier map entry. The code would update only one of those rows and leave the rest enabled in `calendar_events`.

Because effective schedules read from all enabled materialized rows, those stale duplicates continued to appear in:

- `/api/v1/organizations/{slug}/schedule`
- `/api/v1/schedule`
- org schedule pages backed by the effective schedule service

The duplicate materialized rows could be introduced by earlier materialization/import behavior, deploy restarts, or manual rematerialization before the materialized table had a source-reference cleanup step. Once present, the old sync code did not remove them.

## Fix

`CalendarMaterializationService` now normalizes imported materialized rows when it builds the `sourceRef` cache:

1. Group existing imported `calendar_events` rows by `sourceRef`.
2. Keep the oldest row for each `sourceRef`.
3. Delete the extra rows before applying the imported-entry upsert.

This makes future startup, scheduled, manual, and live-sync materialization passes clean up existing duplicated materialized rows.

`EffectiveScheduleService` also applies a display-level dedupe after precedence rules. This protects public schedule output if duplicate materialized rows are present before the cleanup runs, or if a similar data issue is introduced later.

The display dedupe key is:

- organization
- date
- start time
- minyan type
- location name
- notes
- dynamic time string
- whatsapp

## Validation

After deploying the fix to dev/staging, `bmob` and `beth-abraham` were compared against production for `2026-05-17` through `2026-05-23`.

Results:

- Production `bmob`: 35 duplicate display groups.
- Dev `bmob`: 0 duplicate display groups.
- Production `beth-abraham`: 1 duplicate display group.
- Dev `beth-abraham`: 0 duplicate display groups.

Both org-specific and combined schedule API endpoints were checked.
