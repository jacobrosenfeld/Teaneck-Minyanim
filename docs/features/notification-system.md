# Homepage Notification System Documentation

## Overview
Version 1.2.5 introduces a sophisticated homepage notification popup system that allows administrators to display important announcements to users with intelligent controls for expiration and display frequency.

## Features

### 1. Popup Notification Modal
- Modern Bootstrap 5 modal design with icon and styled buttons
- Centered display that doesn't disrupt user experience
- Automatically dismissed with "Got it!" button
- Clean, professional appearance matching site design

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

### 4. Cookie-Based Tracking
- Each notification tracked separately by its unique ID
- View count stored in browser cookies: `notification_views_{id}`
- Cookies persist for 1 year for long-term tracking
- Privacy-friendly (no server-side user tracking required)
- Works for anonymous users without login

## Admin Configuration

### Accessing Settings
1. Navigate to Admin Panel → Settings
2. Find "Home Page Announcement" in the settings table
3. Click "Edit" button to open configuration modal

### Configuration Options

#### Enabled/Disabled
- **Enabled**: Notification will be shown to users (subject to expiration and display limits)
- **Disabled**: Notification will not be shown to anyone

#### Message Text
- The announcement message shown in the popup
- Keep concise and clear (recommended: under 200 characters)
- Supports plain text only

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

## Technical Implementation

### Database Schema
```sql
ALTER TABLE SETTINGS ADD COLUMN EXPIRATION_DATE VARCHAR(255) NULL;
ALTER TABLE SETTINGS ADD COLUMN MAX_DISPLAYS INT NULL;
```

### Frontend Components

#### notification-popup.js
JavaScript module that handles:
- Cookie management (get, set, read view counts)
- Expiration checking
- Display limit enforcement
- Modal creation and display
- Bootstrap 5 integration

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
