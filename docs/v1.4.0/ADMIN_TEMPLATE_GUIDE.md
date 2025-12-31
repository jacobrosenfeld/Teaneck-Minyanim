# Admin Template Guide - Teaneck Minyanim v1.4.0

## Overview

This guide documents the admin template structure introduced in v1.4.0 for the materialized calendar events system. The design is inspired by **Backpack for Laravel**, emphasizing clean data tables, intuitive filtering, and consistent UI patterns.

## Design Philosophy

### Backpack for Laravel Inspiration

The admin UI is inspired by Backpack for Laravel's principles:

1. **Data-Centric Tables**: Admin pages focus on clean, sortable, filterable tables for managing entities
2. **Inline Actions**: Edit/delete actions are inline in tables for quick access
3. **Filter Panels**: Collapsible filter panels above tables for querying data
4. **Statistics Dashboards**: Summary cards showing key metrics at a glance
5. **Consistent Patterns**: Reusable patterns across all CRUD operations
6. **Color-Coded Badges**: Visual indicators for status, type, source using color
7. **Modern Design System**: CSS variables for colors, spacing, typography

### Core Principles

- **Clarity over decoration**: Clean, professional, no unnecessary embellishments
- **Data visibility**: Tables as first-class citizens with excellent readability
- **Quick actions**: Common tasks accessible without navigation
- **Responsive design**: Works on desktop, tablet, mobile
- **Performance**: Minimal JavaScript, fast page loads

## Design System

### CSS Variables (Design Tokens)

All admin pages use the design system defined in `/static/admin/design-system.css`:

```css
/* Colors */
--color-primary: #275ed8;        /* Primary brand color */
--color-secondary: #6c757d;      /* Secondary actions */
--color-accent: #28a745;         /* Success/enabled */
--color-warning: #ffc107;        /* Warnings */
--color-danger: #dc3545;         /* Errors/delete */
--color-info: #17a2b8;          /* Info messages */

/* Neutral Grays (9-step scale) */
--color-gray-50: #f8f9fa;
--color-gray-100: #f1f3f5;
--color-gray-200: #e9ecef;
--color-gray-300: #dee2e6;
--color-gray-400: #ced4da;
--color-gray-500: #adb5bd;
--color-gray-600: #6c757d;
--color-gray-700: #495057;
--color-gray-800: #343a40;
--color-gray-900: #212529;

/* Spacing (12-step scale, 8px base) */
--space-1: 0.25rem;  /* 4px */
--space-2: 0.5rem;   /* 8px */
--space-3: 0.75rem;  /* 12px */
--space-4: 1rem;     /* 16px */
--space-5: 1.25rem;  /* 20px */
--space-6: 1.5rem;   /* 24px */
--space-8: 2rem;     /* 32px */
--space-10: 2.5rem;  /* 40px */
--space-12: 3rem;    /* 48px */
--space-16: 4rem;    /* 64px */

/* Typography */
--font-family-base: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto;
--font-family-mono: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas;

--font-size-xs: 0.75rem;   /* 12px */
--font-size-sm: 0.875rem;  /* 14px */
--font-size-base: 1rem;    /* 16px */
--font-size-lg: 1.125rem;  /* 18px */
--font-size-xl: 1.25rem;   /* 20px */
--font-size-2xl: 1.5rem;   /* 24px */
--font-size-3xl: 1.875rem; /* 30px */
--font-size-4xl: 2.25rem;  /* 36px */

/* Border Radius */
--radius-sm: 0.25rem;  /* 4px */
--radius-md: 0.375rem; /* 6px */
--radius-lg: 0.5rem;   /* 8px */
--radius-xl: 0.75rem;  /* 12px */
--radius-full: 9999px; /* Pills/circles */

/* Shadows */
--shadow-sm: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
--shadow-md: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
--shadow-lg: 0 10px 15px -3px rgba(0, 0, 0, 0.1);
--shadow-xl: 0 20px 25px -5px rgba(0, 0, 0, 0.1);
```

For complete details, see `/src/main/resources/static/admin/design-system.css`

## Component Patterns

### Statistics Cards

Display key metrics at the top of admin pages using a responsive grid:

```html
<div class="stats-grid">
    <div class="stat-card-modern">
        <div class="stat-number">127</div>
        <div class="stat-label">Total Events</div>
    </div>
    <div class="stat-card-modern">
        <div class="stat-number">115</div>
        <div class="stat-label">Enabled</div>
    </div>
    <div class="stat-card-modern">
        <div class="stat-number">89</div>
        <div class="stat-label">Rules-Based</div>
    </div>
</div>
```

### Filter Panel (Collapsible)

