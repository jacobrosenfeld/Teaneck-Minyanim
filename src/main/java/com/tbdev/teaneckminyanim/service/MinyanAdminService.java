package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.minyan.MinyanDay;
import com.tbdev.teaneckminyanim.minyan.MinyanTime;
import com.tbdev.teaneckminyanim.model.Location;
import com.tbdev.teaneckminyanim.model.Minyan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin actions on minyan rules, shared by the admin panel and the admin API. Each public method checks that
 * the caller can access the shul, validates, saves, and syncs the materialized calendar, in one transaction.
 *
 * <p>Errors: {@link AccessDeniedException} (no access to the shul), {@link MinyanNotFoundException} (no such
 * minyan in that shul) and {@link IllegalArgumentException} (invalid input, with a message safe to show).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MinyanAdminService {
    static final int MAX_TEXT_LENGTH = 255;

    private final MinyanService minyanService;
    private final LocationService locationService;
    private final TNMUserService userService;
    private final CalendarMaterializationService calendarMaterializationService;

    /** The minyan does not exist, or it belongs to a different shul. */
    public static class MinyanNotFoundException extends RuntimeException {
        public MinyanNotFoundException(String message) {
            super(message);
        }
    }

    /** All minyanim of the shul, enabled or not. */
    @Transactional(readOnly = true)
    public List<Minyan> list(String orgId) {
        requireAccess(orgId);
        return minyanService.findMatching(orgId);
    }

    @Transactional(readOnly = true)
    public Minyan get(String orgId, String minyanId) {
        requireAccess(orgId);
        return requireMinyan(orgId, minyanId);
    }

    /** Location names by id for the shul, for display. */
    @Transactional(readOnly = true)
    public Map<String, String> locationNames(String orgId) {
        requireAccess(orgId);
        Map<String, String> names = new HashMap<>();
        for (Location location : locationService.findMatching(orgId)) {
            names.put(location.getId(), location.getName());
        }
        return names;
    }

    /** Enables or disables a minyan and syncs the calendar. */
    @Transactional
    public Minyan setEnabled(String orgId, String minyanId, boolean enabled) {
        return apply(orgId, minyanId, enabled, null);
    }

    /** Updates a minyan's schedule and details (not its enabled flag) and syncs the calendar. */
    @Transactional
    public Minyan update(String orgId, String minyanId, MinyanUpdateCommand command) {
        return apply(orgId, minyanId, null, command);
    }

    /**
     * Applies an enabled change and/or an update together: everything is validated before anything is
     * saved, and a failure rolls the whole change back.
     *
     * @param enabled null leaves the enabled flag as it is
     * @param command null changes no details
     */
    @Transactional
    public Minyan apply(String orgId, String minyanId, Boolean enabled, MinyanUpdateCommand command) {
        requireAccess(orgId);
        Minyan minyan = requireMinyan(orgId, minyanId);

        if (command != null) {
            validateAndApply(orgId, minyan, command);
        }
        if (enabled != null) {
            minyan.setEnabled(enabled);
        }

        Minyan saved = minyanService.update(minyan);
        calendarMaterializationService.syncRulesForOrganizationLive(orgId);
        return saved != null ? saved : minyan;
    }

    private void validateAndApply(String orgId, Minyan minyan, MinyanUpdateCommand command) {
        // Validate everything first so a bad request never leaves the entity half-changed.
        String notes = command.notes();
        String whatsapp = command.whatsapp();
        if (notes != null && notes.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Notes can be at most " + MAX_TEXT_LENGTH + " characters.");
        }
        if (whatsapp != null && whatsapp.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("WhatsApp can be at most " + MAX_TEXT_LENGTH + " characters.");
        }
        Location location = null;
        if (command.locationId() != null) {
            location = command.locationId().isBlank() ? null : locationService.findById(command.locationId());
            if (location == null || !orgId.equals(location.getOrganizationId())) {
                throw new IllegalArgumentException("Location not found for this shul.");
            }
        }
        Map<MinyanDay, String> stored = new EnumMap<>(MinyanDay.class);
        if (command.slots() != null) {
            command.slots().forEach((day, time) -> {
                if (time == null) {
                    return;
                }
                String value = time.toString();
                if ("INVALID".equals(value)) {
                    throw new IllegalArgumentException("Invalid time for " + day.name().toLowerCase().replace('_', ' ') + ".");
                }
                stored.put(day, value);
            });
        }

        if (location != null) {
            minyan.setLocationId(location.getId());
        }
        if (command.type() != null) {
            minyan.setType(command.type());
        }
        if (command.nusach() != null) {
            minyan.setNusach(command.nusach());
        }
        if (notes != null) {
            minyan.setNotes(notes.isBlank() ? null : notes);
        }
        if (whatsapp != null) {
            minyan.setWhatsapp(whatsapp.isBlank() ? null : whatsapp);
        }
        stored.forEach((day, value) -> {
            switch (day) {
                case SUNDAY -> minyan.setStartTime1(value);
                case MONDAY -> minyan.setStartTime2(value);
                case TUESDAY -> minyan.setStartTime3(value);
                case WEDNESDAY -> minyan.setStartTime4(value);
                case THURSDAY -> minyan.setStartTime5(value);
                case FRIDAY -> minyan.setStartTime6(value);
                case SHABBOS -> minyan.setStartTime7(value);
                case ROSH_CHODESH -> minyan.setStartTimeRC(value);
                case YOM_TOV -> minyan.setStartTimeYT(value);
                case CHANUKA -> minyan.setStartTimeCH(value);
                case ROSH_CHODESH_CHANUKA -> minyan.setStartTimeCHRC(value);
            }
        });
    }

    private void requireAccess(String orgId) {
        if (orgId == null || !userService.canAccessOrganization(orgId)) {
            throw new AccessDeniedException("You do not have permission to access this shul.");
        }
    }

    private Minyan requireMinyan(String orgId, String minyanId) {
        Minyan minyan = minyanId == null ? null : minyanService.findById(minyanId);
        if (minyan == null || !orgId.equals(minyan.getOrganizationId())) {
            throw new MinyanNotFoundException("Minyan not found.");
        }
        return minyan;
    }
}
