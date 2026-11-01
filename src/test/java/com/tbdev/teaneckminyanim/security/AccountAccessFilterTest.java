package com.tbdev.teaneckminyanim.security;

import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.FilterChain;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountAccessFilterTest {
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void disabledUserLosesExistingSession() throws Exception { checkBlocked(false, 1); }
    @Test void staleRoleLosesExistingSession() throws Exception { checkBlocked(true, 2); }

    private void checkBlocked(boolean enabled, int roleId) throws Exception {
        TNMUserService users = mock(TNMUserService.class);
        when(users.findByName("manager")).thenReturn(TNMUser.builder().id("one").username("manager")
                .roleId(roleId).enabled(enabled).organizationId("org").build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "manager", "unused", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin");
        var session = (org.springframework.mock.web.MockHttpSession) request.getSession();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        new AccountAccessFilter(users).doFilter(request, response, chain);
        assertTrue(session.isInvalid());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("/admin/login", response.getRedirectedUrl());
        verifyNoInteractions(chain);
    }
}
