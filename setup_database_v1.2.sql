-- Quick Database Setup Script for v1.2 Calendar Scraping
-- Run this in your MariaDB/MySQL database

USE minyanim;

-- 1. Add calendar columns to organization table if not exists
ALTER TABLE organization 
    ADD COLUMN IF NOT EXISTS calendar VARCHAR(2000);

ALTER TABLE organization 
    ADD COLUMN IF NOT EXISTS use_scraped_calendar BOOLEAN DEFAULT FALSE;

-- 2. Drop and recreate organization_calendar_entry table to ensure it's correct
DROP TABLE IF EXISTS organization_calendar_entry;

CREATE TABLE organization_calendar_entry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organization_id VARCHAR(255) NOT NULL,
    date DATE NOT NULL,
    title VARCHAR(500) NOT NULL,
    type VARCHAR(50),
    time TIME NOT NULL,
    raw_text TEXT,
    source_url VARCHAR(2000),
    fingerprint VARCHAR(64) NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    scraped_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    dedupe_reason VARCHAR(500),
    
    INDEX idx_org_date (organization_id, date),
    INDEX idx_org_date_enabled (organization_id, date, enabled),
    INDEX idx_fingerprint (fingerprint),
    
    CONSTRAINT fk_calendar_entry_org FOREIGN KEY (organization_id) 
        REFERENCES organization(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Verify the table was created
SHOW TABLES LIKE 'organization_calendar_entry';
DESCRIBE organization_calendar_entry;

-- 4. Check if organization table has the new columns
DESCRIBE organization;

SELECT 'Setup completed. Tables are ready.' AS status;
