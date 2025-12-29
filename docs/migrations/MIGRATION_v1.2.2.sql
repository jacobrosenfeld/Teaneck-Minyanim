-- Migration SQL for v1.2.2: Add Classification and Notes to OrganizationCalendarEntry
-- This script adds the new fields required for the enhanced import management feature.
-- 
-- Run this migration if you have an existing database that needs updating.
-- If you're using Hibernate auto-update (spring.jpa.hibernate.ddl-auto=update), 
-- these changes will be applied automatically.

-- Add classification enum column
ALTER TABLE organization_calendar_entry 
ADD COLUMN classification VARCHAR(20) NULL 
COMMENT 'Classification of entry: MINYAN, MINCHA_MAARIV, NON_MINYAN, or OTHER';

-- Add classification reason column
ALTER TABLE organization_calendar_entry 
ADD COLUMN classification_reason TEXT NULL 
COMMENT 'Explanation of why entry was classified as such';

-- Add notes column for additional information like Shkiya time
ALTER TABLE organization_calendar_entry 
ADD COLUMN notes TEXT NULL 
COMMENT 'Additional notes such as Shkiya time for Mincha/Maariv entries';

-- Add manual edit tracking columns
ALTER TABLE organization_calendar_entry
ADD COLUMN location_manually_edited BOOLEAN NOT NULL DEFAULT FALSE
COMMENT 'Flag to indicate if location was manually edited';

ALTER TABLE organization_calendar_entry
ADD COLUMN manually_edited_by VARCHAR(255) NULL
COMMENT 'User who last manually edited this entry';

ALTER TABLE organization_calendar_entry
ADD COLUMN manually_edited_at TIMESTAMP NULL
COMMENT 'Timestamp of last manual edit';

-- Create index on classification for faster filtering
CREATE INDEX idx_org_classification ON organization_calendar_entry(organization_id, classification);

-- Create index on enabled + classification for faster queries
CREATE INDEX idx_org_enabled_classification ON organization_calendar_entry(organization_id, enabled, classification);

-- Verify the changes
-- SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE 
-- FROM INFORMATION_SCHEMA.COLUMNS 
-- WHERE TABLE_NAME = 'organization_calendar_entry' 
-- AND COLUMN_NAME IN ('classification', 'classification_reason', 'notes', 
--                      'location_manually_edited', 'manually_edited_by', 'manually_edited_at');

-- Optional: Update existing entries to classify them retroactively
-- This is not required as entries will be reclassified on next import.
-- However, if you want to classify existing entries without re-importing:
-- 
-- UPDATE organization_calendar_entry SET
--   classification = CASE
--     WHEN title REGEXP 'mincha.*(and|&|/).*(maariv|ma\'?ariv)' THEN 'MINCHA_MAARIV'
--     WHEN title REGEXP '(daf yomi|shiur|lecture|class|learning)' THEN 'NON_MINYAN'
--     WHEN title REGEXP '(shacharis|shacharit|mincha|maariv|selichos)' THEN 'MINYAN'
--     ELSE 'OTHER'
--   END,
--   classification_reason = CASE
--     WHEN title REGEXP 'mincha.*(and|&|/).*(maariv|ma\'?ariv)' THEN 'Matched combined Mincha/Maariv pattern'
--     WHEN title REGEXP '(daf yomi|shiur|lecture|class|learning)' THEN 'Matched non-minyan pattern'
--     WHEN title REGEXP '(shacharis|shacharit|mincha|maariv|selichos)' THEN 'Matched minyan pattern'
--     ELSE 'No specific pattern matched'
--   END
-- WHERE classification IS NULL;
