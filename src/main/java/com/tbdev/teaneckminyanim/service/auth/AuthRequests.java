package com.tbdev.teaneckminyanim.service.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Request helpers shared by the token services. */
public final class AuthRequests {
    private AuthRequests() {
    }

    /** The request bound to the current thread, or null outside a request. */
    public static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servlet ? servlet.getRequest() : null;
    }

    /** Client IP, preferring the first X-Forwarded-For hop (matches MagicLinkService and the rate limiter). */
    public static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** SHA-256 of the client IP, or null when unknown. */
    public static String clientIpHash(HttpServletRequest request) {
        return AuthAuditService.hash(clientIp(request));
    }

    /** SHA-256 of the current thread's client IP, or null. */
    public static String currentClientIpHash() {
        return clientIpHash(currentRequest());
    }

    static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