Provide advanced filtering without cluttering the interface:

```html
<div class="filter-panel">
    <div class="filter-header" onclick="toggleFilters()">
        <div class="filter-title">
            <i class="fas fa-filter"></i>
            <span>Filters</span>
        </div>
        <i class="fas fa-chevron-down filter-chevron"></i>
    </div>
    <div class="filter-body" id="filterBody">
        <form method="get">
            <div class="filter-row">
                <div class="form-group-modern">
                    <label>Start Date</label>
                    <input type="date" name="startDate" class="form-control-modern" />
                </div>
                <div class="form-group-modern">
                    <label>Type</label>
                    <select name="type" class="form-control-modern">
                        <option value="">All Types</option>
                    </select>
                </div>
            </div>
            <button type="submit" class="btn-primary-modern">Apply</button>
        </form>
    </div>
</div>
```

### Data Table

Clean, responsive tables with sticky headers and hover effects:

```html
<div class="table-wrapper">
    <div class="table-container">
        <table class="events-table">
            <thead>
                <tr>
                    <th>Date</th>
                    <th>Type</th>
                    <th>Source</th>
                    <th>Status</th>
                    <th>Actions</th>
                </tr>
            </thead>
            <tbody>
                <tr th:each="event : ${events}">
                    <td th:text="${#temporals.format(event.date, 'MMM dd, yyyy')}"></td>
                    <td>
                        <span class="badge-event badge-shacharis" 
                              th:text="${event.minyanType}"></span>
                    </td>
                    <td>
                        <span class="badge-event badge-rules" 
                              th:if="${event.source == 'RULES'}"
                              th:text="${event.source}"></span>
                    </td>
                    <td>
                        <span class="badge-event badge-enabled" 
                              th:if="${event.enabled}">Enabled</span>
                    </td>
                    <td>
                        <button class="action-btn btn-toggle">Toggle</button>
                        <button class="action-btn btn-edit">Edit</button>
                    </td>
                </tr>
            </tbody>
        </table>
    </div>
</div>
```

### Color-Coded Badges

Visual indicators with semantic colors:

```html
<!-- Source badges -->
<span class="badge-event badge-rules">Rules</span>
<span class="badge-event badge-imported">Imported</span>
<span class="badge-event badge-manual">Manual</span>

<!-- Type badges -->
<span class="badge-event badge-shacharis">Shacharis</span>
<span class="badge-event badge-mincha">Mincha</span>
<span class="badge-event badge-maariv">Maariv</span>

<!-- Status badges -->
<span class="badge-event badge-enabled">Enabled</span>
<span class="badge-event badge-disabled">Disabled</span>
```

