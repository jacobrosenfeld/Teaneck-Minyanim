# Organization URL Slug Migration

This directory contains one-time migration scripts for the Teaneck Minyanim application.

## Generate Organization Slugs Migration

**File:** `GenerateOrganizationSlugsRunner.java`

### Purpose
Generates URL slugs for all existing organizations in the database that don't already have one. This is a one-time migration needed after deploying the URL slug feature.

### What It Does
1. Finds all organizations in the database
2. For each organization without a slug:
   - Generates a slug from the organization name (e.g., "Keter Torah" → "keter-torah")
   - Ensures uniqueness by appending numbers if needed (e.g., "keter-torah-2")
   - Saves the slug to the database
3. Logs detailed results showing which organizations were updated

### How to Run

#### Step 1: Enable the Migration
Add this line to your `application.properties` file:
```properties
migration.generate-slugs.enabled=true
```

#### Step 2: Start the Application
```bash
mvn spring-boot:run
```

#### Step 3: Check the Logs
The migration will run automatically on startup. Look for output like this:
```
=================================================================
Starting migration: Generate URL slugs for existing organizations
=================================================================
Found 5 organization(s) in database
  [OK] Generated slug for 'Keter Torah' (ID: O1234567890): 'keter-torah'
  [OK] Generated slug for 'Congregation Beth Aaron' (ID: O0987654321): 'congregation-beth-aaron'
  [SKIP] Organization 'Rinat Yisrael' (ID: O1122334455) already has slug: 'rinat-yisrael'
  ...
=================================================================
Migration completed!
  Total organizations: 5
  Updated: 4
  Skipped (already had slug): 1
  Errors: 0
=================================================================

SUCCESS! 4 organization(s) now have URL slugs.

NEXT STEPS:
1. Set 'migration.generate-slugs.enabled=false' in application.properties
2. Restart the application to prevent re-running this migration
```

#### Step 4: Disable the Migration
After the migration runs successfully, set this in `application.properties`:
```properties
migration.generate-slugs.enabled=false
```

Or simply remove the property.

#### Step 5: Restart the Application
```bash
mvn spring-boot:run
```

### Safety Features
- **Idempotent**: Safe to run multiple times - skips organizations that already have slugs
- **Conditional**: Only runs when explicitly enabled via configuration property
- **Detailed Logging**: Shows exactly what happened for each organization
- **Error Handling**: Continues processing if one organization fails

### Verification
After running the migration, you can verify the results by:

1. **Check the database:**
   ```sql
   SELECT id, name, url_slug FROM organization;
   ```

2. **Test the URLs:**
   - Visit `/org/{slug}` for each organization
   - Verify old `/orgs/{id}` URLs still work

3. **Admin interface:**
   - Edit an organization in the admin panel
   - Verify the URL slug field shows the generated slug

### Troubleshooting

**Migration doesn't run:**
- Check that `migration.generate-slugs.enabled=true` is in `application.properties`
- Check application logs for any errors during startup

**Some organizations show errors:**
- Check if the organization name is empty or contains only special characters
- Review error messages in the logs for specific issues

**Duplicate slug errors:**
- The script should handle this automatically by appending numbers
- If you see errors, check the database for existing slug conflicts

### Example Output

For a database with these organizations:
- "Keter Torah"
- "Congregation Beth Aaron"
- "Young Israel of Teaneck"
- "Rinat Yisrael"

The migration will generate:
- `keter-torah`
- `congregation-beth-aaron`
- `young-israel-of-teaneck`
- `rinat-yisrael`

### Notes
- This is a **one-time migration** - it should only be run once per environment
- After running, the migration can be safely left in the codebase (it won't run unless enabled)
- Future organizations created through the admin interface will automatically get slugs
- If you need to regenerate a slug, you can manually edit it in the admin interface
