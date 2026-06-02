package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.email.EmailSendResult;
import com.tbdev.teaneckminyanim.service.email.EmailService;
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
    private final TNMUserService userService;

    @PostMapping("/test")
    public ResponseEntity<TestEmailResponse> sendTestEmail(@RequestParam("recipient") String recipient) {
        requireSuperAdmin();

        EmailSendResult result = emailService.sendTestEmail(recipient);
        HttpStatus status = result.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(TestEmailResponse.from(result));
    }

    private void requireSuperAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new AccessDeniedException("You are not authorized to send test emails.");
        }

        TNMUser user = userService.findByName(authentication.getName());
        if (user == null || !user.isSuperAdmin()) {
            throw new AccessDeniedException("You are not authorized to send test emails.");
        }
    }

    public record TestEmailResponse(boolean success, String provider, String message) {
        static TestEmailResponse from(EmailSendResult result) {
            return new TestEmailResponse(
                    result.isSuccess(),
                    result.getProvider() == null ? null : result.getProvider().name(),
                    result.getMessage());
        }
    }
}
