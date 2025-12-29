# Homepage Notification Popup - Visual Design (Updated v1.2.5)

## Modal Appearance - Site-Styled Version

The notification popup now matches the Teaneck Minyanim site styling with the primary blue color (#275ed8) and Montserrat font:

```
┌────────────────────────────────────────────────────────┐
│  🔔 Announcement                             [X]      │  <- Site blue (#275ed8)
├────────────────────────────────────────────────────────┤
│                                                        │
│  Version 1.2.5 released! Setting up the system        │
│  for pulling accurate times from shul websites.        │
│                                                        │
│  (Montserrat font, clean spacing)                     │
│                                                        │
├────────────────────────────────────────────────────────┤
│                                       [  Got it!  ]    │  <- Styled .btn-cta
└────────────────────────────────────────────────────────┘
```

## Key Visual Features (Updated)

### Header
- **Background**: Site primary blue (#275ed8)
- **Text**: White
- **Font**: Montserrat, semi-bold (600)
- **Icon**: Megaphone icon (bi-megaphone-fill) on the left
- **Close button**: White X button on the right
- **Border Radius**: 8px top corners

### Body
- **Background**: White
- **Text**: Dark gray/black, left-aligned
- **Font**: Montserrat, regular weight
- **Font Size**: 1rem (16px)
- **Line Height**: 1.6 for readability
- **Padding**: 1.5rem for comfortable spacing

### Footer
- **Background**: White with subtle top border
- **Button Style**: Matches site's `.btn-cta` class
  - Background: #275ed8 (site blue)
  - Text: White
  - Font: Montserrat, semi-bold (600)
  - Border Radius: 4px
  - Letter Spacing: 0.5px
  - Padding: 8px 24px
  - No border
- **Alignment**: Right-aligned

### Modal Container
- **Border**: None
- **Border Radius**: 8px (rounded corners)
- **Shadow**: Default Bootstrap modal shadow
- **Backdrop**: Dark semi-transparent overlay
- **Animation**: Smooth fade-in (Bootstrap default)
- **Display delay**: 500ms after page load
- **Dismissal**: Click "Got it!" button or click outside modal

## Comparison with Site Design

### Color Scheme Alignment
- ✅ Uses #275ed8 (site primary blue) instead of Bootstrap blue
- ✅ Matches .btn-cta button styling
- ✅ Consistent with site's color palette

### Typography Alignment
- ✅ Montserrat font family throughout
- ✅ Similar font weights to site buttons (600)
- ✅ Letter spacing matches site style
- ✅ Line height provides comfortable reading

### Visual Consistency
- ✅ Rounded corners (8px) match site elements
- ✅ Button styling identical to site's CTA buttons
- ✅ Spacing and padding consistent with site design
- ✅ Professional, clean appearance

## Two Separate Announcement Systems

### 1. Home Page Announcement (Existing)
```
┌────────────────────────────────────────────────────────┐
│  Welcome to Teaneck Minyanim. You can find minyanim   │  <- Gray banner
│  for today on the home page...                         │     at page top
└────────────────────────────────────────────────────────┘
```
- **Type**: Banner (alert-secondary)
- **Location**: Top of homepage, below header
- **Style**: Bootstrap jumbotron alert
- **Setting**: "Home Page Announcement"
- **Features**: Always visible when enabled, no expiration/limits

### 2. Home Page Popup (New)
```
        [Centered Modal Dialog]
┌────────────────────────────────┐
│  🔔 Announcement        [X]   │
├────────────────────────────────┤
│  Your message here...         │
├────────────────────────────────┤
│                  [  Got it!  ]│
└────────────────────────────────┘
```
- **Type**: Modal popup
- **Location**: Center of viewport (overlay)
- **Style**: Site-branded (#275ed8)
- **Setting**: "Home Page Popup"
- **Features**: Expiration dates, max displays, cookie tracking

## Admin Settings Panel Preview (Updated)

In the admin settings panel, there are now TWO separate settings:

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ Setting              │ ID  │ Enabled │ Value            │ Expiration │ Max Displays │
├─────────────────────────────────────────────────────────────────────────────────────┤
│ Home Page            │ 1   │ [✓]     │ Welcome to       │ N/A        │ N/A         │
│ Announcement         │     │ Enabled │ Teaneck...       │            │      [Edit] │
├─────────────────────────────────────────────────────────────────────────────────────┤
│ Home Page            │ 2   │ [✓]     │ Version 1.2.5    │ 2024-12-31 │ 3          │
│ Popup                │     │ Enabled │ released!...     │            │      [Edit] │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### Key Differences
1. **Home Page Announcement**: Controls the banner (no expiration/limits)
2. **Home Page Popup**: Controls the modal (with expiration/limits)

## Responsive Behavior

### Desktop (> 992px)
- Modal width: ~500px
- Centered on screen
- Full text visible
- Large "Got it!" button

### Tablet (768px - 992px)
- Modal width: ~90% of screen
- Centered on screen
- Text wraps naturally
- Button maintains proportions

### Mobile (< 768px)
- Modal width: ~95% of screen
- Centered on screen
- Close button easily tappable (44x44px minimum)
- "Got it!" button sized for easy tapping
- Text wraps for readability

## Color Specifications

```css
/* Primary Site Blue */
--primary-color: #275ed8;

/* Modal Header */
background-color: #275ed8;
color: #FFFFFF;

/* Button (matches .btn-cta) */
background-color: #275ed8 !important;
color: #FFFFFF !important;
border: none;
border-radius: 4px;

/* Typography */
font-family: 'Montserrat', sans-serif;
font-weight: 600; /* Semi-bold for headers and buttons */
font-weight: 400; /* Regular for body text */
```

## Testing Scenarios

Use the test page at `/test-notification.html` to verify:
- ✅ Styling matches site design (#275ed8 color)
- ✅ Montserrat font loads correctly
- ✅ Button hover effects work
- ✅ Modal appears centered
- ✅ Responsive behavior on mobile
- ✅ "Home Page Popup" setting is used (not "Home Page Announcement")
