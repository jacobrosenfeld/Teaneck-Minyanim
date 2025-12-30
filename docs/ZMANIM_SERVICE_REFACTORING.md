# ZmanimService Refactoring Guide

## Current State
The ZmanimService currently has dual code paths:
1. Calendar import path (via OrgScheduleResolver/CalendarImportProvider)
2. Rule-based path (direct Minyan entity queries)

## Target State  
Single unified path using materialized calendar_events table.

## Required Changes

### 1. Inject New Services
```java
private final EffectiveScheduleService effectiveScheduleService;
private final CalendarEventAdapter calendarEventAdapter;
```

### 2. Replace getZmanim() Logic

**Current Pattern (lines 120-315):**
```java
for (Organization org : allOrganizations) {
    if (scheduleResolver.isCalendarImportEnabled(orgId)) {
        // Get from calendar import
    } else {
        // Get from rule-based minyanim
    }
}
```

**New Pattern:**
```java
// Check window
if (!effectiveScheduleService.isDateInWindow(localDateRef)) {
    // Show window message
    return mv;
}

// Get all events from materialized calendar
for (Organization org : allOrganizations) {
    List<CalendarEvent> calendarEvents = 
        effectiveScheduleService.getEffectiveEventsForDate(orgId, localDateRef);
    
    List<MinyanEvent> orgEvents = calendarEventAdapter.toMinyanEvents(calendarEvents);
    
    // Apply frontend filtering
    for (MinyanEvent event : orgEvents) {
        if (shouldDisplayEvent(event, date, now, terminationDate, zmanim)) {
            minyanEvents.add(event);
        }
    }
}
```

### 3. Replace KolhaMinyanim Generation (lines 304-400)

Same pattern - use materialized calendar instead of dual paths.

### 4. Add shouldDisplayEvent() Helper Method

```java
private boolean shouldDisplayEvent(MinyanEvent event, Date requestedDate, Date now, 
                                   Date terminationDate, Dictionary<Zman, Date> zmanim) {
    if (event.getStartTime() == null) return false;
    
    // Basic time filter
    if (!event.getStartTime().after(terminationDate) && sameDayOfMonth(now, requestedDate)) {
        return false;
    }
    
    // Type-specific filtering
    switch (event.getType()) {
        case SHACHARIS:
            return event.getStartTime().before(zmanim.get(Zman.SZT)) && 
                   event.getStartTime().after(zmanim.get(Zman.ALOS_HASHACHAR));
        case MINCHA:
            // Between MG and Shekiya
            return event.getStartTime().before(zmanim.get(Zman.SHEKIYA)) &&
                   event.getStartTime().after(/* MG-1min */);
        case MAARIV:
            // After Shekiya or contains "Plag"
            return event.getStartTime().after(/* Shekiya-1min */) ||
                   (event.dynamicTimeString() != null && event.dynamicTimeString().contains("Plag"));
        case SELICHOS:
            return zmanimHandler.isSelichosRecited(localDateRef);
        default:
            return true;
    }
}
```

### 5. Update org() Method (lines 442-700)

Similar refactoring:
- Check window with effectiveScheduleService.isDateInWindow()
- Use effectiveScheduleService.getEffectiveEventsForDate()
- Convert with calendarEventAdapter.toMinyanEvents()
- Apply same time-based filtering

### 6. Remove Dependencies

After refactoring:
- Remove `scheduleResolver` dependency (no longer needed)
- Keep `minyanService` only for admin operations (not frontend display)

## Testing Checklist

After completing refactoring:
- [ ] Homepage shows minyanim from materialized calendar
- [ ] Organization page shows minyanim from materialized calendar
- [ ] Time-based filtering still works (Shacharis/Mincha/Maariv windows)
- [ ] Out-of-window dates show friendly message
- [ ] Day-level precedence works (imported overrides rules)
- [ ] No duplicate events displayed
- [ ] KolhaMinyanim widget works correctly

## Rollback Plan

If issues arise:
1. Revert ZmanimService to backup
2. Materialization continues to run in background
3. Can debug without affecting user-facing site
4. Re-enable new frontend once issues resolved
