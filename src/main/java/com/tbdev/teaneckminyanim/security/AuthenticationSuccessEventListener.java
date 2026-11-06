package com.tbdev.teaneckminyanim.security;

import com.tbdev.teaneckminyanim.service.LoginAttemptService;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.auth.AuthAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

@Component
public class AuthenticationSuccessEventListener implements
        ApplicationListener<AuthenticationSuccessEvent> {
    
    @Autowired
    private HttpServletRequest request;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private TNMUserService userService;

    @Autowired
    private AuthAuditService auditService;

    @Override
    public void onApplicationEvent(final AuthenticationSuccessEvent e) {
        final String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty() || !xfHeader.contains(request.getRemoteAddr())) {
            loginAttemptService.loginSucceeded(request.getRemoteAddr());
        } else {
            loginAttemptService.loginSucceeded(xfHeader.split(",")[0]);
        }

        TNMUser user = userService.findByName(e.getAuthentication().getName());
        if (user != null) {
            String authenticationType = e.getAuthentication().getClass().getSimpleName();
            String method = authenticationType.toLowerCase().contains("webauthn") ? "PASSKEY" : "PASSWORD";
            user.setLastLoginAt(LocalDateTime.now());
            user.setLastLoginMethod(method);
            userService.update(user);
            auditService.record(method + "_LOGIN_SUCCEEDED", user.getId(),
                    AuthAuditService.hash(request.getRemoteAddr()), authenticationType);
        }
    }
}
