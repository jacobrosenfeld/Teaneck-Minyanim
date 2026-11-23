package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.enums.EventSource;
import com.tbdev.teaneckminyanim.enums.Nusach;
import com.tbdev.teaneckminyanim.minyan.MinyanType;
import com.tbdev.teaneckminyanim.model.CalendarEvent;
import com.tbdev.teaneckminyanim.model.Location;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.repo.CalendarEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The single place where manual overrides (calendar events with source MANUAL) are created, edited, toggled,
 * deleted and listed. The admin panel controllers and the admin API controller are thin wrappers around it.
 *
 * <p>Every public method authorizes the caller itself ({@link TNMUserService#canAccessOrganization}), validates
 * input and throws exceptions whose messages are safe to show to users:
 * <ul>
 *   <li>{@link AccessDeniedException}: the caller may not manage this shul</li>
 *   <li>{@link IllegalArgumentException}: invalid input</li>
 *   <li>{@link OverrideNotFoundException}: the shul or event does not exist, or the event belongs to another shul</li>
 *   <li>{@link NotManualOverrideException}: the event is a RULES or IMPORTED row</li>
 * </ul>
 *
 * <p>Manual rows are read live by {@link EffectiveScheduleService} (nothing caches them), so a change is visible in
 * the public schedule immediately and no rematerialization is needed.
 *
 * <p>The override mode is stored in {@code sourceRef} as {@code manual:FULL_DAY_REPLACE:<uuid>} or
 * {@code manual:ADDITIVE:<uuid>}. Creating a FULL_DAY_REPLACE row does not delete other MANUAL rows on that date; the
 * precedence logic in EffectiveScheduleService simply shows only MANUAL rows on a day that has a full-day replace row.
 *
 * <p>The {@code ...AsSuperAdmin} variants skip the per-shul check and instead require a super admin. They exist for
 * the super-admin overrides page; the API controller never calls them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManualOverrideService {

    public static final String MODE_ADDITIVE = "ADDITIVE";
    public static final String MODE_FULL_DAY_REPLACE = "FULL_DAY_REPLACE";
    public static final int MAX_RANGE_DAYS = 62;
    public static final int MAX_NOTES_LENGTH = 1000;
    public static final int MAX_LOCATION_NAME_LENGTH = 255;

    private final CalendarEventRepository calendarEventRepository;
    private final OrganizationService organizationService;
    private final LocationService locationService;
    private final TNMUserService userService;
    private final SuperAdminOverrideXlsxService overrideXlsxService;

    /**
     * New override(s) for the inclusive range {@code date}..{@code endDate} (endDate defaults to date).
     * String fields are parsed and validated here so every caller gets the same rules.
     */
    public record CreateOverrideCommand(
            LocalDate date,
            LocalDate endDate,
            LocalTime startTime,
            String minyanType,
            String overrideMode,
            String locationId,
            String locationName,
            String notes,
            String nusach,
            Boolean enabled) {}

    /**
     * Partial update of one MANUAL row. A null field means "leave unchanged". For notes, locationName and nusach a
     * blank string clears the value (nusach falls back to the shul's nusach). A blank locationId clears both the
     * location and its name.
     */
    public record UpdateOverrideCommand(
            LocalTime startTime,
            String overrideMode,
            String notes,
            String locationId,
            String locationName,
            String nusach,
            Boolean enabled) {}

    /** Rows written by {@link #create}, with how many were new versus updated in place. */
    public record CreateResult(List<CalendarEvent> events, int created, int updated) {}

    // ---------------------------------------------------------------------------------------------------------
    // create
    // ---------------------------------------------------------------------------------------------------------

    @Transactional
    public CreateResult create(String orgId, CreateOverrideCommand cmd, String editedBy) {
        requireAccess(orgId);
        return createInternal(orgId, cmd, editedBy);
    }

    /** Same as {@link #create} for the super-admin page. */
    @Transactional
    public CreateResult createAsSuperAdmin(String orgId, CreateOverrideCommand cmd, String editedBy) {
        requireSuperAdmin();
        return createInternal(orgId, cmd, editedBy);
    }

    private CreateResult createInternal(String orgId, CreateOverrideCommand cmd, String editedBy) {
        if (cmd == null) {
            throw new IllegalArgumentException("Override details are required");
        }
        Organization org = organizationService.findById(orgId)
                .orElseThrow(() -> new OverrideNotFoundException("Shul not found"));

        if (cmd.date() == null) {
            throw new IllegalArgumentException("Date is required");
        }
        LocalDate rangeStart = cmd.date();
        LocalDate rangeEnd = cmd.endDate() != null ? cmd.endDate() : rangeStart;
        checkRange(rangeStart, rangeEnd);
        if (cmd.startTime() == null) {
            throw new IllegalArgumentException("Start time is required");
        }
        MinyanType type = parseType(cmd.minyanType());
        String mode = parseMode(cmd.overrideMode(), MODE_ADDITIVE);
        String notes = checkedNotes(trimToNull(cmd.notes()));
        ResolvedLocation location = resolveLocation(orgId, cmd.locationId(), trimToNull(cmd.locationName()));
        Nusach nusach = parseNusach(cmd.nusach(), org.getNusach());
        boolean enabled = cmd.enabled() == null || cmd.enabled();
        String user = editedByOrDefault(editedBy);

        List<CalendarEvent> saved = new ArrayList<>();
        int created = 0;
        int updated = 0;
        for (LocalDate d = rangeStart; !d.isAfter(rangeEnd); d = d.plusDays(1)) {
            SuperAdminOverrideXlsxService.UpsertedManualEvent row = overrideXlsxService.upsertManualRow(
                    orgId, d, cmd.startTime(), type, mode,
                    location.id(), location.name(), notes, nusach, enabled, user);
            if (row.existed()) {
                updated++;
            } else {
                created++;
            }
            saved.add(row.event());
        }
        return new CreateResult(saved, created, updated);
    }

    // ---------------------------------------------------------------------------------------------------------
    // update / toggle / delete / read
    // ---------------------------------------------------------------------------------------------------------

    /** Edits one MANUAL row. Usable for single-row editing in the admin UI as well as the API. */
    @Transactional
    public CalendarEvent update(String orgId, String eventId, UpdateOverrideCommand cmd, String editedBy) {
        requireAccess(orgId);
        CalendarEvent event = requireManual(orgId, parseId(eventId));
        if (cmd == null) {
            throw new IllegalArgumentException("Nothing to update");
        }

        if (cmd.startTime() != null) {
            event.setStartTime(cmd.startTime());
        }
        if (cmd.overrideMode() != null) {
            String mode = parseMode(cmd.overrideMode(), null);
            event.setSourceRef("manual:" + mode + ":" + UUID.randomUUID());
        }
        if (cmd.notes() != null) {
            event.setNotes(checkedNotes(trimToNull(cmd.notes())));
        }
        if (cmd.locationId() != null) {
            if (cmd.locationId().isBlank()) {
                event.setLocationId(null);
                event.setLocationName(null);
            } else {
                ResolvedLocation location = resolveLocation(orgId, cmd.locationId(), null);
                event.setLocationId(location.id());
                event.setLocationName(location.name());
            }
        }
        if (cmd.locationName() != null) {
            String name = trimToNull(cmd.locationName());
            if (name != null && name.length() > MAX_LOCATION_NAME_LENGTH) {
                throw new IllegalArgumentException("Location name is too long");
            }
            // A blank name never undoes a location chosen in the same request.
            if (name != null || cmd.locationId() == null) {
                event.setLocationName(name);
            }
        }
        if (cmd.nusach() != null) {
            Nusach orgDefault = organizationService.findById(orgId).map(Organization::getNusach).orElse(null);
            event.setNusach(parseNusach(cmd.nusach(), orgDefault));
        }
        if (cmd.enabled() != null) {
            event.setEnabled(cmd.enabled());
        }
        stamp(event, editedBy);
        return calendarEventRepository.save(event);
    }

    @Transactional
    public CalendarEvent setEnabled(String orgId, String eventId, boolean enabled, String editedBy) {
        requireAccess(orgId);
        return applyEnabled(requireManual(orgId, parseId(eventId)), enabled, editedBy);
    }

    /** Flips enabled on one MANUAL row. */
    @Transactional
    public CalendarEvent toggle(String orgId, String eventId, String editedBy) {
        requireAccess(orgId);
        CalendarEvent event = requireManual(orgId, parseId(eventId));
        return applyEnabled(event, !event.isEnabled(), editedBy);
    }

    /** Flips enabled on a MANUAL row of any shul. Super-admin page only. */
    @Transactional
    public CalendarEvent toggleAsSuperAdmin(Long eventId, String editedBy) {
        requireSuperAdmin();
        CalendarEvent event = requireManual(null, eventId);
        return applyEnabled(event, !event.isEnabled(), editedBy);
    }

    @Transactional
    public void delete(String orgId, String eventId) {
        requireAccess(orgId);
        calendarEventRepository.delete(requireManual(orgId, parseId(eventId)));
    }

    /** Deletes a MANUAL row of any shul. Super-admin page only. */
    @Transactional
    public void deleteAsSuperAdmin(Long eventId) {
        requireSuperAdmin();
        calendarEventRepository.delete(requireManual(null, eventId));
    }

    /** MANUAL rows of one shul in an inclusive date range of at most {@value #MAX_RANGE_DAYS} days. */
    @Transactional(readOnly = true)
    public List<CalendarEvent> list(String orgId, LocalDate start, LocalDate end) {
        requireAccess(orgId);
        if (start == null || end == null) {
            throw new IllegalArgumentException("start and end dates are required");
        }
        checkRange(start, end);
        return calendarEventRepository
                .findByOrganizationIdAndSourceAndDateBetween(orgId, EventSource.MANUAL, start, end)
                .stream()
                .sorted(Comparator.comparing(CalendarEvent::getDate)
                        .thenComparing(CalendarEvent::getStartTime)
                        .thenComparing(CalendarEvent::getId))
                .toList();
    }

    /** FULL_DAY_REPLACE or ADDITIVE, decoded from the row's sourceRef. */
    public static String modeOf(CalendarEvent event) {
        String ref = event.getSourceRef();
        return ref != null && ref.startsWith(EffectiveScheduleService.MANUAL_FULL_DAY_SOURCE_REF_PREFIX)
                ? MODE_FULL_DAY_REPLACE
                : MODE_ADDITIVE;
    }

    // ---------------------------------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------------------------------

    private CalendarEvent applyEnabled(CalendarEvent event, boolean enabled, String editedBy) {
        event.setEnabled(enabled);
        stamp(event, editedBy);
        return calendarEventRepository.save(event);
    }

    private static void stamp(CalendarEvent event, String editedBy) {
        event.setManuallyEdited(true);
        event.setEditedBy(editedByOrDefault(editedBy));
        event.setEditedAt(LocalDateTime.now());
    }

    private static String editedByOrDefault(String editedBy) {
        return editedBy == null || editedBy.isBlank() ? "system" : editedBy;
    }

    private static void checkRange(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("A date range can span at most " + MAX_RANGE_DAYS + " days");
        }
    }

    private void requireAccess(String orgId) {
        if (orgId == null || !userService.canAccessOrganization(orgId)) {
            throw new AccessDeniedException("Not authorized to manage overrides for this shul");
        }
    }

    private void requireSuperAdmin() {
        if (!userService.isSuperAdmin()) {
            throw new AccessDeniedException("This action is restricted to super admins");
        }
    }

    private static Long parseId(String eventId) {
        try {
            return Long.valueOf(eventId == null ? "" : eventId.trim());
        } catch (NumberFormatException e) {
            throw new OverrideNotFoundException("Event not found");
        }
    }

    /** The MANUAL row with this id; {@code orgId == null} skips the shul check (callers must be super admin). */
    private CalendarEvent requireManual(String orgId, Long eventId) {
        CalendarEvent event = eventId == null ? null : calendarEventRepository.findById(eventId).orElse(null);
        if (event == null || (orgId != null && !orgId.equals(event.getOrganizationId()))) {
            throw new OverrideNotFoundException("Event not found");
        }
        if (event.getSource() != EventSource.MANUAL) {
            throw new NotManualOverrideException("Only manual overrides can be changed here");
        }
        return event;
    }

    private static MinyanType parseType(String value) {
        try {
            return MinyanType.valueOf(value == null ? "" : value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid minyan type");
        }
    }

    private static String parseMode(String value, String defaultMode) {
        if (value == null || value.isBlank()) {
            if (defaultMode == null) {
                throw new IllegalArgumentException("Invalid override mode (use ADDITIVE or FULL_DAY_REPLACE)");
            }
            return defaultMode;
        }
        String normalized = value.trim().toUpperCase(Locale.US);
        if (!MODE_ADDITIVE.equals(normalized) && !MODE_FULL_DAY_REPLACE.equals(normalized)) {
            throw new IllegalArgumentException("Invalid override mode (use ADDITIVE or FULL_DAY_REPLACE)");
        }
        return normalized;
    }

    private static Nusach parseNusach(String value, Nusach fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Nusach.valueOf(value.trim().toUpperCase(Locale.US));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid nusach");
        }
    }

    private static String checkedNotes(String notes) {
        if (notes != null && notes.length() > MAX_NOTES_LENGTH) {
            throw new IllegalArgumentException("Notes are too long (max " + MAX_NOTES_LENGTH + " characters)");
        }
        return notes;
    }

    private record ResolvedLocation(String id, String name) {}

    /** A chosen location must belong to the shul; its name is used unless a name was given explicitly. */
    private ResolvedLocation resolveLocation(String orgId, String locationId, String locationName) {
        if (locationName != null && locationName.length() > MAX_LOCATION_NAME_LENGTH) {
            throw new IllegalArgumentException("Location name is too long");
        }
        if (locationId == null || locationId.isBlank()) {
            return new ResolvedLocation(null, locationName);
        }
        Location selected = locationService.findById(locationId.trim());
        if (selected == null || !orgId.equals(selected.getOrganizationId())) {
            throw new IllegalArgumentException("Invalid location for this shul");
        }
        return new ResolvedLocation(selected.getId(), locationName != null ? locationName : selected.getName());
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
