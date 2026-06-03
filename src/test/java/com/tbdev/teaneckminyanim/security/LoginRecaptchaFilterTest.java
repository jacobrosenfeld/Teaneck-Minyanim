package com.tbdev.teaneckminyanim.security;

import com.tbdev.teaneckminyanim.service.recaptcha.RecaptchaService;
import com.tbdev.teaneckminyanim.service.recaptcha.RecaptchaVerificationException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class LoginRecaptchaFilterTest {

    @Test
    void validLoginRecaptchaContinuesToAuthenticationFilter() throws Exception {
        RecaptchaService recaptchaService = mock(RecaptchaService.class);
        LoginRecaptchaFilter filter = new LoginRecaptchaFilter(recaptchaService);
        MockHttpServletRequest request = loginRequest("token-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        verify(recaptchaService).verify("token-123", request);
        assertEquals(200, response.getStatus());
        assertNull(response.getRedirectedUrl());
    }

    @Test
    void invalidLoginRecaptchaRedirectsToLoginError() throws Exception {
        RecaptchaService recaptchaService = mock(RecaptchaService.class);
        LoginRecaptchaFilter filter = new LoginRecaptchaFilter(recaptchaService);
        MockHttpServletRequest request = loginRequest("bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        org.mockito.Mockito.doThrow(new RecaptchaVerificationException("bad token"))
                .when(recaptchaService).verify("bad-token", request);

        filter.doFilter(request, response, chain);

        assertEquals("/admin/login?error=recaptcha", response.getRedirectedUrl());
    }

    @Test
    void nonLoginPostDoesNotRunRecaptchaCheck() throws Exception {
        RecaptchaService recaptchaService = mock(RecaptchaService.class);
        LoginRecaptchaFilter filter = new LoginRecaptchaFilter(recaptchaService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/update-settings");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        verifyNoInteractions(recaptchaService);
        assertNull(response.getRedirectedUrl());
    }

    private MockHttpServletRequest loginRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", LoginRecaptchaFilter.LOGIN_PROCESSING_PATH);
        request.setServletPath(LoginRecaptchaFilter.LOGIN_PROCESSING_PATH);
        request.addParameter("recaptchaToken", token);
        return request;
    }
}
