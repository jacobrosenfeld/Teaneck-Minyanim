package com.tbdev.teaneckminyanim.controllers;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginTemplateTest {

    @Test
    void hebrewQuoteIsRightToLeftHtmlTextNotSvgTextPath() throws Exception {
        String template = Files.readString(Path.of("src/main/resources/templates/admin/login.html"));
        String css = Files.readString(Path.of("src/main/resources/static/admin/login.css"));

        // WebKit renders SVG textPath glyphs without bidi reordering, which reversed the Hebrew on iOS.
        assertFalse(template.contains("<textPath"));
        assertTrue(template.contains("class=\"login-maariv-quote\" lang=\"he\" dir=\"rtl\""));
        assertTrue(template.contains("משנה עתים ומחליף את הזמנים"), "Hebrew must stay in logical order");
        assertTrue(css.contains("unicode-bidi: isolate"));
    }

    @Test
    void passkeyButtonStartsWebAuthnFlowAndHandlesMissingSupport() throws Exception {
        String template = Files.readString(Path.of("src/main/resources/templates/admin/login.html"));
        String script = Files.readString(Path.of("src/main/resources/static/admin/login.js"));

        assertTrue(template.contains("id=\"passkey-button\""));
        assertTrue(template.contains("Sign in with a passkey"));
        assertTrue(template.contains("th:if=\"${passkeysEnabled}\""));
        assertTrue(template.contains("id=\"passkey-unavailable\""));
        assertTrue(script.contains("passkeyButton.addEventListener('click'"));
        assertTrue(script.contains("authenticateWithPasskey(identifierForm)"));
        assertTrue(script.contains("passkeyUnavailable.textContent = passkeyUnavailableMessage()"));
    }
}
