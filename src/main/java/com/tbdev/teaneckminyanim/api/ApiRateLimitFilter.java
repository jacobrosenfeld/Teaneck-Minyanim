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
 * Each unique client IP gets its own bucket. The default limit is 60 requests
 * per minute (one per second on average), configurable via
 * api.ratelimit.requests-per-minute in application.properties.
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

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String ip = resolveClientIp(request);
        Bucket bucket = buckets.computeIfAbsent(ip, k -> newBucket());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            log.debug("Rate limit exceeded for IP: {}", ip);
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", "60");
            response.getWriter().write(
                    "{\"data\":null,\"meta\":null,\"error\":{\"code\":\"RATE_LIMITED\"," +
                    "\"message\":\"Too many requests. Maximum " + requestsPerMinute +
                    " requests per minute. Please try again shortly.\"}}");
        }
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(
                        requestsPerMinute,
                        Refill.intervally(requestsPerMinute, Duration.ofMinutes(1))))
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
