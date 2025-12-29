-- Migration SQL for v1.2.3: Unified MinyanType enum
-- This migration handles the removal of MinyanClassification enum and unification with MinyanType

-- IMPORTANT: This migration must be run BEFORE deploying the new code
-- as the old MINYAN enum value no longer exists

-- Step 1: Migrate legacy MINYAN entries to specific types based on title patterns
-- This attempts to classify existing MINYAN entries into specific types

-- Migrate to SHACHARIS
UPDATE organization_calendar_entry 
SET classification = 'SHACHARIS' 
WHERE classification = 'MINYAN' 
  AND (LOWER(title) LIKE '%shacharis%' 
       OR LOWER(title) LIKE '%shacharit%'
       OR LOWER(title) LIKE '%sunrise%'
       OR LOWER(title) LIKE '%vasikin%'
       OR LOWER(title) LIKE '%neitz%'
       OR LOWER(title) LIKE '%netz%');

-- Migrate to MINCHA (but not if it contains maariv/arvit)
UPDATE organization_calendar_entry 
SET classification = 'MINCHA' 
WHERE classification = 'MINYAN' 
  AND (LOWER(title) LIKE '%mincha%' OR LOWER(title) LIKE '%minchah%')
  AND LOWER(title) NOT LIKE '%maariv%'
  AND LOWER(title) NOT LIKE '%ma''ariv%'
  AND LOWER(title) NOT LIKE '%arvit%';

-- Migrate to MAARIV
UPDATE organization_calendar_entry 
SET classification = 'MAARIV' 
WHERE classification = 'MINYAN' 
  AND (LOWER(title) LIKE '%maariv%' 
       OR LOWER(title) LIKE '%ma''ariv%' 
       OR LOWER(title) LIKE '%arvit%')
  AND LOWER(title) NOT LIKE '%mincha%';

-- Migrate to SELICHOS
UPDATE organization_calendar_entry 
SET classification = 'SELICHOS' 
WHERE classification = 'MINYAN' 
  AND (LOWER(title) LIKE '%selichos%' OR LOWER(title) LIKE '%selichot%');

-- Step 2: Any remaining MINYAN entries that couldn't be classified should be set to OTHER
-- These will need manual review
UPDATE organization_calendar_entry 
SET classification = 'OTHER' 
WHERE classification = 'MINYAN';

-- Step 3: Verify migration
SELECT 
    classification,
    COUNT(*) as count
FROM organization_calendar_entry
GROUP BY classification
ORDER BY classification;

-- Note: After running this migration:
-- 1. Review entries classified as 'OTHER' to see if they need manual correction
-- 2. The legacy MINYAN enum value is deprecated but kept for backward compatibility
-- 3. New imports will automatically use the specific classification types
-- 4. You may want to reimport calendar entries to get fresh classifications
