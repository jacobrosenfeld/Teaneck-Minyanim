package com.tbdev.teaneckminyanim.api;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Token-bucket rate limiter for /api/v1/** endpoints.
 *
 * Each unique client IP gets its own bucket per category:
 * <ul>
 *   <li>{@code /api/v1/auth/**} (sign-in, refresh, logout): api.ratelimit.auth-requests-per-minute (10)</li>
 *   <li>Feedback submissions: api.ratelimit.feedback-requests-per-minute (5), because each accepted
 *       request can create external GitHub/email work</li>
 *   <li>Other non-GET requests (admin writes): api.ratelimit.write-requests-per-minute (30)</li>
 *   <li>Reads (GET/HEAD): api.ratelimit.requests-per-minute (60)</li>
 * </ul>
 *
 * The filter runs inside the API security chain, after CORS and before bearer-token authentication, so
 * invalid tokens are rate limited too and 429 responses still carry CORS headers. Its automatic servlet
 * registration is disabled in {@code WebSecurityConfiguration}.
 *
 * Responses on limit:
 *   HTTP 429 with Retry-After: 60 and a standard ApiResponse error body.
 *
 * X-Forwarded-For is respected for clients behind a reverse proxy.
 * Old buckets are not pruned — acceptable for a bounded number of unique
 * clients; add Caffeine/Guava cache if scale requires it.
 */
@Component
@Order(1)
@Slf4j
public class ApiRateLimitFilter extends OncePerRequestFilter {

    @Value("${api.ratelimit.requests-per-minute:60}")
    private int requestsPerMinute;

    @Value("${api.ratelimit.feedback-requests-per-minute:5}")
    private int feedbackRequestsPerMinute;

    @Value("${api.ratelimit.auth-requests-per-minute:10}")
    private int authRequestsPerMinute;

    @Value("${api.ratelimit.write-requests-per-minute:30}")
    private int writeRequestsPerMinute;

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Bucket> feedbackBuckets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Bucket> authBuckets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Bucket> writeBuckets = new ConcurrentHashMap<>();

    /** Rate-limit categories, each with its own per-IP buckets. */
    enum Category { READ, WRITE, AUTH, FEEDBACK }

    static Category categorize(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.equals("/api/v1/auth") || uri.startsWith("/api/v1/auth/")) {
            return Category.AUTH;
        }
        if (uri.startsWith("/api/v1/feedback")) {
            return Category.FEEDBACK;
        }
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
            return Category.READ;
        }
        return Category.WRITE;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/")
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String ip = resolveClientIp(request);
        Category category = categorize(request);
        int limit = switch (category) {
            case AUTH -> authRequestsPerMinute;
            case FEEDBACK -> feedbackRequestsPerMinute;
            case WRITE -> writeRequestsPerMinute;
            case READ -> requestsPerMinute;
        };
        ConcurrentMap<String, Bucket> bucketMap = switch (category) {
            case AUTH -> authBuckets;
            case FEEDBACK -> feedbackBuckets;
            case WRITE -> writeBuckets;
            case READ -> buckets;
        };
        Bucket bucket = bucketMap.computeIfAbsent(ip, k -> newBucket(limit));

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            log.debug("Rate limit exceeded for IP: {}", ip);
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", "60");
            response.getWriter().write(
                    "{\"data\":null,\"meta\":null,\"error\":{\"code\":\"RATE_LIMITED\"," +
                    "\"message\":\"Too many requests. Maximum " + limit +
                    " requests per minute. Please try again shortly.\"}}");
        }
    }

    private Bucket newBucket(int limit) {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(
                        limit,
                        Refill.intervally(limit, Duration.ofMinutes(1))))
                .build();
    }

    /**
     * Prefer X-Forwarded-For (first hop) so the rate limit applies per
     * real client behind a reverse proxy / load balancer.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
