package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mobile app links:
 * <ul>
 *   <li>{@code /.well-known/apple-app-site-association}: iOS universal links for {@code /app/auth/*} and passkey
 *       (webcredentials) association.</li>
 *   <li>{@code /.well-known/assetlinks.json}: Android app links and passkey (get_login_creds) association.</li>
 *   <li>{@code /app/auth/magic-link}: the page a browser shows when an app sign-in link opens without the app.</li>
 * </ul>
 * Each association file is 404 until its {@code app.links.*} properties are set.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class WellKnownController {
    static final String APP_LINK_COMPONENT = "/app/auth/*";

    private final ApplicationSettingsService settingsService;

    @Value("${app.links.apple-team-id:}")
    private String appleTeamId = "";

    @Value("${app.links.ios-bundle-id:}")
    private String iosBundleId = "";

    @Value("${app.links.android-package:}")
    private String androidPackage = "";

    @Value("${app.links.android-sha256-fingerprints:}")
    private String androidFingerprints = "";

    @GetMapping(value = "/.well-known/apple-app-site-association", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> appleAppSiteAssociation() {
        String teamId = trim(appleTeamId);
        String bundleId = trim(iosBundleId);
        if (teamId.isEmpty() || bundleId.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String appId = teamId + "." + bundleId;

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("appIDs", List.of(appId));
        detail.put("components", List.of(Map.of("/", APP_LINK_COMPONENT)));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("applinks", Map.of("details", List.of(detail)));
        body.put("webcredentials", Map.of("apps", List.of(appId)));
        return json(body);
    }

    @GetMapping(value = "/.well-known/assetlinks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> assetLinks() {
        String packageName = trim(androidPackage);
        List<String> fingerprints = Arrays.stream(trim(androidFingerprints).split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(String::toUpperCase)
                .toList();
        if (packageName.isEmpty() || fingerprints.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> target = new LinkedHashMap<>();
        target.put("namespace", "android_app");
        target.put("package_name", packageName);
        target.put("sha256_cert_fingerprints", fingerprints);

        Map<String, Object> statement = new LinkedHashMap<>();
        statement.put("relation", List.of("delegate_permission/common.handle_all_urls",
                "delegate_permission/common.get_login_creds"));
        statement.put("target", target);
        return json(List.of(statement));
    }

    /**
     * Shown when an app sign-in link opens in a browser (the app is not installed, or the link was opened on another
     * device). The token is never read, checked or rendered: it can only be exchanged by the app. The page loads no
     * analytics or third-party scripts, so the link's URL cannot leak from it.
     */
    @GetMapping("/app/auth/magic-link")
    public ModelAndView appMagicLinkFallback(HttpServletResponse response) {
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("X-Robots-Tag", "noindex, nofollow");
        ModelAndView mv = new ModelAndView("app-magic-link");
        mv.addObject("siteName", safe(settingsService::getSiteName, "Teaneck Minyanim"));
        mv.addObject("supportEmail", safe(settingsService::getSupportEmail, null));
        return mv;
    }

    private static <T> ResponseEntity<T> json(T body) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(body);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safe(java.util.function.Supplier<String> supplier, String fallback) {
        try {
            String value = supplier.get();
            return value == null || value.isBlank() ? fallback : value;
        } catch (RuntimeException e) {
            log.warn("Could not read a site setting for the app sign-in page: {}", e.getMessage());
            return fallback;
        }
    }
}
