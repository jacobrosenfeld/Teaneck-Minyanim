-- Migration SQL for v1.10.13: Track imported calendar entries removed from source feeds.
-- Hibernate ddl-auto=update can add these columns automatically; run manually where schema changes are controlled.

ALTER TABLE organization_calendar_entry
ADD COLUMN source_deleted BOOLEAN NOT NULL DEFAULT FALSE
COMMENT 'True when a successful source-calendar import covering this date no longer contains the entry';

ALTER TABLE organization_calendar_entry
ADD COLUMN source_deleted_at TIMESTAMP NULL
COMMENT 'Timestamp when the entry was first marked missing from the source calendar';
