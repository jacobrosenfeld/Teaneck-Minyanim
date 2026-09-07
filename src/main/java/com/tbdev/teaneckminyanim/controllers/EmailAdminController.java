package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.email.EmailSendResult;
import com.tbdev.teaneckminyanim.service.email.EmailService;
import com.tbdev.teaneckminyanim.service.email.WeeklyMinyanReviewEmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/email")
public class EmailAdminController {
    private final EmailService emailService;
    private final WeeklyMinyanReviewEmailService weeklyMinyanReviewEmailService;
    private final TNMUserService userService;

    @PostMapping("/test")
    public ResponseEntity<TestEmailResponse> sendTestEmail(@RequestParam("recipient") String recipient) {
        requireSuperAdmin();

        EmailSendResult result = emailService.sendTestEmail(recipient);
        HttpStatus status = result.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(TestEmailResponse.from(result));
    }

    @PostMapping("/weekly-minyan-review/test")
    public ResponseEntity<WeeklyDigestTestEmailResponse> sendWeeklyDigestTestEmail(
            @RequestParam(value = "refreshBeforeSend", defaultValue = "false") boolean refreshBeforeSend,
            @RequestParam(value = "organizationId", required = false) String organizationId) {
        TNMUser user = requireAdmin("You are not authorized to send weekly digest test emails.");

        String selectedOrganizationId = trimToNull(organizationId);
        if (selectedOrganizationId != null && !user.isSuperAdmin()) {
            throw new AccessDeniedException("You are not authorized to send a shul digest test.");
        }

        WeeklyMinyanReviewEmailService.WeeklyMinyanReviewTestSendResult result =
                selectedOrganizationId == null
                        ? weeklyMinyanReviewEmailService.sendWeeklyReviewTestEmail(user, refreshBeforeSend)
                        : weeklyMinyanReviewEmailService.sendWeeklyReviewTestEmailForOrganization(
                                user,
                                selectedOrganizationId,
                                refreshBeforeSend);
        HttpStatus status = result.success() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(WeeklyDigestTestEmailResponse.from(result));
    }

    private TNMUser requireSuperAdmin() {
        TNMUser user = requireAdmin("You are not authorized to send test emails.");
        if (!user.isSuperAdmin()) {
            throw new AccessDeniedException("You are not authorized to send test emails.");
        }
        return user;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private TNMUser requireAdmin(String message) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new AccessDeniedException(message);
        }

        TNMUser user = userService.findByName(authentication.getName());
        if (user == null || !user.isAdmin()) {
            throw new AccessDeniedException(message);
        }
        return user;
    }

    public record TestEmailResponse(boolean success, String provider, String message) {
        static TestEmailResponse from(EmailSendResult result) {
            return new TestEmailResponse(
                    result.isSuccess(),
                    result.getProvider() == null ? null : result.getProvider().name(),
                    result.getMessage());
        }
    }

    public record WeeklyDigestTestEmailResponse(boolean success, String range, String message) {
        static WeeklyDigestTestEmailResponse from(
                WeeklyMinyanReviewEmailService.WeeklyMinyanReviewTestSendResult result) {
            String rangeLabel = result.range() == null ? null : result.range().displayLabel();
            String digestLabel = result.digestLabel() == null ? "weekly digest" : result.digestLabel();
            String message = result.success()
                    ? "Weekly digest test sent for " + digestLabel + ", " + rangeLabel + "."
                    : defaultFailureMessage(result.message());
            return new WeeklyDigestTestEmailResponse(result.success(), rangeLabel, message);
        }

        private static String defaultFailureMessage(String message) {
            if (message != null && !message.isBlank()) {
                return message;
            }
            return "Weekly digest test could not be sent.";
        }
    }
}
