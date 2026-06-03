package com.tbdev.teaneckminyanim.security;

import com.tbdev.teaneckminyanim.service.recaptcha.RecaptchaService;
import com.tbdev.teaneckminyanim.service.recaptcha.RecaptchaVerificationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginRecaptchaFilter extends OncePerRequestFilter {
    static final String LOGIN_PROCESSING_PATH = "/j_spring_security_check";

    private final RecaptchaService recaptchaService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod())
                && LOGIN_PROCESSING_PATH.equals(request.getServletPath()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            recaptchaService.verify(request.getParameter("recaptchaToken"), request);
            filterChain.doFilter(request, response);
        } catch (RecaptchaVerificationException e) {
            log.warn("Admin login rejected by reCAPTCHA: {}", e.getMessage());
            response.sendRedirect(request.getContextPath() + "/admin/login?error=recaptcha");
        }
    }
}
