# Calendar Scraping Feature - Setup Instructions

## IMPORTANT: Database Setup Required

The calendar scraping feature requires a new database table. **You must run the database setup script before using this feature.**

### Quick Setup Steps

1. **Run the Database Setup Script**

   Connect to your MariaDB/MySQL database and run:
   
   ```bash
   mysql -u root -p minyanim < setup_database_v1.2.sql
   ```
   
   Or manually execute the script in your database client (e.g., HeidiSQL, phpMyAdmin, MySQL Workbench).

2. **Restart the Application**

   After creating the database table, restart your Spring Boot application:
   
   ```bash
   ./mvnw spring-boot:run
   ```

3. **Configure an Organization**

   - Go to Admin Panel → Organizations
   - Select an organization
   - Fill in "Calendar URL" with your shul's calendar page
   - Check "Use Scraped Calendar"
   - Click "Update"

4. **Trigger a Sync**

   - Click "Refresh Zmanim Sync" button
   - You should see a message indicating success or any issues
   - Click "Manage Calendar Entries" to view scraped data

## Troubleshooting

### Problem: "Calendar entries page throws an error"

**Cause**: The `organization_calendar_entry` table doesn't exist in the database.

**Solution**: Run the `setup_database_v1.2.sql` script as shown above.

**Verification**:
```sql
USE minyanim;
SHOW TABLES LIKE 'organization_calendar_entry';
DESCRIBE organization_calendar_entry;
```

### Problem: "SQL table remains empty after sync"

**Cause 1**: Calendar format not supported (JavaScript-rendered)

**Solution**: Check if your calendar is compatible:

1. Visit your calendar URL
2. Right-click → View Page Source
3. Search for date patterns like "12/25/2024"
4. If you don't find dates in the HTML source, the calendar is JavaScript-rendered

See `CALENDAR_SCRAPING_GUIDE.md` for supported formats.

**Cause 2**: Scraping threw an exception

**Solution**: Check application logs for error messages:

```bash
# Look for errors in the logs
tail -f logs/spring.log | grep -i "calendar"
```

Or check the console output when you click "Refresh Zmanim Sync".

**Cause 3**: Database connection issue

**Solution**: Verify database connection:

```sql
USE minyanim;
SELECT * FROM organization WHERE calendar IS NOT NULL;
```

### Problem: "Error messages about database table"

**Error**: `Table 'minyanim.organization_calendar_entry' doesn't exist`

**Solution**: You forgot to run the setup script. See step 1 above.

## Database Schema

The setup script creates this table:

```sql
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
    scraped_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    dedupe_reason VARCHAR(500),
    -- Indexes for fast lookups
    INDEX idx_org_date (organization_id, date),
    INDEX idx_org_date_enabled (organization_id, date, enabled),
    INDEX idx_fingerprint (fingerprint)
);
```

## Testing the Feature

### Test with a Compatible Calendar

Many shuls don't have compatible calendars yet. To test:

1. **Find a simple HTML calendar**
   - Look for calendar pages with visible tables or lists
   - Avoid modern calendar widgets (FullCalendar, DayPilot)

2. **Check Logs**
   When you click "Refresh Zmanim Sync", watch the logs for:
   ```
   Scraping calendar from URL: [url]
   Downloaded HTML, size: X bytes
   Found X Google Calendar entries
   Found X table-based entries
   Found X list-based entries
   Scraped X total entries
   ```

3. **View Results**
   Click "Manage Calendar Entries" to see scraped data.

### Manual Test Query

After scraping, verify data in the database:

```sql
USE minyanim;

-- Check how many entries were scraped
SELECT COUNT(*) FROM organization_calendar_entry;

-- View recent entries
SELECT 
    organization_id,
    date,
    time,
    title,
    enabled,
    scraped_at
FROM organization_calendar_entry
ORDER BY scraped_at DESC
LIMIT 10;

-- Check entries for a specific org
SELECT * FROM organization_calendar_entry
WHERE organization_id = 'YOUR_ORG_ID'
ORDER BY date, time;
```

## Alternative: Manual Entry (Future Feature)

If your calendar format isn't supported, you can:

1. Wait for future enhancement (headless browser support)
2. Use a calendar export URL (many platforms offer static HTML versions)
3. Request support for your specific calendar platform

Email: info@teaneckminyanim.com with:
- Calendar URL
- Screenshot of the calendar
- Calendar platform name (if known)

## Application Logs

Enable debug logging to see detailed scraping information:

Add to `application.properties`:
```properties
logging.level.com.tbdev.teaneckminyanim.calendar=DEBUG
```

Then restart and check logs when clicking "Refresh Zmanim Sync".

## Common Calendar Platforms

### Confirmed Working
- Simple HTML tables with dates/times
- Basic WordPress calendar plugins
- Static Google Calendar embeds

### Not Working (JavaScript-rendered)
- FullCalendar.js
- DayPilot
- Modern React/Vue/Angular calendar apps
- Most embedded iframes

See `CALENDAR_SCRAPING_GUIDE.md` for detailed compatibility information.

## Support

If you've run the setup script and still have issues:

1. Check the application logs for error messages
2. Verify the table exists: `SHOW TABLES LIKE 'organization_calendar_entry';`
3. Test with a known compatible calendar format
4. Share error messages when reporting issues

## Version Information

- Feature Version: 1.2.0
- Database Schema Version: 1.2
- Required: MariaDB 10.2+ or MySQL 5.7+
