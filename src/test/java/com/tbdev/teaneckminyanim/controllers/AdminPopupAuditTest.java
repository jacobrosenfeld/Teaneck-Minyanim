package com.tbdev.teaneckminyanim.controllers;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every admin popup uses the standard design (#223 follow-up on PR #315): confirmations go through the
 * shared confirm dialog, notices through toasts, and the remaining form or info modals use .admin-modal.
 */
class AdminPopupAuditTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");
    private static final Path STATIC = Path.of("src/main/resources/static");

    /** Owned by another open PR (#317), or not routed by any controller. */
    private static final Set<String> EXCLUDED = Set.of(
            "admin/settings.html", "admin/email-settings.html", "admin/settings-old.html");

    private static final Pattern NATIVE_DIALOG =
            Pattern.compile("(?<![\\w.$])(?:window\\.)?(confirm|alert|prompt)\\s*\\(");

    private static List<Path> adminTemplates() throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(TEMPLATES.resolve("admin"))) {
            walk.filter(p -> p.toString().endsWith(".html"))
                    .filter(p -> !EXCLUDED.contains(TEMPLATES.relativize(p).toString().replace('\\', '/')))
                    .forEach(files::add);
        }
        files.add(TEMPLATES.resolve("account.html"));
        files.add(TEMPLATES.resolve("minyanschedule.html"));
        return files;
    }

    private static List<Path> adminScripts() throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(STATIC.resolve("admin"))) {
            walk.filter(p -> p.toString().endsWith(".js")).forEach(files::add);
        }
        files.add(STATIC.resolve("account.js"));
        return files;
    }

    @Test
    void noNativeBrowserDialogsInAdminPages() throws IOException {
        List<Path> files = new ArrayList<>(adminTemplates());
        files.addAll(adminScripts());
        for (Path file : files) {
            String source = Files.readString(file)
                    .replaceAll("(?s)/\\*.*?\\*/", "")
                    .replaceAll("(?s)<!--.*?-->", "")
                    // The shared dialog's own handler is named confirm().
                    .replace("function confirm()", "");
            Matcher matcher = NATIVE_DIALOG.matcher(source);
            assertFalse(matcher.find(), () -> file + " uses a native " + matcher.group(1) + "()");
        }
    }

    @Test
    void everyAdminModalUsesTheSharedDesign() throws IOException {
        Pattern modal = Pattern.compile("class=\"modal fade([^\"]*)\"");
        for (Path file : adminTemplates()) {
            Matcher matcher = modal.matcher(Files.readString(file));
            while (matcher.find()) {
                assertTrue(matcher.group(1).contains("admin-modal"), file + ": " + matcher.group());
            }
        }
    }

    @Test
    void confirmTriggersAreButtonsNotForms() throws IOException {
        Pattern formWithConfirm = Pattern.compile("<form[^>]*\\sdata-confirm[\\s=>]");
        for (Path file : adminTemplates()) {
            String source = Files.readString(file);
            assertFalse(formWithConfirm.matcher(source).find(), file + " puts data-confirm on a form");
        }
    }

    @Test
    void layoutProvidesTheSharedDialogAndStyles() throws IOException {
        String layout = Files.readString(TEMPLATES.resolve("admin/layout.html"));
        assertTrue(layout.contains("~{admin/fragments/confirm-modal :: confirmModal}"));
        assertTrue(layout.contains("@{/admin/admin-modal.css}"));

        String fragment = Files.readString(TEMPLATES.resolve("admin/fragments/confirm-modal.html"));
        assertFalse(fragment.contains("<style"), "dialog styles belong in /admin/admin-modal.css");
        assertTrue(fragment.contains("data-confirm-part=\"secondary\""));

        String css = Files.readString(STATIC.resolve("admin/admin-modal.css"));
        for (String rule : List.of(".admin-modal .modal-content", ".admin-modal-heading", ".admin-modal-icon.danger",
                ".admin-modal .modal-footer", ".admin-modal.admin-modal-lg .modal-dialog")) {
            assertTrue(css.contains(rule), rule);
        }

        // No page re-includes the dialog (it would duplicate the #confirm-modal id).
        for (Path file : adminTemplates()) {
            if (file.endsWith("layout.html")) {
                continue;
            }
            assertFalse(Files.readString(file).contains("confirm-modal :: confirmModal"), file.toString());
        }
    }
}
