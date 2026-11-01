package com.tbdev.teaneckminyanim.service.email;

import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserWelcomeEmailService {
    private final EmailService emailService;
    private final ApplicationSettingsService settingsService;
    private final OrganizationService organizationService;

    public EmailSendResult sendWelcome(TNMUser user, String loginUrl) {
        WelcomeCopy copy = welcomeCopy(user);
        String siteName = settingsService.getSiteName();
        String username = user.getUsername() == null || user.getUsername().isBlank() ? "there" : user.getUsername();
        EmailMessage message = EmailMessage.builder()
                .to(user.getEmail())
                .subject("Welcome to " + siteName)
                .textBody(textBody(siteName, username, loginUrl, copy))
                .htmlBody(htmlBody(
                        HtmlUtils.htmlEscape(siteName),
                        HtmlUtils.htmlEscape(username),
                        HtmlUtils.htmlEscape(loginUrl),
                        copy.escaped()))
                .metadata("source", "admin-welcome")
                .build();
        return emailService.send(message);
    }

    WelcomeCopy welcomeCopy(TNMUser user) {
        if (user.isSuperAdmin()) {
            return new WelcomeCopy(
                    "Super admin",
                    "the entire site",
                    "You can manage every shul, user permissions, account recovery, and platform settings.");
        }

        List<String> organizationNames = user.getAccessibleOrganizationIds().stream()
                .map(organizationService::findById)
                .flatMap(java.util.Optional::stream)
                .map(Organization::getName)
                .filter(name -> name != null && !name.isBlank())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        String scope = organizationNames.isEmpty() ? "your assigned shuls" : naturalList(organizationNames);
        if (user.isAdmin()) {
            return new WelcomeCopy(
                    "Manager",
                    scope,
                    "You can maintain schedules, locations, and shul details, and help manage accounts whose access is fully within your assigned shuls.");
        }
        return new WelcomeCopy(
                "User",
                scope,
                "You can keep minyan times, schedules, locations, and shul details accurate for your assigned shuls.");
    }

    private String naturalList(List<String> values) {
        List<String> names = new ArrayList<>(values);
        if (names.size() == 1) return names.getFirst();
        if (names.size() == 2) return names.get(0) + " and " + names.get(1);
        return String.join(", ", names.subList(0, names.size() - 1)) + ", and " + names.getLast();
    }

    private String textBody(String siteName, String username, String loginUrl, WelcomeCopy copy) {
        return "Hello " + username + ",\n\n"
                + "Welcome to " + siteName + ". You have been granted " + copy.roleName()
                + " access for " + copy.scope() + ".\n\n"
                + copy.description() + "\n\n"
                + "Activate your access and sign in using this secure link:\n" + loginUrl + "\n\n"
                + "This link expires in 15 minutes and can only be used once. After signing in, you can add a passkey from My Account for faster future access.\n\n"
                + "If you were not expecting this invitation, contact the site administrator.";
    }

    private String htmlBody(String siteName, String username, String loginUrl, WelcomeCopy copy) {
        return """
                <!doctype html>
                <html lang="en">
                <body style="margin:0;padding:0;background:#f3f6fb;font-family:Arial,Helvetica,sans-serif;color:#1f2937;">
                  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:#f3f6fb;padding:32px 16px;">
                    <tr><td align="center">
                      <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:600px;background:#ffffff;border:1px solid #dbe3ef;border-radius:14px;overflow:hidden;box-shadow:0 8px 24px rgba(31,41,55,0.08);">
                        <tr><td style="background:#275ed8;padding:28px 32px;color:#ffffff;">
                          <div style="font-size:13px;font-weight:700;letter-spacing:.08em;text-transform:uppercase;opacity:.88;">Welcome to</div>
                          <div style="font-size:25px;font-weight:700;margin-top:6px;">{{SITE_NAME}}</div>
                        </td></tr>
                        <tr><td style="padding:32px;">
                          <p style="font-size:17px;line-height:1.55;margin:0 0 14px;">Hello {{USERNAME}},</p>
                          <p style="font-size:16px;line-height:1.6;margin:0 0 20px;">Your account is ready. You have been granted <strong>{{ROLE}}</strong> access for <strong>{{SCOPE}}</strong>.</p>
                          <div style="background:#f8fafc;border-left:4px solid #275ed8;border-radius:6px;padding:16px;margin-bottom:24px;">
                            <p style="font-size:14px;line-height:1.6;margin:0;">{{DESCRIPTION}}</p>
                          </div>
                          <table role="presentation" cellspacing="0" cellpadding="0" style="margin:0 0 22px;"><tr><td style="border-radius:8px;background:#275ed8;">
                            <a href="{{LOGIN_URL}}" style="display:inline-block;padding:14px 24px;color:#ffffff;text-decoration:none;font-size:16px;font-weight:700;">Activate access and sign in</a>
                          </td></tr></table>
                          <p style="font-size:14px;line-height:1.55;margin:0 0 20px;"><strong>This secure link expires in 15 minutes</strong> and can only be used once. After signing in, visit My Account to add a passkey for faster future access.</p>
                          <p style="font-size:13px;line-height:1.55;color:#64748b;margin:0 0 8px;">If the button does not work, copy and paste this link into your browser:</p>
                          <p style="font-size:12px;line-height:1.5;word-break:break-all;margin:0;"><a href="{{LOGIN_URL}}" style="color:#275ed8;">{{LOGIN_URL}}</a></p>
                        </td></tr>
                        <tr><td style="border-top:1px solid #e5e7eb;padding:18px 32px;color:#64748b;font-size:12px;line-height:1.5;">If you were not expecting this invitation, contact the {{SITE_NAME}} administrator.</td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """
                .replace("{{SITE_NAME}}", siteName)
                .replace("{{USERNAME}}", username)
                .replace("{{ROLE}}", copy.roleName())
                .replace("{{SCOPE}}", copy.scope())
                .replace("{{DESCRIPTION}}", copy.description())
                .replace("{{LOGIN_URL}}", loginUrl);
    }

    record WelcomeCopy(String roleName, String scope, String description) {
        WelcomeCopy escaped() {
            return new WelcomeCopy(
                    HtmlUtils.htmlEscape(roleName),
                    HtmlUtils.htmlEscape(scope),
                    HtmlUtils.htmlEscape(description));
        }
    }
}
