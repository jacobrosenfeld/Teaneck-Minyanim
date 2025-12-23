# Calendar Scraping - Supported Formats & Troubleshooting

## Supported Calendar Formats

The v1.2 calendar scraper can extract minyan times from these HTML structures:

### 1. Google Calendar Embeds
```html
<div class="event">
    <span class="date">12/25/2024</span>
    <span class="time">7:30 AM</span>
    <span class="title">Shacharis</span>
</div>
```

### 2. HTML Tables
```html
<table>
    <tr>
        <td>12/25/2024</td>
        <td>7:30 AM</td>
        <td>Shacharis</td>
    </tr>
</table>
```

### 3. HTML Lists
```html
<ul>
    <li>12/25/2024 - 7:30 AM - Shacharis</li>
</ul>
```

## Not Supported (Currently)

- **JavaScript-rendered calendars** (React, Vue, Angular, FullCalendar.js)
- **iFrame embeds** from external domains
- **PDF or image-based calendars**
- **Calendars requiring authentication**

## Troubleshooting Empty Results

### 1. Check Calendar URL
Make sure the URL points directly to a page with visible calendar events (not a homepage or login page).

### 2. Test the URL
Visit the calendar URL in your browser and:
- Right-click → View Page Source
- Look for visible date/time patterns in the HTML
- If you see `<script>` tags loading a calendar library (FullCalendar, DayPilot, etc.), it's JavaScript-rendered

### 3. Check Application Logs
After clicking "Refresh Zmanim Sync", check the logs for:
```
Scraping calendar from URL: [url]
Downloaded HTML, size: [bytes] bytes
Found X Google Calendar entries
Found X table-based entries  
Found X list-based entries
Scraped X total entries from [url]
```

### 4. Alternative Solutions

If your calendar is JavaScript-based:

**Option A: Export URL**
Many calendars offer an export/feed URL that provides static HTML:
- Google Calendar: Use the public calendar HTML embed
- Other platforms: Look for "Export" or "Feed" options

**Option B: Manual Entry** (Future Feature)
We plan to add a manual entry UI for adding individual minyan times.

**Option C: Custom Scraper**
Contact the maintainers to add support for your specific calendar platform.

## Common Calendar Platforms

### Supported
- ✅ Simple HTML tables
- ✅ WordPress calendar plugins (if they output HTML)
- ✅ Basic PHP calendar scripts

### Partially Supported
- ⚠️ Google Calendar embeds (depends on embed type)
- ⚠️ Shul Cloud (depends on configuration)

### Not Supported (Yet)
- ❌ FullCalendar.js
- ❌ DayPilot Calendar
- ❌ TorahCalendar.com embeds
- ❌ CheshbonHanefesh calendars

## Testing Your Calendar

To test if your calendar is compatible:

1. Visit your calendar URL
2. Right-click → View Page Source
3. Search for date patterns like "12/25/2024" or "December 25"
4. If you find them in the HTML source, it should work
5. If you only see JavaScript code, it won't work yet

## Example: Testing rinat.org/calendar

```bash
# Fetch the page
curl https://rinat.org/calendar > calendar.html

# Search for date patterns
grep -E "[0-9]{1,2}/[0-9]{1,2}/[0-9]{4}" calendar.html

# Search for time patterns  
grep -E "[0-9]{1,2}:[0-9]{2}\s*[AP]M" calendar.html
```

If these searches return no results, the calendar is JavaScript-rendered.

## Requesting Support for Your Platform

If your shul's calendar isn't working, please provide:

1. Calendar URL
2. Screenshot of the calendar page
3. Page source (right-click → View Page Source, save as .html)
4. Calendar platform/software name if known

Email to: info@teaneckminyanim.com with subject "Calendar Scraping Support Request"
