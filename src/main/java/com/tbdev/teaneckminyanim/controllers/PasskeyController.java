package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.VersionService;
import com.tbdev.teaneckminyanim.service.auth.PasskeyCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.ModelAndView;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class PasskeyController {
    private final TNMUserService userService;
    private final PasskeyCredentialService passkeyCredentialService;
    private final ApplicationSettingsService settingsService;
    private final VersionService versionService;
    private final OrganizationService organizationService;

    @ModelAttribute("siteName")
    public String siteName() {
        return settingsService.getSiteName();
    }

    @ModelAttribute("supportEmail")
    public String supportEmail() {
        return settingsService.getSupportEmail();
    }

    @GetMapping("/webauthn/register")
    public ModelAndView passkeys() {
        TNMUser user = userService.getCurrentUser();
        ModelAndView mv = new ModelAndView("admin/passkeys");
        mv.addObject("user", user);
        mv.addObject("passkeys", passkeyRows(user));
        mv.addObject("passkeyCount", passkeyCredentialService.countPasskeys(user));
        mv.addObject("date", DateTimeFormatter.ofPattern("MMMM d, yyyy | hh:mm aa")
                .withZone(settingsService.getTimeZone().toZoneId())
                .format(new Date().toInstant()));
        mv.addObject("appVersion", versionService.getVersion());

        if (user != null && user.isSuperAdmin()) {
            List<Organization> organizations = organizationService.getAll();
            organizations.sort(Comparator.comparing(Organization::getName, String.CASE_INSENSITIVE_ORDER));
            mv.addObject("allOrganizations", organizations);
        }
        return mv;
    }

    private List<PasskeyRow> passkeyRows(TNMUser user) {
        ZoneId zoneId = settingsService.getTimeZone().toZoneId();
        return passkeyCredentialService.findPasskeys(user).stream()
                .filter(CredentialRecord.class::isInstance)
                .map(CredentialRecord.class::cast)
                .map(record -> new PasskeyRow(
                        record.getCredentialId().toBase64UrlString(),
                        displayLabel(record),
                        formatInstant(record.getCreated(), zoneId),
                        formatInstant(record.getLastUsed(), zoneId)))
                .toList();
    }

    private String displayLabel(CredentialRecord record) {
        String label = record.getLabel();
        if (label == null || label.isBlank()) {
            return "Passkey";
        }
        return label;
    }

    private String formatInstant(Instant instant, ZoneId zoneId) {
        if (instant == null) {
            return "Never";
        }
        return DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
                .withZone(zoneId)
                .format(instant);
    }

    public record PasskeyRow(String credentialId, String label, String created, String lastUsed) {
    }
}
