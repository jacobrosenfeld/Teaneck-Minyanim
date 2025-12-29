# Migration Notes for v1.2.3: Specific Minyan Type Classifications

## Overview
Version 1.2.3 refactors the `MinyanClassification` enum to use specific minyan types instead of a generic "MINYAN" classification. This improves pattern matching robustness and aligns with the existing `MinyanType` enum used for rule-based minyanim.

## Changes to MinyanClassification Enum

### Before (v1.2.2)
- `MINYAN` - Generic classification for all prayer services
- `MINCHA_MAARIV` - Combined afternoon/evening service
- `NON_MINYAN` - Non-prayer events
- `OTHER` - Other events

### After (v1.2.3)
- `SHACHARIS` - Morning prayer service (includes sunrise, vasikin, teen minyan)
- `MINCHA` - Afternoon prayer service (includes early mincha)
- `MAARIV` - Evening prayer service
- `MINCHA_MAARIV` - Combined afternoon/evening service (unchanged)
- `SELICHOS` - Penitential prayers
- `NON_MINYAN` - Non-prayer events (unchanged)
- `OTHER` - Other events (unchanged)

## Database Schema
No database schema changes are required. The `classification` column is already a `VARCHAR(20)` and can store the new enum values as strings.

## Data Migration
**Important:** Existing calendar entries with classification `MINYAN` will continue to work but will not be automatically updated. 

### For Existing Data:
1. **Option 1 (Recommended)**: Use "Reimport All" button in admin UI
   - This will reclassify all entries using the new specific types
   - Entries previously classified as `MINYAN` will be reclassified as `SHACHARIS`, `MINCHA`, or `MAARIV` based on title/type

2. **Option 2**: Manual SQL update (if needed for specific cases)
   ```sql
   -- Update entries that should be SHACHARIS
   UPDATE organization_calendar_entry 
   SET classification = 'SHACHARIS' 
   WHERE classification = 'MINYAN' 
     AND (LOWER(title) LIKE '%shacharis%' 
          OR LOWER(title) LIKE '%shacharit%'
          OR LOWER(title) LIKE '%sunrise%'
          OR LOWER(title) LIKE '%vasikin%');

   -- Update entries that should be MINCHA
   UPDATE organization_calendar_entry 
   SET classification = 'MINCHA' 
   WHERE classification = 'MINYAN' 
     AND LOWER(title) LIKE '%mincha%'
     AND LOWER(title) NOT LIKE '%maariv%';

   -- Update entries that should be MAARIV
   UPDATE organization_calendar_entry 
   SET classification = 'MAARIV' 
   WHERE classification = 'MINYAN' 
     AND (LOWER(title) LIKE '%maariv%' OR LOWER(title) LIKE '%arvit%')
     AND LOWER(title) NOT LIKE '%mincha%';

   -- Update entries that should be SELICHOS
   UPDATE organization_calendar_entry 
   SET classification = 'SELICHOS' 
   WHERE classification = 'MINYAN' 
     AND (LOWER(title) LIKE '%selichos%' OR LOWER(title) LIKE '%selichot%');
   ```

## Backward Compatibility

### Code Compatibility
- All existing code using `isMinyan()` method continues to work
  - `isMinyan()` now returns true for: SHACHARIS, MINCHA, MAARIV, MINCHA_MAARIV, SELICHOS
- Statistics and counts remain accurate
- Enabled/disabled logic unchanged

### API Compatibility
- `fromString()` method supports all new enum values
- Display names provided via `getDisplayName()` method
- UI filters and badges updated to show specific types

## Testing
All 66 tests pass, including:
- 43 MinyanClassifier tests verifying specific classifications
- Pattern matching tests for all minyan types
- Integration tests for enabled/disabled logic
- UI template rendering (no runtime errors)

## UI Changes
1. **Admin Calendar Entries Page**
   - Filter dropdown now shows: Shacharis, Mincha, Maariv, Mincha/Maariv, Selichos, Non-Minyan, Other
   - Classification badges color-coded:
     - Green: All minyan types (SHACHARIS, MINCHA, MAARIV, SELICHOS)
     - Cyan: MINCHA_MAARIV
     - Gray: NON_MINYAN
     - Yellow: OTHER

## Benefits
1. **More Robust Pattern Matching**: Each minyan type has dedicated pattern set
2. **Better Alignment**: Matches existing `MinyanType` enum structure
3. **Improved Filtering**: Users can filter by specific prayer type
4. **Enhanced Statistics**: Can track counts by specific minyan type
5. **Future-Proof**: Easier to add new minyan types (e.g., MEGILA_READING)

## Rollback Procedure
If rollback is needed:
1. Revert code changes to previous commit
2. Optionally run SQL to convert specific types back to generic MINYAN:
   ```sql
   UPDATE organization_calendar_entry 
   SET classification = 'MINYAN' 
   WHERE classification IN ('SHACHARIS', 'MINCHA', 'MAARIV', 'SELICHOS');
   ```

## Questions or Issues
Contact: jacobrosenfeld@users.noreply.github.com
