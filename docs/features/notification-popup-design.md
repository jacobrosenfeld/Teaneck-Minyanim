# Homepage Notification Popup - Visual Design (Updated v1.2.5)

## Modal Appearance - Site-Styled with Markdown Support

The notification popup now matches the Teaneck Minyanim site styling with the primary blue color (#275ed8) and Montserrat font, with full markdown formatting support:

```
┌────────────────────────────────────────────────────────┐
│  🔔 Announcement                             [X]      │  <- Site blue (#275ed8)
├────────────────────────────────────────────────────────┤
│                                                        │
│  New Features                                          │  <- ## Header
│                                                        │
│  Version 1.2.5 includes:                               │  <- **bold**
│                                                        │
│  • Markdown styling support                            │  <- - list
│  • Code formatting                                     │  <- - list
│  • Link support                                        │  <- - list
│                                                        │
│  Visit our documentation page for details.             │  <- [link](url)
│                                                        │
│  (Montserrat font, site-styled formatting)             │
│                                                        │
├────────────────────────────────────────────────────────┤
│                                       [  Got it!  ]    │  <- Styled .btn-cta
└────────────────────────────────────────────────────────┘
```

## Markdown Formatting Examples

### Basic Example
**Input (Admin Panel):**
```markdown
**Important**: Version 1.2.5 is now available!
```

**Rendered Output:**
- "Important" appears in **bold** (font-weight: 600)
- Rest in regular weight

### With Links and Italic
**Input:**
```markdown
Check out the [new features](https://example.com) - they're *amazing*!
```

**Rendered Output:**
- "new features" is a clickable link in #275ed8 blue
- "amazing" appears in *italic*
- Link opens in new tab

### Lists with Headers
**Input:**
```markdown
## What's New

- Improved performance
- Better mobile support
- Enhanced security
```

**Rendered Output:**
- "What's New" is a header (h4 style, Montserrat 600)
- Three bulleted items with proper indentation
- Each item on its own line

### Complete Notification Example
**Input:**
```markdown
## Version 1.2.5 Released!

**New features** include:

- *Markdown styling* in notifications
- `Code formatting` support
- [Documentation updates](https://example.com)

Visit our site for full details.
```

**Rendered Output:**
- Bold header at top
- "New features" in bold
- Three bulleted items with mixed formatting
- Link in site blue color
- Clean spacing between elements

## Key Visual Features (Updated)

### Header
- **Background**: Site primary blue (#275ed8)
- **Text**: White
- **Font**: Montserrat, semi-bold (600)
- **Icon**: Megaphone icon (bi-megaphone-fill) on the left
- **Close button**: White X button on the right
- **Border Radius**: 8px top corners

### Body (with Markdown Support)
- **Background**: White
- **Text**: Dark gray/black, left-aligned
- **Font**: Montserrat, regular weight
- **Font Size**: 1rem (16px)
- **Line Height**: 1.6 for readability
- **Padding**: 1.5rem for comfortable spacing
- **Markdown Styling**:
  - Bold: font-weight 600
  - Italic: font-style italic
  - Links: #275ed8 color, underlined, open in new tab
  - Code: gray background (#f5f5f5), monospace, slightly smaller
  - Lists: 1.5rem left margin, disc bullets, 0.5rem top/bottom spacing
  - Headers: Montserrat 600, appropriate sizing (h4/h5)
  - All HTML escaped first for security

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

/* Markdown Elements */
strong { font-weight: 600; }
em { font-style: italic; }
a { color: #275ed8; text-decoration: underline; }
code { background-color: #f5f5f5; padding: 2px 6px; border-radius: 3px; }
ul { margin: 0.5rem 0; padding-left: 1rem; list-style-type: disc; }
li { margin-left: 1.5rem; }
h4, h5 { font-family: 'Montserrat', sans-serif; font-weight: 600; }
```

## Markdown Styling Guide

### Supported Syntax

| Markdown | Rendered As | Styling |
|----------|-------------|---------|
| `**text**` | **Bold** | font-weight: 600 |
| `*text*` | *Italic* | font-style: italic |
| `[text](url)` | Link | color: #275ed8, underline, new tab |
| `` `code` `` | Code | gray bg, monospace, smaller |
| `- item` | • List item | proper indentation, disc bullet |
| `## Header` | Header | Montserrat 600, larger size |

### Security Features
- All user input is HTML-escaped first
- Markdown parsing happens after escaping
- No raw HTML allowed in notifications
- Links automatically get `rel="noopener noreferrer"`
- XSS protection maintained throughout

### Best Practices for Notifications

**Do:**
- Use **bold** for important information
- Use lists for multiple items
- Include [links] for additional resources
- Keep headers concise (## or ###)
- Use `code` for technical terms

**Don't:**
- Overuse formatting (keep it clean)
- Create very long lists (max 5 items)
- Use too many nested formats
- Write overly long messages (300 chars max recommended)

## Testing Scenarios

Use the test page at `/test-notification.html` to verify:
- ✅ Styling matches site design (#275ed8 color)
- ✅ Montserrat font loads correctly
- ✅ Button hover effects work
- ✅ Modal appears centered
- ✅ Responsive behavior on mobile
- ✅ "Home Page Popup" setting is used (not "Home Page Announcement")
- ✅ Markdown formatting renders correctly
- ✅ Links open in new tabs
- ✅ Code blocks have proper background
- ✅ Lists are properly indented
