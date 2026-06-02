package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.enums.SettingKey;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.VersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class EmailSettingsController {
    private final ApplicationSettingsService settingsService;
    private final TNMUserService userService;
    private final OrganizationService organizationService;
    private final VersionService versionService;

    @ModelAttribute("siteName")
    public String siteName() {
        return settingsService.getSiteName();
    }

    @ModelAttribute("supportEmail")
    public String supportEmail() {
        return settingsService.getSupportEmail();
    }

    @GetMapping("/admin/settings/email")
    public ModelAndView emailSettings() {
        requireSuperAdmin();

        ModelAndView mv = new ModelAndView("admin/email-settings");
        mv.addObject("sections", sections());
        mv.addObject("emailProvider", settingsService.getEmailProvider());
        addStandardPageData(mv);
        return mv;
    }

    @ResponseBody
    @PostMapping("/admin/settings/email/update-field")
    public ResponseEntity<FieldUpdateResponse> updateField(
            @RequestParam("settingKey") String settingKey,
            @RequestParam(value = "settingValue", required = false) String settingValue) {
        TNMUser user = requireSuperAdmin();

        SettingKey key;
        try {
            key = SettingKey.fromKey(settingKey);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(FieldUpdateResponse.failure(settingKey, "Unknown email setting."));
        }

        if (!isEmailSetting(key)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(FieldUpdateResponse.failure(settingKey, "This endpoint only updates email settings."));
        }

        try {
            if (key.isSensitive() && (settingValue == null || settingValue.isBlank())) {
                log.info("Sensitive email setting left unchanged: {} by user {}", key.getKey(), user.getUsername());
                return ResponseEntity.ok(FieldUpdateResponse.success(
                        key.getKey(),
                        displayValue(key),
                        "Saved"));
            }

            settingsService.updateSetting(key, settingValue);
            log.info("Email setting updated: {} by user {}", key.getKey(), user.getUsername());
            return ResponseEntity.ok(FieldUpdateResponse.success(
                    key.getKey(),
                    displayValue(key),
                    "Saved"));
        } catch (ApplicationSettingsService.ValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(FieldUpdateResponse.failure(key.getKey(), e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating email setting {}: {}", key.getKey(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(FieldUpdateResponse.failure(key.getKey(), "Setting could not be saved."));
        }
    }

    private List<EmailSettingSection> sections() {
        return List.of(
                new EmailSettingSection(
                        "delivery",
                        "Delivery Mode",
                        "Choose the active email provider. Leave disabled until credentials are ready.",
                        "fa-toggle-on",
                        List.of(field(
                                SettingKey.EMAIL_PROVIDER,
                                "select",
                                List.of(
                                        new SettingOption("", "Disabled"),
                                        new SettingOption("SMTP", "SMTP"),
                                        new SettingOption("SES", "AWS SES"))))),
                new EmailSettingSection(
                        "sender",
                        "Sender Defaults",
                        "Set the identity recipients see on transactional and admin emails.",
                        "fa-at",
                        List.of(
                                field(SettingKey.EMAIL_FROM_ADDRESS, "email"),
                                field(SettingKey.EMAIL_FROM_NAME, "text"),
                                field(SettingKey.EMAIL_REPLY_TO, "email"))),
                new EmailSettingSection(
                        "smtp",
                        "SMTP Server",
                        "Use standard SMTP credentials and optional STARTTLS.",
                        "fa-server",
                        List.of(
                                field(SettingKey.EMAIL_SMTP_HOST, "text"),
                                field(SettingKey.EMAIL_SMTP_PORT, "number"),
                                field(SettingKey.EMAIL_SMTP_USERNAME, "text"),
                                field(SettingKey.EMAIL_SMTP_PASSWORD, "password"),
                                field(
                                        SettingKey.EMAIL_SMTP_STARTTLS_ENABLED,
                                        "select",
                                        List.of(
                                                new SettingOption("true", "Enabled"),
                                                new SettingOption("false", "Disabled"))))),
                new EmailSettingSection(
                        "ses",
                        "AWS SES",
                        "Use the SES API for production-grade email delivery.",
                        "fa-cloud",
                        List.of(
                                field(SettingKey.EMAIL_SES_REGION, "text"),
                                field(SettingKey.EMAIL_SES_ACCESS_KEY_ID, "password"),
                                field(SettingKey.EMAIL_SES_SECRET_ACCESS_KEY, "password"),
                                field(SettingKey.EMAIL_SES_CONFIGURATION_SET, "text"))));
    }

    private EmailSettingField field(SettingKey key, String inputType) {
        return field(key, inputType, List.of());
    }

    private EmailSettingField field(SettingKey key, String inputType, List<SettingOption> options) {
        String value = settingsService.getString(key);
        boolean hasValue = value != null && !value.isBlank();
        String displayValue = displayValue(key, value);
        String editableValue = key.isSensitive() ? "" : value;
        String placeholder = key.isSensitive()
                ? (hasValue ? "Saved value hidden - enter a new value to replace" : "Enter value")
                : "Enter value";

        return new EmailSettingField(
                key.getKey(),
                key.getDisplayName(),
                key.getDescription(),
                inputType,
                editableValue,
                displayValue,
                key.isSensitive(),
                placeholder,
                options,
                !options.isEmpty());
    }

    private String displayValue(SettingKey key) {
        return displayValue(key, settingsService.getString(key));
    }

    private String displayValue(SettingKey key, String value) {
        if (value == null || value.isBlank()) {
            return "Not set";
        }
        return key.maskValue(value);
    }

    private boolean isEmailSetting(SettingKey key) {
        return key.getKey().startsWith("email.");
    }

    private TNMUser requireSuperAdmin() {
        TNMUser user = getCurrentUser();
        if (user == null || !user.isSuperAdmin()) {
            throw new AccessDeniedException("You are not authorized to access email settings.");
        }
        return user;
    }

    private TNMUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        return userService.findByName(auth.getName());
    }

    private void addStandardPageData(ModelAndView mv) {
        TNMUser user = getCurrentUser();
        mv.addObject("user", user);

        List<Organization> organizations = new ArrayList<>(organizationService.getAll());
        organizations.sort(Comparator.comparing(Organization::getName, String.CASE_INSENSITIVE_ORDER));
        mv.addObject("allOrganizations", organizations);

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM d, yyyy | hh:mm aa");
        dateFormat.setTimeZone(settingsService.getTimeZone());
        mv.addObject("date", dateFormat.format(new Date()));
        mv.addObject("appVersion", versionService.getVersion());
    }

    public record EmailSettingSection(
            String id,
            String title,
            String description,
            String icon,
            List<EmailSettingField> fields) {
    }

    public record EmailSettingField(
            String key,
            String label,
            String description,
            String inputType,
            String value,
            String displayValue,
            boolean sensitive,
            String placeholder,
            List<SettingOption> options,
            boolean hasOptions) {
    }

    public record SettingOption(String value, String label) {
    }

    public record FieldUpdateResponse(boolean success, String key, String displayValue, String message) {
        static FieldUpdateResponse success(String key, String displayValue, String message) {
            return new FieldUpdateResponse(true, key, displayValue, message);
        }

        static FieldUpdateResponse failure(String key, String message) {
            return new FieldUpdateResponse(false, key, null, message);
        }
    }
}