**Badge Color Reference:**
- **Blue** (#e3f2fd / #1976d2): Rules-based events, Shacharis
- **Green** (#e8f5e9 / #388e3c): Imported events, Enabled status
- **Orange** (#fff3e0 / #f57c00): Manual events
- **Amber** (#ffe0b2 / #e65100): Mincha
- **Purple** (#d1c4e9 / #4a148c): Maariv
- **Gray** (#adb5bd): Disabled status

## Template Structure

### Standard Admin Page

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.w3.org/1999/xhtml">
<head>
    <meta charset="UTF-8">
    <title th:text="@{|Page Title - ${organization.name} - ${siteName}|}"></title>
    
    <!-- Favicon set -->
    <link rel="icon" type="image/svg+xml" href="/assets/icons/favicon.svg">
    <link rel="apple-touch-icon" sizes="180x180" 
          href="/assets/icons/apple-touch-icon.png">
    
    <!-- Stylesheets -->
    <link th:rel="stylesheet" 
          th:href="@{/webjars/bootstrap/4.6.1/css/bootstrap.min.css}"/>
    <link th:rel="stylesheet" th:href="@{/admin/styles.css}">
    <link rel="stylesheet" 
          href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0/css/all.min.css"/>
</head>
<body>
    <!-- Include navbar -->
    <div th:replace="admin/navbar :: navbar"></div>
    
    <div class="admin-layout">
        <!-- Include sidebar -->
        <div th:replace="admin/sidebar :: sidebar"></div>
        
        <!-- Main content area -->
        <main class="admin-main">
            <div class="admin-header">
                <h1 class="display-4">Page Title</h1>
                <p class="lead">
                    Manage resources for <span th:text="${siteName}"></span>.
                </p>
            </div>
            
            <div class="admin-content">
                <!-- Statistics -->
                <div class="stats-grid">
                    <!-- stat cards here -->
                </div>
                
                <!-- Filter Panel -->
                <div class="filter-panel">
                    <!-- filters here -->
                </div>
                
                <!-- Data Table -->
                <div class="table-wrapper">
                    <!-- table here -->
                </div>
            </div>
        </main>
    </div>
    
    <!-- Scripts -->
    <script th:src="@{/webjars/jquery/3.6.0/jquery.min.js}"></script>
    <script th:src="@{/webjars/bootstrap/4.6.1/js/bootstrap.bundle.min.js}"></script>
</body>
</html>
```

## Controller Pattern

### Settings Integration

Always inject ApplicationSettingsService for branding:

```java
@Controller
@RequiredArgsConstructor
public class MyAdminController {
    
    private final ApplicationSettingsService settingsService;
    private final TNMUserService userService;
    
    // Make siteName available to all views
    @ModelAttribute("siteName")
    public String siteName() {
        return settingsService.getSetting("SITE_NAME")
            .map(TNMSettings::getText)
            .orElse("Teaneck Minyanim");
    }
    
    @ModelAttribute("supportEmail")
    public String supportEmail() {
        return settingsService.getSetting("SUPPORT_EMAIL")
            .map(TNMSettings::getText)
            .orElse("support@example.com");
    }
    
    @GetMapping("/admin/{orgId}/my-page")
    public ModelAndView viewPage(@PathVariable String orgId) {
        // Authorization check
        if (!userService.canAccessOrganization(orgId)) {
            throw new AccessDeniedException("Access denied");
        }
        
        // Query data, calculate stats
        // ...
        
        ModelAndView mav = new ModelAndView("admin/my-page");
        mav.addObject("organization", org);
        mav.addObject("user", userService.getCurrentUser());
        // siteName and supportEmail automatically available
        
        return mav;
    }
}
```

## Creating New Admin Pages

### Step-by-Step Guide

1. **Create Controller**:
   - Add `@ModelAttribute` for siteName and supportEmail
   - Implement authorization with `userService.canAccessOrganization()`
   - Query data with filters
   - Calculate statistics
   - Return ModelAndView

2. **Create Template**:
   - Copy structure from `calendar-events.html`
   - Update title to include `${organization.name}` and `${siteName}`
   - Include navbar and sidebar fragments
   - Add H1 with `display-4` class
   - Add lead paragraph referencing `${siteName}`
   - Create statistics grid
   - Add collapsible filter panel
   - Build data table

3. **Add to Sidebar** (if needed):
   - Edit `src/main/resources/templates/admin/sidebar.html`
   - Add link with Font Awesome icon
   - Use `th:classappend="${#strings.contains(#httpServletRequest.requestURI, '/my-page')} ? 'active' : ''"`

4. **Test**:
   - Verify authorization works
   - Test on desktop, tablet, mobile
   - Verify statistics calculate correctly
   - Test filters
   - Check responsive table scrolling

## Best Practices

### 1. Always Use Design Tokens

❌ **Don't:**
```css
.my-card {
    padding: 20px;
    background: #f8f9fa;
    border-radius: 8px;
}
```

✅ **Do:**
```css
.my-card {
    padding: var(--space-5);
    background: var(--color-gray-50);
    border-radius: var(--radius-lg);
}
```

### 2. Consistent Badge Colors

Use semantic badge colors:
- **Blue**: Rules-based, Shacharis, primary info
- **Green**: Imported, enabled, success
- **Orange**: Manual, warnings
- **Amber**: Mincha
- **Purple**: Maariv
- **Gray**: Disabled

### 3. Statistics First

Always show key metrics at the top using stat cards.

### 4. Collapsible Filters

Keep tables prominent while providing advanced filtering.

### 5. Responsive Tables

Wrap tables in `.table-wrapper` and `.table-container` for mobile scrolling.

### 6. Authorization Checks

Every controller method must verify access before displaying data.

### 7. Settings Integration

Inject siteName and supportEmail for consistent branding.

## Testing Checklist

- [ ] Page loads without errors
- [ ] Title includes siteName
- [ ] Navbar renders correctly
- [ ] Sidebar renders with active state
- [ ] Statistics display correct counts
- [ ] Filter panel is collapsible
- [ ] Table renders properly
- [ ] Badges use correct colors
- [ ] Actions work (edit, delete, toggle)
- [ ] Empty state displays when no data
- [ ] Responsive on desktop, tablet, mobile
- [ ] Authorization prevents unauthorized access

## Resources

- **Design System**: `/static/admin/design-system.css`
- **Example Template**: `/templates/admin/calendar-events.html`
- **Example Controller**: `/controllers/CalendarEventsAdminController.java`
- **Backpack for Laravel**: https://backpackforlaravel.com/ (inspiration)
