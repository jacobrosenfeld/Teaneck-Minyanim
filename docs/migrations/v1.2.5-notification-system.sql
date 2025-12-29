-- Migration for Version 1.2.5: Homepage Notification System
-- Adds expiration_date, max_displays, and version columns to SETTINGS table
-- Creates new "Home Page Popup" setting separate from existing "Home Page Announcement"

-- Add expiration_date column (nullable)
ALTER TABLE SETTINGS ADD COLUMN EXPIRATION_DATE VARCHAR(255) NULL;

-- Add max_displays column (nullable)
ALTER TABLE SETTINGS ADD COLUMN MAX_DISPLAYS INT NULL;

-- Add version column for tracking content changes (nullable, defaults to 0)
ALTER TABLE SETTINGS ADD COLUMN VERSION BIGINT NULL DEFAULT 0;

-- Insert new "Home Page Popup" setting (separate from existing "Home Page Announcement")
-- This will be used for the modal popup notification system
INSERT INTO SETTINGS (ID, SETTING, ENABLED, TEXT, TYPE, EXPIRATION_DATE, MAX_DISPLAYS, VERSION)
VALUES (
    'home-page-popup',
    'Home Page Popup',
    'Disabled',
    'Welcome! Check out the latest updates to Teaneck Minyanim.',
    'text',
    NULL,
    NULL,
    0
);

-- Note: The existing "Home Page Announcement" setting remains unchanged
-- It continues to control the banner announcement at the top of the homepage

-- Optional: Add comment to track migration
-- This migration adds support for notification popup controls on the homepage
-- - EXPIRATION_DATE: Date string (YYYY-MM-DD format) after which notification should not be shown
-- - MAX_DISPLAYS: Maximum number of times a notification should be shown to a single user
-- - VERSION: Auto-incremented when notification text changes, resets view tracking per version
-- - New "Home Page Popup" setting: Controls the modal popup (distinct from banner announcement)

-- Configuration:
-- "Home Page Announcement" = Banner at top of homepage (existing)
-- "Home Page Popup" = Modal popup notification (new in v1.2.5)

-- Version Tracking:
-- When admin updates notification text, version auto-increments
-- Users will see the new notification even if they hit max displays on previous version
-- Cookie format: notification_views_{id}_v{version}

