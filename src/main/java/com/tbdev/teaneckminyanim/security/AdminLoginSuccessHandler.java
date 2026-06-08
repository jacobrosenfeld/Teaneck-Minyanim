package com.tbdev.teaneckminyanim.security;

import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.auth.PasskeyCredentialService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AdminLoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {
    private final TNMUserService userService;
    private final PasskeyCredentialService passkeyCredentialService;

    public AdminLoginSuccessHandler(TNMUserService userService,
                                    PasskeyCredentialService passkeyCredentialService) {
        this.userService = userService;
        this.passkeyCredentialService = passkeyCredentialService;
        setDefaultTargetUrl("/admin");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws ServletException, IOException {
        TNMUser user = userService.findByName(authentication.getName());
        if (user != null && !passkeyCredentialService.hasPasskeys(user)) {
            getRedirectStrategy().sendRedirect(request, response, passkeyCredentialService.setupPromptUrl(user));
            return;
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
