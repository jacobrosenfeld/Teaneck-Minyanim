package com.tbdev.teaneckminyanim.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiRateLimitFilterTest {
    private ApiRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiRateLimitFilter();
        ReflectionTestUtils.setField(filter, "requestsPerMinute", 4);
        ReflectionTestUtils.setField(filter, "feedbackRequestsPerMinute", 1);
        ReflectionTestUtils.setField(filter, "authRequestsPerMinute", 2);
        ReflectionTestUtils.setField(filter, "writeRequestsPerMinute", 3);
    }

    @Test
    void categorizesRequests() {
        assertEquals(ApiRateLimitFilter.Category.AUTH, ApiRateLimitFilter.categorize(req("POST", "/api/v1/auth/refresh")));
        assertEquals(ApiRateLimitFilter.Category.AUTH, ApiRateLimitFilter.categorize(req("GET", "/api/v1/auth/passkey/options")));
        assertEquals(ApiRateLimitFilter.Category.FEEDBACK, ApiRateLimitFilter.categorize(req("POST", "/api/v1/feedback")));
        assertEquals(ApiRateLimitFilter.Category.FEEDBACK, ApiRateLimitFilter.categorize(req("GET", "/api/v1/feedback/metadata")));
        assertEquals(ApiRateLimitFilter.Category.WRITE, ApiRateLimitFilter.categorize(req("PATCH", "/api/v1/organizations/x/minyanim/y")));
        assertEquals(ApiRateLimitFilter.Category.WRITE, ApiRateLimitFilter.categorize(req("DELETE", "/api/v1/notifications/1")));
        assertEquals(ApiRateLimitFilter.Category.WRITE, ApiRateLimitFilter.categorize(req("POST", "/api/v1/notifications")));
        assertEquals(ApiRateLimitFilter.Category.READ, ApiRateLimitFilter.categorize(req("GET", "/api/v1/schedule")));
        assertEquals(ApiRateLimitFilter.Category.READ, ApiRateLimitFilter.categorize(req("HEAD", "/api/v1/schedule")));
        assertEquals(ApiRateLimitFilter.Category.READ, ApiRateLimitFilter.categorize(req("GET", "/api/v1/authors")));
    }

    @Test
    void authBucketIsLimitedSeparately() throws Exception {
        assertEquals(200, run("POST", "/api/v1/auth/refresh", "10.0.0.1"));
        assertEquals(200, run("POST", "/api/v1/auth/logout", "10.0.0.1"));
        MockHttpServletResponse limited = response("POST", "/api/v1/auth/refresh", "10.0.0.1");
        assertEquals(429, limited.getStatus());
        assertEquals("60", limited.getHeader("Retry-After"));
        assertTrue(limited.getContentAsString().contains("\"code\":\"RATE_LIMITED\""));
        // Other categories and other IPs are unaffected.
        assertEquals(200, run("GET", "/api/v1/schedule", "10.0.0.1"));
        assertEquals(200, run("POST", "/api/v1/auth/refresh", "10.0.0.2"));
    }

    @Test
    void writeBucketIsSeparateFromReads() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertEquals(200, run("PATCH", "/api/v1/organizations/x/minyanim/y", "10.0.1.1"));
        }
        assertEquals(429, run("DELETE", "/api/v1/notifications/1", "10.0.1.1"));
        for (int i = 0; i < 4; i++) {
            assertEquals(200, run("GET", "/api/v1/schedule", "10.0.1.1"));
        }
        assertEquals(429, run("GET", "/api/v1/schedule", "10.0.1.1"));
    }

    @Test
    void feedbackKeepsItsOwnBucket() throws Exception {
        assertEquals(200, run("POST", "/api/v1/feedback", "10.0.2.1"));
        assertEquals(429, run("POST", "/api/v1/feedback", "10.0.2.1"));
        assertEquals(200, run("POST", "/api/v1/notifications", "10.0.2.1"));
    }

    @Test
    void preflightAndNonApiRequestsAreNotLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertEquals(200, run("OPTIONS", "/api/v1/auth/refresh", "10.0.3.1"));
            assertEquals(200, run("POST", "/admin/login", "10.0.3.1"));
        }
    }

    @Test
    void forwardedForIdentifiesTheClient() throws Exception {
        MockHttpServletRequest first = req("POST", "/api/v1/auth/refresh");
        first.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");
        MockHttpServletRequest second = req("POST", "/api/v1/auth/refresh");
        second.addHeader("X-Forwarded-For", "203.0.113.9");
        MockHttpServletRequest third = req("POST", "/api/v1/auth/refresh");
        third.addHeader("X-Forwarded-For", "203.0.113.9");
        filter.doFilter(first, new MockHttpServletResponse(), new MockFilterChain());
        filter.doFilter(second, new MockHttpServletResponse(), new MockFilterChain());
        MockHttpServletResponse limited = new MockHttpServletResponse();
        filter.doFilter(third, limited, new MockFilterChain());
        assertEquals(429, limited.getStatus());
    }

    private int run(String method, String uri, String ip) throws Exception {
        return response(method, uri, ip).getStatus();
    }

    private MockHttpServletResponse response(String method, String uri, String ip) throws Exception {
        MockHttpServletRequest request = req(method, uri);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static MockHttpServletRequest req(String method, String uri) {
        return new MockHttpServletRequest(method, uri);
    }
}
