-- Teaneck Minyanim v1.2 Database Migration
-- This script adds support for calendar scraping feature

-- Add new columns to organization table
ALTER TABLE organization 
    ADD COLUMN IF NOT EXISTS calendar VARCHAR(2000) COMMENT 'URL to organizations online calendar',
    ADD COLUMN IF NOT EXISTS use_scraped_calendar BOOLEAN DEFAULT FALSE COMMENT 'Whether to use scraped calendar data instead of rule-based schedules';

-- Create new table for scraped calendar entries
CREATE TABLE IF NOT EXISTS organization_calendar_entry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Unique identifier for calendar entry',
    organization_id VARCHAR(255) NOT NULL COMMENT 'FK to organization table',
    date DATE NOT NULL COMMENT 'Date of the minyan',
    title VARCHAR(500) NOT NULL COMMENT 'Original title/label from calendar',
    type VARCHAR(50) COMMENT 'Inferred minyan type (Shacharis/Mincha/Maariv/etc)',
    time TIME NOT NULL COMMENT 'Time of the minyan',
    raw_text TEXT COMMENT 'Original scraped text for reference',
    source_url VARCHAR(2000) COMMENT 'URL where data was scraped from',
    fingerprint VARCHAR(64) NOT NULL UNIQUE COMMENT 'SHA-256 hash for deduplication',
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Whether entry is active (allows disabling duplicates)',
    scraped_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'When entry was first scraped',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'When entry was last updated',
    dedupe_reason VARCHAR(500) COMMENT 'Reason why entry was auto-disabled (if applicable)',
    
    INDEX idx_org_date (organization_id, date) COMMENT 'Fast lookup by org and date',
    INDEX idx_org_date_enabled (organization_id, date, enabled) COMMENT 'Fast enabled entries lookup',
    INDEX idx_fingerprint (fingerprint) COMMENT 'Deduplication index',
    
    CONSTRAINT fk_calendar_entry_org FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Stores scraped minyan times from shul calendars';

-- Verify migration
SELECT 'Migration completed successfully' AS status;
SELECT COUNT(*) AS calendar_entry_count FROM organization_calendar_entry;
SELECT COUNT(*) AS orgs_with_calendar FROM organization WHERE calendar IS NOT NULL AND calendar != '';
