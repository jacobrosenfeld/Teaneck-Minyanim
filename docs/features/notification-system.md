# Homepage Notification System Documentation

## Overview
Version 1.2.5 introduces a sophisticated homepage notification popup system that allows administrators to display important announcements to users with intelligent controls for expiration and display frequency.

**Important**: This system is separate from the existing "Home Page Announcement" banner. There are now two distinct announcement systems:
- **Home Page Announcement**: The existing banner shown at the top of the homepage
- **Home Page Popup**: The new modal popup notification system (this feature)

## Features

### 1. Popup Notification Modal
- Modern modal design with site-specific styling (#275ed8 blue)
- Montserrat font family matching site design
- Centered display that doesn't disrupt user experience
- Automatically dismissed with styled "Got it!" button
- Clean, professional appearance matching homepage aesthetic

### 2. Expiration Date Control
- Set an expiration date for time-limited announcements
- Notifications automatically stop displaying after the expiration date
- Date format: YYYY-MM-DD (e.g., "2024-12-31")
- Leave empty for no expiration (notification shows indefinitely)

### 3. Max Displays Per User
- Limit how many times a single user sees the notification
- Uses cookie-based tracking (persists for 1 year)
- Range: 1-100 displays
- Leave empty for unlimited displays
- Useful for important but non-critical announcements

### 4. Cookie-Based Tracking with Version Control
- Each notification version tracked separately
- View count stored in browser cookies: `notification_views_{id}_v{version}`
- Cookies persist for 1 year for long-term tracking
- Privacy-friendly (no server-side user tracking required)
- Works for anonymous users without login
- **Version Auto-Increment**: When notification text is updated, version increments automatically
- **Smart Reset**: Users see updated notifications even if they hit max displays on previous version

### 5. Multiline Markdown Support
- Full paragraph and line break support
- Bold/italic can span multiple lines
- Proper paragraph spacing with double newlines
- Line breaks within paragraphs preserved
- Lists and headers support multiline content

## Admin Configuration

### Accessing Settings
1. Navigate to Admin Panel → Settings
2. Find "Home Page Popup" in the settings table
3. Click "Edit" button to open configuration modal

**Note**: "Home Page Announcement" is a separate setting for the banner at the top of the page. The popup uses "Home Page Popup".

### Configuration Options

#### Enabled/Disabled
- **Enabled**: Notification will be shown to users (subject to expiration and display limits)
- **Disabled**: Notification will not be shown to anyone

#### Message Text (Markdown Supported)
- The announcement message shown in the popup
- Supports markdown formatting for rich text styling
- Keep concise and clear (recommended: under 300 characters with formatting)
- Markdown features supported:
  - **Bold text**: `**bold**`
  - *Italic text*: `*italic*`
  - [Links](url): `[link text](https://example.com)`
  - `Code`: `` `code` ``
  - Lists: `- list item`
  - Headers: `## Header`
- All markdown is rendered with site styling (Montserrat font, #275ed8 link color)
- HTML is automatically escaped for security

#### Expiration Date
- Optional field
- Format: YYYY-MM-DD (use date picker in form)
- Example: "2024-12-31" for New Year's Eve
- Leave empty if notification should never expire
- Notification stops showing after this date (inclusive)

#### Max Displays Per User
- Optional field
- Integer value between 1 and 100
- Leave empty for unlimited displays
- Example use cases:
  - Set to 1 for one-time critical announcements
  - Set to 3 for important but less urgent updates
  - Set to 5 for feature announcements
  - Leave empty for ongoing notifications

## Markdown Formatting Examples

### Basic Formatting
```markdown
**Important**: Version 1.2.5 is now available!

Visit our *help page* for more information.
```

### With Links
```markdown
Check out the [new features](https://example.com/features) in this release!
```

### Lists and Headers
```markdown
## What's New

- Improved performance
- Better mobile support
- Enhanced security

Learn more at our `documentation` page.
```

### Complete Multiline Example
```markdown
## Version 1.2.7 Released!

**New features** include:

- *Markdown styling* in notifications
- `Code formatting` support
- [Documentation updates](https://example.com)

This is a second paragraph with more details.
You can have multiple sentences on separate lines
and they'll flow naturally.

Visit our site for full details.
```

**Rendered Output:**
- Bold text appears with font-weight: 600
- Italic text is styled appropriately
- Links are colored #275ed8 (site blue) and open in new tab
- Double newlines create paragraph breaks
- Single newlines within paragraphs become line breaks
- Lists are properly formatted with bullets
- Headers use Montserrat font with proper sizing

## Version Tracking & View Count Reset

### How Version Tracking Works

When you update notification text in the admin panel, the system automatically:
1. Detects the text change
2. Increments the version number (e.g., 0 → 1, 1 → 2)
3. Starts fresh view tracking for the new version

### Example Scenario

**Initial Notification (Version 0):**
```markdown
## Version 1.2.5 Released!
Check out the new features.
```
- User sees it 3 times (maxDisplays: 3)
- Cookie: `notification_views_home-page-popup_v0 = 3`
- User won't see version 0 again

**Updated Notification (Version 1, auto-incremented):**
```markdown
## Version 1.2.7 Released!
Major improvements and bug fixes.
```
- Version automatically increments to 1
- User sees new notification immediately!
- Cookie: `notification_views_home-page-popup_v1 = 1`
- Old cookie (v0) remains but is ignored

### Benefits

- **No manual reset needed**: Admins just update the text
- **Users stay informed**: They see important updates even if they hit display limits
- **Version history**: Old cookies persist, allowing analytics if needed
- **Simple**: No complex workflows or buttons to reset tracking

### When Version Increments

✅ **Version DOES increment when:**
- Notification text is changed (even minor edits)
- Text is updated through admin panel

❌ **Version DOES NOT increment when:**
- Only expiration date is changed
- Only max displays is changed
- Notification is enabled/disabled
- No text change occurs
- Code appears with gray background
- Lists are properly indented with bullets
- Headers use Montserrat font with proper sizing

## Technical Implementation

### Database Schema
```sql
ALTER TABLE SETTINGS ADD COLUMN EXPIRATION_DATE VARCHAR(255) NULL;
ALTER TABLE SETTINGS ADD COLUMN MAX_DISPLAYS INT NULL;

-- New setting entry
INSERT INTO SETTINGS (ID, SETTING, ENABLED, TEXT, TYPE)
VALUES ('home-page-popup', 'Home Page Popup', 'Disabled', 'Default message', 'text');
```

### Frontend Components

#### notification-popup.js
JavaScript module that handles:
- Cookie management (get, set, read view counts)
- Expiration checking
- Display limit enforcement
- **Markdown parsing** (bold, italic, links, code, lists, headers)
- Modal creation and display with site styling
- Bootstrap 5 integration
- XSS protection (HTML escaping before markdown parsing)

#### Integration in homepage.html
```javascript
window.notificationData = {
    enabled: true,
    id: 'notification-id',
    title: 'Announcement',
    message: 'Your announcement text',
    maxDisplays: 5,
    expirationDate: '2024-12-31'
};
```

### Backend Components

#### TNMSettings Entity
- `expirationDate`: String field for date storage
- `maxDisplays`: Integer field for display limit

#### AdminController
Updated to handle new fields in settings update endpoint

## User Experience Flow

1. **User visits homepage**
2. **JavaScript checks notification settings**
   - Is notification enabled?
   - Has it expired?
   - Has user reached max displays?
3. **If all checks pass:**
   - Increment view count in cookie
   - Show popup modal after 500ms delay
4. **User clicks "Got it!"**
   - Modal dismisses
   - View count remains in cookie
5. **On next visit:**
   - JavaScript checks view count
   - Shows again if under max displays

## Migration Guide

### For Existing Installations

1. **Run Database Migration**
   ```bash
   mysql -u root -p minyanim < docs/migrations/v1.2.5-notification-system.sql
   ```

2. **Update Application**
   ```bash
   git pull origin main
   mvn clean install
   ```

3. **Restart Application**
   ```bash
   # Stop existing instance
   # Start new instance with updated code
   ```

4. **Configure Notification**
   - Access Admin Panel → Settings
   - Edit "Home Page Announcement"
   - Set expiration date and/or max displays
   - Enable notification

### For New Installations
- No action needed
- Schema will be created automatically by Hibernate
- Configure through Admin Settings panel

## Testing

### Test Expiration
1. Set expiration date to tomorrow
2. Visit homepage - popup should show
3. Change system date to day after expiration
4. Visit homepage - popup should NOT show

### Test Max Displays
1. Set max displays to 2
2. Clear browser cookies for the site
3. Visit homepage - popup shows (count: 1)
4. Refresh page - popup shows (count: 2)
5. Refresh again - popup does NOT show (limit reached)

### Test Cookie Persistence
1. Set notification with max displays
2. View popup once
3. Close browser completely
4. Open browser and visit homepage
5. View count should persist from previous session

## Troubleshooting

### Popup Not Showing
- Check if notification is enabled in Admin Settings
- Check if expiration date has passed
- Check browser cookies - clear if testing
- Check browser console for JavaScript errors

### Popup Showing Too Many Times
- Verify max displays value is set correctly
- Check if cookies are being cleared automatically
- Verify cookie domain settings in browser

### Expiration Not Working
- Verify date format is YYYY-MM-DD
- Check server timezone is set correctly
- Verify JavaScript Date parsing in browser console

## Best Practices

### Message Content
- Keep messages under 200 characters
- Use clear, action-oriented language
- Avoid technical jargon for general announcements
- Test message display on mobile devices

### Display Frequency
- Critical announcements: 1-2 displays
- Important updates: 3-5 displays
- Feature announcements: 5-10 displays
- Ongoing reminders: Unlimited

### Expiration Strategy
- Time-sensitive announcements: Set specific date
- Seasonal content: Set to end of season
- Permanent content: No expiration
- Regular updates: Use short expiration + new notification

## Privacy Considerations

### Cookie Usage
- Only stores notification view counts
- No personal information collected
- No server-side tracking
- Cookie name: `notification_views_{notification_id}`
- Lifetime: 1 year
- SameSite: Lax (prevents CSRF)

### GDPR Compliance
- Functional cookies (not marketing)
- No third-party cookies
- No user profiling
- Can be disabled by disabling cookies in browser

## Future Enhancements

Potential improvements for future versions:
- Multiple concurrent notifications
- Priority/urgency levels
- User-specific targeting
- A/B testing support
- Analytics dashboard
- Rich text formatting
- Image/icon support
- Action buttons with links
- Scheduled notifications
- Recurring notifications

## API Reference

### NotificationManager Methods

#### getCookie(name)
Returns cookie value by name
```javascript
const value = NotificationManager.getCookie('notification_views_1');
```

#### setCookie(name, value, days)
Sets cookie with optional expiration
```javascript
NotificationManager.setCookie('test', 'value', 30); // 30 days
```

#### getViewCount(notificationId)
Returns current view count for notification
```javascript
const count = NotificationManager.getViewCount('notification-1');
```

#### incrementViewCount(notificationId)
Increments and returns new view count
```javascript
const newCount = NotificationManager.incrementViewCount('notification-1');
```

#### isExpired(expirationDate)
Checks if notification has expired
```javascript
const expired = NotificationManager.isExpired('2024-12-31');
```

#### shouldShowNotification(notificationId, maxDisplays, expirationDate)
Returns true if notification should be shown
```javascript
const show = NotificationManager.shouldShowNotification('id', 5, '2024-12-31');
```

## Support

For issues or questions:
- GitHub Issues: [Teaneck-Minyanim Issues](https://github.com/jacobrosenfeld/Teaneck-Minyanim/issues)
- Email: info@teaneckminyanim.com

## Changelog Entry
See CHANGELOG.md for full version 1.2.5 details
