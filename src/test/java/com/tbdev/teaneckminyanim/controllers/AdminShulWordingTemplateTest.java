package com.tbdev.teaneckminyanim.controllers;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #207: the admin panel says "Shul" instead of "Organization" in user-facing text.
 * Routes, ids, model attributes and other code identifiers keep the "organization" name.
 */
class AdminShulWordingTemplateTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    /** "Organization(s)" as a visible word: between tags, or as placeholder/title/confirm text. */
    private static final Pattern VISIBLE_ORGANIZATION = Pattern.compile(
            ">\\s*[^<>]*\\b[Oo]rgani[sz]ations?\\b[^<>${}]*<"
                    + "|(placeholder|title)=\"[^\"]*\\b[Oo]rgani[sz]ations?\\b"
                    + "|confirm\\('[^']*\\b[Oo]rgani[sz]ations?\\b");

    private static String read(String relative) throws IOException {
        String html = Files.readString(TEMPLATES.resolve(relative));
        // Comments are not user-facing.
        return html.replaceAll("(?s)<!--.*?-->", "").replaceAll("(?m)//.*$", "");
    }

    private static void assertNoVisibleOrganization(String relative) throws IOException {
        Matcher matcher = VISIBLE_ORGANIZATION.matcher(read(relative));
        assertFalse(matcher.find(), () -> relative + " still shows \"Organization\": " + matcher.group());
    }

    @Test
    void adminNavigationSaysShul() throws IOException {
        String sidebar = read("admin/sidebar.html");
        assertTrue(sidebar.contains("<span>Shuls</span>"));
        assertTrue(sidebar.contains("<span>New Shul</span>"));
        assertTrue(sidebar.contains("<span>My Shul</span>"));
        assertTrue(sidebar.contains("<span>Delete Shul</span>"));

        String navbar = read("admin/navbar.html");
        assertTrue(navbar.contains("<span>Shuls</span>"));
        assertTrue(navbar.contains("placeholder=\"Search shuls...\""));
    }

    @Test
    void ownedAdminTemplatesShowNoOrganizationWording() throws IOException {
        for (String template : new String[]{
                "admin/sidebar.html", "admin/navbar.html", "admin/organizations.html",
                "admin/new-organization.html", "admin/accounts.html", "admin/calendar-entries.html",
                "admin/calendar-events.html", "admin/calendar-events-all.html", "admin/maintenance.html",
                "account.html"}) {
            assertNoVisibleOrganization(template);
        }
    }

    @Test
    void routesAndFieldNamesAreUnchanged() throws IOException {
        String sidebar = read("admin/sidebar.html");
        assertTrue(sidebar.contains("@{/admin/organizations}"));
        assertTrue(sidebar.contains("@{/admin/new-organization}"));
        assertTrue(sidebar.contains("data-target=\"#delete-organization-modal\""));

        String allEvents = read("admin/calendar-events-all.html");
        assertTrue(allEvents.contains("<select name=\"organizationId\">"));
    }
}
