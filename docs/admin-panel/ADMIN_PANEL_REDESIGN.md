# Admin Panel Redesign - Design Document

## Overview

This document outlines the redesign of the Teaneck Minyanim admin panel to support:
1. New materialized calendar_events architecture (Issue #79, PR #98)
2. Enhanced settings management (Issue #70, PR #93 - merged)
3. Improved UX and modern design

## Current State Analysis

### Existing Admin Templates
- `accounts.html` - User account management
- `calendar-entries.html` - Imported calendar management (v1.2.2, modern design)
- `footer.html` - Admin footer component
- `locations.html` - Location management
- `login.html` - Admin login page
- `minyanim/new.html` - Create new minyan
- `minyanim/update.html` - Edit existing minyan
- `navbar.html` - Top navigation bar
- `new-organization.html` - Create organization
- `organization.html` - Organization profile/settings
- `organizations.html` - List all organizations (super admin)
- `settings.html` - Application settings
- `sidebar.html` - Left navigation sidebar

### Existing Static Assets
**CSS:**
- `dashboard.css` - Empty file
- `minyanschedule.css` - Minyan schedule styling
- `sidebar.css` - Sidebar styling
- `styles.css` - Common admin styles

**JavaScript:**
- `confirm-password.js` - Password confirmation
- `login.js` - Login functionality
- `minyanim/update.js` - Update minyan form
- `minyanschedule.js` - Schedule management
- `new-minyan.js` - New minyan form
- `organization.js` - Organization management
- `sidebar.js` - Sidebar interactions
- `timezone.js` - Timezone handling

### Current Navigation Structure (sidebar.html)

**Super Admin:**
- Organizations
- Create an Organization
- Accounts
- Application Settings
- Logout

**Organization Admin:**
- Locations
- Minyan Schedule
- My Organization
- My Account
- Logout

### UI Framework
- **Bootstrap 4.6.1** - Current framework
- **jQuery 3.6.3** - JavaScript library
- **Popper.js 1.16.0** - Tooltip positioning
- **Thymeleaf** - Template engine with Spring Security integration

## Design Decisions

### Framework Choice: Bootstrap 4.6 (Keep Current)
**Rationale:**
- Already integrated and working well
- Minimal changes required (following requirements)
- Familiar to maintainers
- Good browser support and responsive design
- v1.2.2 calendar-entries page demonstrates it can look modern

**Alternative Considered:**
- Tailwind CSS - Would require major overhaul, not justified for scope
- Material Design - Heavy framework, overkill for admin panel
- Custom CSS - Would take significant time, Bootstrap adequate

### Color Scheme
- **Primary**: #035174 (existing teal blue)
- **Success**: #28a745 (Bootstrap green)
- **Info**: #17a2b8 (Bootstrap cyan)
- **Warning**: #ffc107 (Bootstrap yellow)
- **Danger**: #dc3545 (Bootstrap red)
- **Secondary**: #6c757d (Bootstrap gray)

### Typography
- Keep existing: System fonts with Bootstrap defaults
- Headers: Keep existing styling
- Body: Keep 0.9rem - 1rem range

## New Features Required

### 1. Calendar Events Management (PR #98 Integration)
**Note**: This is the key integration point with the new architecture from issue #79.

**New Page:** `/admin/{orgId}/calendar-events`

**Features:**
- **View Types:**
  - Agenda view (default) - List grouped by date
  - Calendar view - Month/week grid display
  
- **Filters:**
  - Organization (super admin only)
  - Date range (past 3 weeks, next 8 weeks)
  - Minyan type (Shacharis, Mincha, Maariv, etc.)
  - Source (IMPORTED, RULES, MANUAL)
  - Enabled status (all, enabled only, disabled only)
  
- **Actions:**
  - Enable/disable toggle
  - Edit inline (notes, location)
  - Delete (manual events only)
  - Trigger rematerialization
  
- **Visual Indicators:**
  - Source badges with color coding:
    - IMPORTED: Green (#28a745)
    - RULES: Blue (#007bff)
    - MANUAL: Orange (#fd7e14)
  - Type badges (Shacharis, Mincha, Maariv, etc.)
  - Enabled/disabled status badges

### 2. Enhanced Navigation

**Sidebar Updates:**

**Super Admin:**
- Organizations
- **Calendar Management** (new section)
  - All Calendar Events
  - Materialization Status
- Create an Organization
- Accounts
- Application Settings
- Logout

**Organization Admin:**
- Locations
- Minyan Schedule (rule-based)
- **Calendar Events** (new)
- My Organization
- My Account
- Logout

### 3. Settings Integration
- Settings page already redesigned in PR #93
- Ensure it's accessible from sidebar
- Add link in organization profile
- Verify save/load works correctly

## Wireframes

### Calendar Events Page - Agenda View
```
+------------------------------------------------+
|  Calendar Events - [Organization Name]          |
+------------------------------------------------+
| [Filters ▼]                    [Calendar View] |
|                                                  |
| Date Range: [___] to [___]  Type: [All ▼]      |
| Source: [All ▼]  Status: [All ▼]  [Apply] [Clear] |
+------------------------------------------------+
|                                                  |
| Monday, January 6, 2025                         |
| +--------------------------------------------+  |
| | 7:00 AM          [RULES] [SHACHARIS]      |  |
| | Main Sanctuary                            |  |
| | [Edit] [Disable]                          |  |
| +--------------------------------------------+  |
| | 7:30 AM          [IMPORTED] [SHACHARIS]   |  |
| | Vasikin                                   |  |
| | Sunrise minyan                             |  |
| | [Edit] [Disable]                          |  |
| +--------------------------------------------+  |
|                                                  |
| Tuesday, January 7, 2025                        |
| +--------------------------------------------+  |
| | 7:00 AM          [RULES] [SHACHARIS]      |  |
| | Main Sanctuary                            |  |
| | [Edit] [Disable]                          |  |
| +--------------------------------------------+  |
+------------------------------------------------+
```

### Calendar Events Page - Calendar View
```
+------------------------------------------------+
|  Calendar Events - [Organization Name]          |
+------------------------------------------------+
| [Filters ▼]                     [Agenda View]   |
|                                                  |
| [<]          January 2025             [>]       |
+------------------------------------------------+
| Sun | Mon | Tue | Wed | Thu | Fri | Sat       |
+------------------------------------------------+
|     |  1  |  2  |  3  |  4  |  5  |  6        |
|     | 7AM | 7AM | 7AM | 7AM | 6:30| 7AM       |
|     | SH  | SH  | SH  | SH  | AM  | SH        |
|     |     |     |     |     | 8AM |           |
|     |     |     |     |     | SH  |           |
+------------------------------------------------+
|  7  |  8  |  9  | 10  | 11  | 12  | 13        |
| 7AM | 7AM | 7AM | 7AM | 7AM | 6:30| 8AM       |
| SH  | SH  | SH  | SH  | SH  | AM  | SH        |
|     |     |     |     |     | 8AM |           |
|     |     |     |     |     | SH  |           |
+------------------------------------------------+

Legend:
[Green pill] = IMPORTED
[Blue pill] = RULES
[Orange pill] = MANUAL
```

## Component Justification

### Why Agenda View as Default
- Most common use case is checking "what's coming up"
- Easier to scan chronologically
- Better for mobile/responsive
- Can show more details per event

### Why Add Calendar View
- Requested in issue #79
- Visual at-a-glance view of entire month
- Helps identify gaps or conflicts
- Familiar UI pattern for users

### Why Color-Coded Source Badges
- Immediate visual distinction
- Helps admins understand data sources
- Aligns with materialization concept
- Green (imported) = highest priority in precedence
- Blue (rules) = fallback
- Orange (manual) = custom overrides

### Why Inline Editing
- Reduces clicks for common tasks
- Faster workflow for admins
- Already proven effective in calendar-entries.html
- Keeps user in context

## Implementation Plan

Based on my analysis, I'll take a **minimal-change approach**:

1. **Enhance existing calendar-entries.html** to become the unified calendar-events page
2. **Add calendar view component** alongside existing agenda-style table
3. **Update sidebar** to include calendar events navigation
4. **Integrate with PR #98** calendar_events table (when merged)
5. **Verify settings page** works correctly

This approach minimizes code changes while delivering the required functionality.

## Next Steps

1. ✅ Created design document
2. Update progress report
3. Begin implementation of calendar view component
4. Test with sample data
5. Take screenshots for documentation
