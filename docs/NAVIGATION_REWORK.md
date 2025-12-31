# Admin Navigation Rework - v1.6.0

## Overview

The admin panel navigation has been completely redesigned to provide a more intuitive and efficient user experience, inspired by Laravel Backpack's admin panel patterns. The new navigation separates global Super Admin controls from organization-specific management tools.

## Navigation Architecture

### For Super Admins

#### 1. Top Navbar (Global Controls)
Located at the top of the screen, the navbar provides quick access to:

- **Organizations Dropdown**
  - Searchable list of all organizations
  - Click to enter organization context
  - "New Organization" action at bottom of dropdown
  
- **Settings** - Application-wide settings
- **Accounts** - Global user account management
- **Notifications** - System notifications

#### 2. Organization Context Sidebar
When a Super Admin selects an organization (via dropdown or direct URL), they see:

- **Organization name as section title**
- **Dashboard** - Organization overview (currently redirects to minyanim)
- **Minyan Schedule** - Manage prayer services
- **Locations** - Manage organization locations
- **Calendar Entries** - Manage imported calendar events
- **Profile & Accounts** - Organization settings and user accounts

**Organization Actions** (Super Admin only):
- **Delete Organization** - Remove organization (with confirmation)

### For Organization Managers/Admins

Organization managers see **only** the organization-specific sidebar with:

- Their organization's tools (no global controls)
- Same org-level navigation as Super Admins
- No access to other organizations or global settings

## URL Structure

### New Standardized Routes

All organization-context pages now use the pattern `/admin/org/{orgId}/...`:

```
/admin/org/{orgId}/dashboard          → Organization dashboard
/admin/org/{orgId}/minyanim           → Minyan schedule management
/admin/org/{orgId}/locations          → Location management
/admin/org/{orgId}/calendar-entries   → Calendar entries management
```

### Legacy Routes (still supported)

The following legacy routes are maintained for backward compatibility:

```
/admin/{orgId}/minyanim               → Redirects to org context
/admin/{orgId}/locations              → Redirects to org context
/admin/{orgId}/calendar-entries       → Works directly
```

### Global Routes (Super Admin only)

```
/admin/organizations                  → List all organizations
/admin/new-organization               → Create new organization
/admin/settings                       → Application settings
/admin/accounts                       → User account management
/admin/notifications                  → System notifications
```

## User Flows

### Super Admin Flow

1. **Land on admin panel** → See Organizations list page
2. **Click Organizations dropdown** → See searchable list
3. **Select an organization** → Navigate to `/admin/org/{orgId}/dashboard`
4. **See org-specific sidebar** → Access all org management tools
5. **Switch orgs** → Use dropdown to select different organization

### Org Manager Flow

1. **Land on admin panel** → Automatically see their org's tools
2. **See org-specific sidebar** → Access organization management
3. **Navigate between tools** → Use sidebar links
4. **No org switching** → Limited to their assigned organization

## Visual Hierarchy

```
┌─────────────────────────────────────────────────────────────┐
│  NAVBAR (Super Admin Only)                                  │
│  [Logo] [Orgs ▼] [Settings] [Accounts] [Notifications]     │
└─────────────────────────────────────────────────────────────┘
┌──────────────────┬──────────────────────────────────────────┐
│  SIDEBAR         │  MAIN CONTENT                            │
│                  │                                          │
│  Org Context:    │  Selected page content displays here    │
│  ┌─────────────┐ │                                          │
│  │ Org Name    │ │  - Organizations list                   │
│  ├─────────────┤ │  - Minyan schedule                      │
│  │ Dashboard   │ │  - Locations                            │
│  │ Minyanim    │ │  - Calendar entries                     │
│  │ Locations   │ │  - Settings forms                       │
│  │ Calendar    │ │  - etc.                                 │
│  │ Profile     │ │                                          │
│  ├─────────────┤ │                                          │
│  │ Org Actions │ │                                          │
│  │ - Delete    │ │                                          │
│  ├─────────────┤ │                                          │
│  │ Account     │ │                                          │
│  │ - My Acct   │ │                                          │
│  │ - Logout    │ │                                          │
│  └─────────────┘ │                                          │
└──────────────────┴──────────────────────────────────────────┘
```

## Technical Implementation

### Key Components

1. **navbar.html**
   - Super Admin top navigation
   - Organizations dropdown with search
   - Smooth animations and transitions
   - Mobile responsive

