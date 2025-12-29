-- Migration for Version 1.2.5: Homepage Notification System
-- Adds expiration_date and max_displays columns to SETTINGS table

-- Add expiration_date column (nullable)
ALTER TABLE SETTINGS ADD COLUMN EXPIRATION_DATE VARCHAR(255) NULL;

-- Add max_displays column (nullable)
ALTER TABLE SETTINGS ADD COLUMN MAX_DISPLAYS INT NULL;

-- Optional: Add comment to track migration
-- This migration adds support for notification popup controls on the homepage
-- - EXPIRATION_DATE: Date string (YYYY-MM-DD format) after which notification should not be shown
-- - MAX_DISPLAYS: Maximum number of times a notification should be shown to a single user

-- Note: Existing settings will have NULL values for these columns
-- These can be configured through the Admin Settings panel
