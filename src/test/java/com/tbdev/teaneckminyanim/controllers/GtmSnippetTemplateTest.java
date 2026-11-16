package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GtmSnippetTemplateTest {

    private static final List<String> NAVBARS = List.of("frontnavbar", "admin/navbar");

    @Test
    void snippetsRenderWithConfiguredContainerId() {
        for (String template : NAVBARS) {
            String html = render(template, "GTM-ABC123");

            assertTrue(html.contains("https://www.googletagmanager.com/gtm.js?id="), template);
            assertTrue(html.contains("'dataLayer',\"GTM-ABC123\""), template + ": " + headSnippet(html));
            assertTrue(html.contains("https://www.googletagmanager.com/ns.html?id=GTM-ABC123"), template);
        }
    }

    @Test
    void snippetsAreOmittedWhenContainerIdIsBlank() {
        for (String template : NAVBARS) {
            for (String blank : new String[] {"", null}) {
                String html = render(template, blank);

                assertFalse(html.contains("googletagmanager.com"), template);
                assertFalse(html.contains("gtm.start"), template);
            }
        }
    }

    @Test
    void containerIdIsEscapedInBothSnippets() {
        String html = render("frontnavbar", "GTM-X\"</script><script>alert(1)</script>");

        // JS string literal: quote escaped and </script> neutralised, so the tag can't be closed early.
        assertTrue(html.contains("'dataLayer',\"GTM-X\\\"<\\/script><script>alert(1)<\\/script>\")"), headSnippet(html));
        assertFalse(html.contains("<script>alert(1)</script>"));
        // noscript iframe URL: percent-encoded query parameter.
        assertTrue(html.contains("ns.html?id=GTM-X%22%3C/script%3E%3Cscript%3Ealert(1)%3C/script%3E\""), html);
    }

    @Test
    void noTemplateHardcodesTheContainerId() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources/templates"))) {
            List<Path> offenders = files
                    .filter(path -> path.toString().endsWith(".html"))
                    .filter(path -> !path.toString().contains("_old") && !path.toString().contains("-old"))
                    .filter(path -> {
                        try {
                            return Files.readString(path).contains("GTM-T2P4J8V");
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .toList();
            assertEquals(List.of(), offenders);
        }
    }

    @Test
    void adviceExposesContainerIdAndNeverThrows() {
        ApplicationSettingsService settings = mock(ApplicationSettingsService.class);
        when(settings.getGtmContainerId()).thenReturn("GTM-ABC123");
        assertEquals("GTM-ABC123", new AnalyticsModelAdvice(settings).gtmId());

        when(settings.getGtmContainerId()).thenThrow(new IllegalStateException("db down"));
        assertEquals("", new AnalyticsModelAdvice(settings).gtmId());
    }

    private static String render(String template, String gtmId) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        MockServletContext servletContext = new MockServletContext();
        WebContext context = new WebContext(JakartaServletWebApplication.buildApplication(servletContext)
                .buildExchange(new MockHttpServletRequest(servletContext), new MockHttpServletResponse()));
        context.setVariable("gtmId", gtmId);
        context.setVariable("siteName", "Teaneck Minyanim");
        context.setVariable("user", com.tbdev.teaneckminyanim.model.TNMUser.builder()
                .id("A0").username("admin").roleId(com.tbdev.teaneckminyanim.enums.Role.ADMIN.getId()).build());
        return engine.process(template, context);
    }

    private static String headSnippet(String html) {
        int start = html.indexOf("gtm.start");
        return start < 0 ? "(no snippet)" : html.substring(start, Math.min(html.length(), start + 400));
    }
}
