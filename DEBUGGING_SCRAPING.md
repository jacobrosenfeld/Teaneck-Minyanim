# Debugging Calendar Scraping Issues

## Issue: Table Exists But Remains Empty

If you've confirmed the `organization_calendar_entry` table exists in your database but it remains empty after clicking "Refresh Zmanim Sync", follow these debugging steps.

## Step 1: Use the Debug Calendar Tool

I've added a new **"Debug Calendar"** button on the organization page (next to "Manage Calendar Entries").

**How to use it:**
1. Go to Admin Panel → Your Organization
2. Ensure Calendar URL is filled in
3. Click the **"Debug Calendar"** button (opens in new tab)
4. Review the debug report

**What the debug report shows:**
- Whether the page downloaded successfully
- If JavaScript calendar libraries are detected (FullCalendar, DayPilot, React)
- Whether date/time patterns exist in the HTML
- Sample of visible text content
- Detection of minyan-related keywords

## Step 2: Interpret the Debug Report

### Scenario A: JavaScript Calendar Detected

If you see warnings like:
```
⚠ Found FullCalendar.js: ...
⚠ WARNING: This appears to be a JavaScript-rendered calendar.
```

**Problem**: The calendar renders content using JavaScript AFTER the page loads. Static HTML scraping won't work because Jsoup only sees the initial HTML, not the JavaScript-rendered content.

**Solutions**:
1. **Check for export URL**: Many calendar platforms offer a separate URL for HTML export or iCal feed
2. **Contact the shul**: Ask if they have an API or feed URL
3. **Wait for future enhancement**: We're planning to add headless browser support (Selenium/Playwright)

### Scenario B: No Date/Time Patterns Found

If you see:
```
✗ No date patterns found!
✗ No time patterns found!
```

**Problem**: The calendar doesn't have dates/times in a format the scraper recognizes, or the content is hidden/protected.

**Check**:
- Is the calendar password-protected?
- Does the calendar use unusual date formats?
- Share the debug report output so we can add support

### Scenario C: Dates/Times Found But No Entries Scraped

If the debug report shows date/time patterns but sync still returns 0 entries:

**Possible causes**:
1. **Date range mismatch**: Scraper looks for dates within 2 weeks past to 8 weeks future
2. **Missing title extraction**: Events need a recognizable title (Shacharis, Mincha, etc.)
3. **HTML structure not matched**: Calendar uses a structure the scraper doesn't recognize yet

## Step 3: Check Application Logs

When you click "Refresh Zmanim Sync", check your application logs for detailed output:

```
Scraping calendar from URL: https://...
Downloaded HTML, size: X bytes
Found X Google Calendar entries
Found X table-based entries
Found X list-based entries
Scraped X total entries from https://...
```

If all three parsing methods return 0 entries, the calendar structure isn't compatible.

## Step 4: Manual Verification

### Check what Jsoup sees:

Visit your calendar URL in a browser:
1. Right-click → "View Page Source"
2. Search (Ctrl+F) for today's date (e.g., "12/23/2024" or "December 23")
3. Search for time patterns (e.g., "7:30 AM")

**If you DON'T find them in the page source**: JavaScript-rendered calendar (see Scenario A)

**If you DO find them**: The HTML structure might not match our parsers. Share the page source (or the debug report) and I can add support.

## Step 5: Test with a Known Compatible Calendar

To verify the scraping system works, try testing with a simple calendar format:

### Example: Create a Test HTML Calendar

Save this as a file and host it, or use a simple calendar you control:

```html
<!DOCTYPE html>
<html>
<body>
    <table>
        <tr>
            <td>12/25/2024</td>
            <td>7:30 AM</td>
            <td>Shacharis</td>
        </tr>
        <tr>
            <td>12/25/2024</td>
            <td>1:45 PM</td>
            <td>Mincha</td>
        </tr>
    </table>
</body>
</html>
```

If this works but your calendar doesn't, it confirms a format compatibility issue.

## Common Calendar Platforms

### ✓ Known to Work
- Simple HTML tables
- WordPress Simple Calendar plugin
- Static Google Calendar HTML embeds
- Basic list-based calendars

### ✗ Known NOT to Work
- **FullCalendar.js** (most common JavaScript calendar)
- **DayPilot** (JavaScript calendar)
- **React/Vue/Angular calendar apps**
- **Embedded iframes** from external sites
- **CheshbonHanefesh** calendars
- **ShulCloud** (most use JavaScript)

### ? Platform Unknown - Try the Debug Tool
- **Rinat.org** (the URL in your screenshot)
- **TorahCalendar.com** embeds
- Custom shul websites

## Example: Rinat.org Calendar

Based on your screenshot showing `https://rinat.org/calendar`, let me check what platform they're likely using:

Most modern synagogue websites use:
1. **ShulCloud** → Usually JavaScript-based (won't work)
2. **WordPress with FullCalendar** → JavaScript-based (won't work)
3. **Custom CMS** → Depends on implementation

**Action**: Click the "Debug Calendar" button to see what's actually on https://rinat.org/calendar

## What to Share for Support

If you need help adding support for your calendar:

1. **Debug report output** (from the "Debug Calendar" button)
2. **Calendar platform name** (if you know it)
3. **Screenshot** of the calendar page
4. **Page source** (right-click → View Page Source, save first 1000 lines)

## Future Enhancements

We're planning to add:
1. **Headless browser support** (Selenium/Playwright) for JavaScript calendars
2. **More date format patterns** (DD/MM/YYYY, spelled-out dates, etc.)
3. **Manual entry UI** (add events manually if scraping doesn't work)
4. **iCal feed import** (standard calendar format)
5. **API integrations** for major shul management platforms

## Quick Checklist

- [ ] Confirmed table exists: `SHOW TABLES LIKE 'organization_calendar_entry';`
- [ ] Calendar URL is filled in and accessible
- [ ] "Use Scraped Calendar" checkbox is checked
- [ ] Clicked "Refresh Zmanim Sync"
- [ ] Clicked "Debug Calendar" to see what's on the page
- [ ] Checked application logs for scraping output
- [ ] Verified calendar isn't JavaScript-rendered (check page source)
- [ ] Tested date range (entries must be within 2 weeks past to 8 weeks future)

## Still Not Working?

Reply with:
1. Output from "Debug Calendar" button
2. Any error messages from "Refresh Zmanim Sync"
3. Calendar platform name (if known)
4. Screenshot of the calendar page

This will help diagnose the specific issue with your calendar format.