2. **sidebar.html**
   - Context-aware navigation
   - Role-based visibility
   - Active link highlighting
   - Organization actions section

3. **AdminController.java**
   - New org-context route wrappers
   - Provides organizations list for dropdown
   - Permission validation
   - Delegates to existing methods

### Route Wrappers

```java
@RequestMapping(value = "/admin/org/{organizationId}/dashboard")
public ModelAndView orgDashboard(@PathVariable String organizationId) {
    return minyanim(organizationId, null, null);
}

@RequestMapping(value = "/admin/org/{organizationId}/minyanim")
public ModelAndView orgMinyanim(@PathVariable String organizationId, ...) {
    return minyanim(organizationId, successMessage, errorMessage);
}

// Similar wrappers for locations and calendar-entries
```

### Permission Checks

- All routes verify user has access to the organization
- Super Admins can access any organization
- Org Managers/Admins limited to their assigned organization
- Non-admin users redirected appropriately

## Design Features

### Dropdown Search

The organizations dropdown includes real-time search:

```javascript
// Filter organizations as user types
orgSearch.addEventListener('input', function(e) {
    const searchTerm = e.target.value.toLowerCase();
    orgItems.forEach(item => {
        const orgName = item.getAttribute('data-org-name');
        item.style.display = orgName.includes(searchTerm) ? 'block' : 'none';
    });
});
```

### Active Link Highlighting

Sidebar links automatically highlight based on current URL:

```html
<a th:classappend="${#strings.contains(#httpServletRequest.requestURI, '/minyanim')} ? 'active' : ''">
```

### Mobile Responsiveness

- Navbar collapses on small screens
- Sidebar becomes drawer on mobile
- Dropdown adjusts to screen width
- Touch-friendly tap targets

## Migration Notes

### Breaking Changes

None. All existing routes continue to work.

### Recommended Updates

For consistency, update links to use the new org-context pattern:

**Before:**
```html
<a href="/admin/{{ orgId }}/minyanim">Minyanim</a>
```

**After:**
```html
<a href="/admin/org/{{ orgId }}/minyanim">Minyanim</a>
```

### Backward Compatibility

Legacy routes are automatically supported:
- `/admin/{orgId}/minyanim` still works
- `/admin/{orgId}/locations` still works
- No code changes required for existing links

## Benefits

1. **Clearer Hierarchy** - Global vs org-specific controls clearly separated
2. **Faster Navigation** - Quick org switching via dropdown
3. **Better UX** - Context-aware sidebar shows relevant tools
4. **Consistent URLs** - Predictable route patterns
5. **Mobile Friendly** - Responsive design maintained
6. **Role-Based** - Appropriate controls for each user type
7. **Scalable** - Easy to add new org-level features

## Future Enhancements

Potential improvements for future versions:

1. **Org Dashboard** - Create proper dashboard page (currently redirects to minyanim)
2. **Breadcrumbs** - Add breadcrumb navigation showing current context
3. **Recent Orgs** - Show recently accessed organizations at top of dropdown
4. **Favorites** - Allow Super Admins to favorite frequently accessed orgs
5. **Quick Actions** - Add common actions to dropdown (import calendar, view schedule)
6. **Org Stats** - Show quick stats in dropdown (minyan count, locations, etc.)

## Testing Checklist

### Super Admin Tests
- [ ] Can see top navbar with dropdown
- [ ] Dropdown lists all organizations
- [ ] Search filters organizations correctly
- [ ] Can navigate to organization context
- [ ] Sees org-specific sidebar when in org context
- [ ] Can access Settings, Accounts, Notifications
- [ ] Can see Delete Organization action
- [ ] Can switch between organizations

### Org Manager Tests
- [ ] Does not see top navbar
- [ ] Sees org-specific sidebar immediately
- [ ] Cannot access global settings
- [ ] Cannot see other organizations
- [ ] Can manage their organization's tools
- [ ] Cannot see Delete Organization action

### Mobile Tests
- [ ] Navbar displays correctly on mobile
- [ ] Dropdown is accessible on small screens
- [ ] Sidebar becomes drawer on mobile
- [ ] All touch targets are properly sized
- [ ] Navigation remains functional

## Support

For questions or issues related to the navigation rework, please refer to:
- GitHub Issue: [Admin Navigation Rework](https://github.com/jacobrosenfeld/Teaneck-Minyanim/issues/[issue-number])
- Changelog: See v1.6.0 section in CHANGELOG.md
- Code: Review navbar.html, sidebar.html, and AdminController.java
