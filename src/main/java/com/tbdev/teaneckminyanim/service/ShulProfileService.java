package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.model.Organization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Edits a shul's profile, shared by the admin panel and the admin API. Checks shul access, validates,
 * saves, makes sure the shul has a slug, and geocodes the address when it changed.
 *
 * <p>No field is limited to super admins today: anyone who can access the shul can edit every profile field.
 *
 * <p>Errors: {@link AccessDeniedException}, {@link ShulNotFoundException}, {@link IllegalArgumentException}
 * (invalid input, with a message safe to show) and {@link IllegalStateException} (the save failed).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShulProfileService {
    static final int MAX_TEXT_LENGTH = 255;
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9-]+$");
    private static final Pattern COLOR = Pattern.compile("^#([0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$");

    private final OrganizationService organizationService;
    private final TNMUserService userService;
    private final GeocodingService geocodingService;

    /** The shul does not exist. */
    public static class ShulNotFoundException extends RuntimeException {
        public ShulNotFoundException(String message) {
            super(message);
        }
    }

    public Organization update(String orgId, ShulProfileCommand command) {
        if (orgId == null || !userService.canAccessOrganization(orgId)) {
            throw new AccessDeniedException("You do not have permission to access this shul.");
        }
        Organization organization = organizationService.findById(orgId)
                .orElseThrow(() -> new ShulNotFoundException("Shul not found."));

        String oldAddress = organization.getAddress();
        boolean hadCoordinates = organization.getLatitude() != null && organization.getLongitude() != null;

        // Validate everything before changing the entity.
        String name = null;
        if (command.name() != null) {
            name = command.name().trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Shul name cannot be empty.");
            }
            requireLength(name, "Shul name");
        }
        String address = optionalText(command.address(), "Address");
        String website = null;
        if (command.websiteUrl() != null) {
            website = validateWebsite(command.websiteUrl().trim());
        }
        String color = null;
        if (command.color() != null) {
            color = command.color().trim();
            if (!COLOR.matcher(color).matches()) {
                throw new IllegalArgumentException("Shul color must be a hex color such as #275ed8.");
            }
        }
        String whatsapp = optionalText(command.whatsapp(), "WhatsApp");
        String calendar = null;
        if (command.calendarUrl() != null) {
            calendar = validateCalendar(command.calendarUrl().trim(), organization.getCalendar());
        }
        String slug = null;
        boolean regenerateSlug = false;
        if (command.slug() != null) {
            slug = validateSlug(organization, command.slug().trim());
            regenerateSlug = slug.isEmpty();
        }

        if (name != null) {
            organization.setName(name);
        }
        if (command.address() != null) {
            organization.setAddress(address);
        }
        if (command.websiteUrl() != null) {
            organization.setWebsiteURIStr(website);
        }
        if (command.nusach() != null) {
            organization.setNusach(command.nusach());
        }
        if (color != null) {
            organization.setOrgColor(color);
        }
        if (command.slug() != null) {
            organization.setUrlSlug(regenerateSlug ? null : slug);
        }
        if (command.whatsapp() != null) {
            organization.setWhatsapp(whatsapp);
        }
        if (command.calendarUrl() != null) {
            organization.setCalendar(calendar);
        }
        if (command.useScrapedCalendar() != null) {
            organization.setUseScrapedCalendar(command.useScrapedCalendar());
        }

        organizationService.ensureSlug(organization);
        if (!organizationService.update(organization)) {
            throw new IllegalStateException("Sorry, the update failed.");
        }
        geocodeIfNeeded(organization, oldAddress, hadCoordinates);
        return organizationService.findById(orgId).orElse(organization);
    }

    private void geocodeIfNeeded(Organization organization, String oldAddress, boolean hadCoordinates) {
        String address = organization.getAddress();
        if (address == null || address.isBlank()) {
            return;
        }
        boolean addressChanged = !address.equals(oldAddress);
        if (!addressChanged && hadCoordinates) {
            return;
        }
        try {
            double[] coordinates = geocodingService.geocodeAddress(address);
            if (coordinates != null) {
                organizationService.updateGeocode(organization.getId(), coordinates[0], coordinates[1]);
            }
        } catch (RuntimeException e) {
            // The profile is saved; coordinates can be filled in on a later save.
            log.warn("Could not geocode the address of shul {}: {}", organization.getId(), e.getMessage());
        }
    }

    private static String optionalText(String value, String label) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        requireLength(trimmed, label);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void requireLength(String value, String label) {
        if (value.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(label + " can be at most " + MAX_TEXT_LENGTH + " characters.");
        }
    }

    /** A blank value clears the website. A scheme, when present, must be http or https. */
    private static String validateWebsite(String value) {
        if (value.isEmpty()) {
            return null;
        }
        requireLength(value, "Website");
        try {
            URI uri = new URI(value);
            if (uri.getScheme() != null && !isHttp(uri.getScheme())) {
                throw new IllegalArgumentException("Invalid website URL.");
            }
            return uri.toString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid website URL.");
        }
    }

    /** A blank value clears the calendar URL. An unchanged value is accepted as it is. */
    private static String validateCalendar(String value, String current) {
        if (value.isEmpty()) {
            return null;
        }
        if (value.equals(current)) {
            return value;
        }
        requireLength(value, "Calendar URL");
        try {
            URI uri = new URI(value);
            if (uri.getScheme() == null || !isHttp(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException("Calendar URL must be an http or https address.");
            }
            return value;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Calendar URL must be an http or https address.");
        }
    }

    private static boolean isHttp(String scheme) {
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    /** Returns the normalized slug, or an empty string when one should be generated from the name. */
    private String validateSlug(Organization organization, String value) {
        if (value.isEmpty()) {
            return "";
        }
        String current = organization.getUrlSlug();
        String slug = value.equalsIgnoreCase(current) ? current : value.toLowerCase(Locale.ENGLISH);
        if (!slug.equals(current)) {
            if (!SLUG.matcher(slug).matches()) {
                throw new IllegalArgumentException("URL slug can only contain lowercase letters, numbers and hyphens.");
            }
            requireLength(slug, "URL slug");
            Optional<Organization> owner = organizationService.findByUrlSlug(slug);
            if (owner.isPresent() && !owner.get().getId().equals(organization.getId())) {
                throw new IllegalArgumentException("That URL slug is already used by another shul.");
            }
        }
        return slug;
    }
}
