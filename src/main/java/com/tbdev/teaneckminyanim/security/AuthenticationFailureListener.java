package com.tbdev.teaneckminyanim.security;

import com.tbdev.teaneckminyanim.service.LoginAttemptService;
import com.tbdev.teaneckminyanim.service.auth.AuthAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class AuthenticationFailureListener implements
        ApplicationListener<AuthenticationFailureBadCredentialsEvent> {

    @Autowired
    private HttpServletRequest request;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private AuthAuditService auditService;

    @Override
    public void onApplicationEvent(AuthenticationFailureBadCredentialsEvent e) {
        final String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty() || !xfHeader.contains(request.getRemoteAddr())) {
            loginAttemptService.loginFailed(request.getRemoteAddr());
        } else {
            loginAttemptService.loginFailed(xfHeader.split(",")[0]);
        }
        String authenticationType = e.getAuthentication().getClass().getSimpleName();
        String method = authenticationType.toLowerCase().contains("webauthn") ? "PASSKEY" : "PASSWORD";
        auditService.record(method + "_LOGIN_FAILED", null,
                AuthAuditService.hash(xfHeader == null ? request.getRemoteAddr() : xfHeader.split(",")[0]),
                authenticationType);
    }
}
