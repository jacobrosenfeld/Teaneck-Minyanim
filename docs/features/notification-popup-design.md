# Homepage Notification Popup - Visual Design

## Modal Appearance

The notification popup appears as a Bootstrap 5 modal with the following styling:

```
┌─────────────────────────────────────────────────────┐
│  🔔 Announcement                            [X]     │  <- Primary blue header
├─────────────────────────────────────────────────────┤
│                                                     │
│  Version 1.2.4 released! Setting up the system     │
│  for pulling accurate times from shul websites.     │
│                                                     │
├─────────────────────────────────────────────────────┤
│                                    [  Got it!  ]    │  <- Primary blue button
└─────────────────────────────────────────────────────┘
```

## Key Visual Features

### Header
- **Background**: Bootstrap primary blue (#0d6efd)
- **Text**: White
- **Icon**: Megaphone icon (bi-megaphone-fill) on the left
- **Close button**: White X button on the right

### Body
- **Background**: White
- **Text**: Dark gray/black, left-aligned
- **Padding**: Standard Bootstrap modal padding
- **Message**: Displayed as paragraph text

### Footer
- **Background**: Light gray/white
- **Button**: Primary blue "Got it!" button
- **Alignment**: Right-aligned

### Modal Behavior
- **Positioning**: Centered on screen (modal-dialog-centered)
- **Backdrop**: Dark semi-transparent overlay
- **Animation**: Smooth fade-in (Bootstrap default)
- **Display delay**: 500ms after page load
- **Dismissal**: Click "Got it!" button or click outside modal

## Admin Settings Panel Preview

In the admin settings panel, the Home Page Announcement setting now shows:

```
┌───────────────────────────────────────────────────────────────────────────────┐
│ Setting              │ ID  │ Enabled │ Value          │ Expiration │ Max     │
│                      │     │         │                │            │ Displays│
├───────────────────────────────────────────────────────────────────────────────┤
│ Home Page            │ 1   │ [✓]     │ Version 1.2.4  │ 2024-12-31 │ 3      │
│ Announcement         │     │ Enabled │ released!...   │            │        │
│                      │     │         │                │            │ [Edit] │
└───────────────────────────────────────────────────────────────────────────────┘
```

When clicking Edit, the modal form includes:

```
┌─────────────────────────────────────────────────────────────┐
│  Edit                                              [X]      │
├─────────────────────────────────────────────────────────────┤
│  Home Page Announcement                                     │
│                                                             │
│  Enabled: [Enabled ▼]                                       │
│                                                             │
│  Message Text:                                              │
│  [Version 1.2.4 released! Setting up the system...]        │
│                                                             │
│  Expiration Date (Optional):                                │
│  [2024-12-31] 📅                                           │
│  Leave empty for no expiration. Format: YYYY-MM-DD         │
│                                                             │
│  Max Displays Per User (Optional):                          │
│  [3]                                                        │
│  Leave empty for unlimited displays. Max: 100              │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│                                              [  Save  ]     │
└─────────────────────────────────────────────────────────────┘
```

## Responsive Behavior

### Desktop (> 992px)
- Modal width: ~500px
- Centered on screen
- Full text visible

### Tablet (768px - 992px)
- Modal width: ~90% of screen
- Centered on screen
- Text wraps if needed

### Mobile (< 768px)
- Modal width: ~95% of screen
- Centered on screen
- Close button easily tappable
- "Got it!" button full-width for easy tapping

## User Experience Flow

1. **First Visit**: User sees popup after 500ms
2. **Cookie Set**: View count = 1
3. **User Clicks "Got it!"**: Modal dismisses smoothly
4. **Second Visit**: Popup shows again if under max displays
5. **After Max Displays**: Popup no longer shows
6. **After Expiration**: Popup no longer shows

## Testing Scenarios

Use the test page at `/test-notification.html` to verify:
- ✅ Basic notification displays correctly
- ✅ Limited displays work (stops after max reached)
- ✅ Expiring notifications show until expiration date
- ✅ Expired notifications don't show
- ✅ Cookie clearing resets view count
